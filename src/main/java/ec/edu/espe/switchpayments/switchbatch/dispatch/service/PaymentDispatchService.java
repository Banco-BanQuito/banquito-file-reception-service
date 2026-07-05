package ec.edu.espe.switchpayments.switchbatch.dispatch.service;

import com.banquito.payswitch.notification.NotificationRequest;
import com.mongodb.client.result.UpdateResult;
import ec.edu.espe.banquito.banquitotariffservice.grpc.TariffCalculationGrpcRequest;
import ec.edu.espe.banquito.banquitotariffservice.grpc.TariffCalculationGrpcResponse;
import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dispatch.client.NotificationGrpcClient;
import ec.edu.espe.switchpayments.switchbatch.dispatch.client.TariffGrpcClient;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.OffUsClearingMessage;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentBatch;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentDetail;
import ec.edu.espe.switchpayments.switchbatch.dispatch.repository.PaymentDispatchDetailRepository;
import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import ec.edu.espe.switchpayments.switchbatch.service.ICoreBankingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PaymentDispatchService.class);
    private static final ZoneId SERVICE_ZONE = ZoneId.of("America/Guayaquil");

    private static final String FIELD_BATCH_ID = "batchId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED = "FAILED";

    private final PaymentDispatchDetailRepository detailRepository;
    private final MongoTemplate mongoTemplate;
    private final ICoreBankingClient coreBankingClient;
    private final TariffGrpcClient tariffClient;
    private final NotificationGrpcClient notificationClient;
    private final RabbitTemplate rabbitTemplate;
    private final FileReceptionProperties properties;

    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> debitOutcomes = new ConcurrentHashMap<>();

    public PaymentDispatchService(PaymentDispatchDetailRepository detailRepository,
                                   MongoTemplate mongoTemplate,
                                   ICoreBankingClient coreBankingClient,
                                   TariffGrpcClient tariffClient,
                                   NotificationGrpcClient notificationClient,
                                   RabbitTemplate rabbitTemplate,
                                   FileReceptionProperties properties) {
        this.detailRepository = detailRepository;
        this.mongoTemplate = mongoTemplate;
        this.coreBankingClient = coreBankingClient;
        this.tariffClient = tariffClient;
        this.notificationClient = notificationClient;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @RabbitListener(queues = "${app.file-reception.rabbit-queue-onus}", concurrency = "5-20")
    public void processOnUsLine(BatchLineMessage message) {
        log.info("Received ON_US payment line: batchId={}, lineNumber={}", message.batchId(), message.lineNumber());
        PaymentDetail detail = prepareLine(message);
        if (detail == null || !ensureBatchDebited(message, detail)) {
            return;
        }

        boolean success = false;
        String errorCode = null;
        String errorMessage = null;
        try {
            processOnUs(message, detail);
            success = true;
        } catch (Exception e) {
            log.error("On-Us error batchId={} line={}: {}", message.batchId(), message.lineNumber(), e.getMessage());
            errorCode = "ONUS_PROCESSING_ERROR";
            errorMessage = e.getMessage();
        }

        finalizeLine(message, detail, success, errorCode, errorMessage, "PROCESSED");
    }

    @RabbitListener(queues = "${app.file-reception.rabbit-queue-offus}", concurrency = "5-20")
    public void processOffUsLine(BatchLineMessage message) {
        log.info("Received OFF_US payment line: batchId={}, lineNumber={}", message.batchId(), message.lineNumber());
        PaymentDetail detail = prepareLine(message);
        if (detail == null || !ensureBatchDebited(message, detail)) {
            return;
        }

        boolean success = false;
        String errorCode = null;
        String errorMessage = null;
        try {
            OffUsClearingMessage clearingMessage = adaptForClearingHouse(message, detail);
            rabbitTemplate.convertAndSend(properties.getClearingExchange(), properties.getClearingRoutingKey(), clearingMessage);
            success = true;
        } catch (Exception e) {
            log.error("Off-Us routing error batchId={} line={}: {}", message.batchId(), message.lineNumber(), e.getMessage());
            errorCode = "OFFUS_ROUTING_ERROR";
            errorMessage = e.getMessage();
        }

        finalizeLine(message, detail, success, errorCode, errorMessage, "CLEARED");
    }

    @RabbitListener(queues = "${app.file-reception.rabbit-queue-invalid}", concurrency = "5-20")
    public void processInvalidLine(BatchLineMessage message) {
        log.warn("Invalid routing code '{}' for batchId={}", message.routingCode(), message.batchId());
        PaymentDetail detail = prepareLine(message);
        if (detail == null || !ensureBatchDebited(message, detail)) {
            return;
        }

        finalizeLine(message, detail, false, "ROUTING_CODE_INVALID",
                "Invalid routing code: " + message.routingCode(), "PROCESSED");
    }

    private PaymentDetail prepareLine(BatchLineMessage message) {
        PaymentDetail detail = buildInitialDetail(message);
        try {
            detailRepository.save(detail);
        } catch (DuplicateKeyException e) {
            log.warn("Duplicate message ignored: batchId={}, lineNumber={}", message.batchId(), message.lineNumber());
            return null;
        }
        ensureBatchExists(message);
        return detail;
    }

    private boolean ensureBatchDebited(BatchLineMessage message, PaymentDetail detail) {
        if (initialDebitIfNeeded(message)) {
            return true;
        }
        String reason = getBatchFailureReason(message.batchId());
        detail.setStatus("REJECTED");
        detail.setErrorCode("BATCH_DEBIT_FAILED");
        detail.setErrorMessage("No se acreditó: " + reason);
        detail.setProcessedAt(LocalDateTime.now(SERVICE_ZONE));
        detailRepository.save(detail);
        updateBatchCounters(message, false);
        return false;
    }

    private void finalizeLine(BatchLineMessage message, PaymentDetail detail, boolean success,
                               String errorCode, String errorMessage, String successStatus) {
        detail.setStatus(success ? successStatus : "REJECTED");
        detail.setErrorCode(errorCode);
        detail.setErrorMessage(errorMessage);
        detail.setProcessedAt(LocalDateTime.now(SERVICE_ZONE));
        detailRepository.save(detail);
        updateBatchCounters(message, success);
    }

    private void processOnUs(BatchLineMessage message, PaymentDetail detail) {
        coreBankingClient.batchCredit(
                message.batchId(),
                message.accountDestination(),
                message.amount(),
                message.reference(),
                detail.getTransactionUuid()
        );

        sendNotificationAsync(message, detail.getId());
    }

    private void sendNotificationAsync(BatchLineMessage message, String detailId) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, String> vars = new HashMap<>();
                vars.put("beneficiaryName", message.beneficiaryName());
                vars.put("amount", String.format("%.2f", message.amount()));
                vars.put("reference", message.reference());
                vars.put(FIELD_BATCH_ID, message.batchId());

                NotificationRequest req = NotificationRequest.newBuilder()
                        .setPaymentDetailId(detailId != null ? detailId.hashCode() : 0)
                        .setEmailTo(message.beneficiaryEmail())
                        .setSubject("Transferencia procesada exitosamente")
                        .setBodyTemplate("payment_credit_notification")
                        .putAllVariables(vars)
                        .build();

                notificationClient.sendNotification(req);
            } catch (Exception e) {
                log.warn("Notification failed batchId={} line={}: {}", message.batchId(), message.lineNumber(), e.getMessage());
            }
        });
    }

    private void ensureBatchExists(BatchLineMessage message) {
        Query query = new Query(Criteria.where(FIELD_BATCH_ID).is(message.batchId()));
        Update update = new Update()
                .setOnInsert(FIELD_BATCH_ID, message.batchId())
                .setOnInsert(FIELD_STATUS, STATUS_PROCESSING)
                .setOnInsert("originatingAccount", message.originatingAccount())
                .setOnInsert("declaredTotalRecords", message.declaredTotalRecords())
                .setOnInsert("declaredTotalAmount", message.declaredTotalAmount())
                .setOnInsert("successfulRecords", 0)
                .setOnInsert("rejectedRecords", 0)
                .setOnInsert("successfulAmount", BigDecimal.ZERO)
                .setOnInsert("rejectedAmount", BigDecimal.ZERO)
                .setOnInsert("refundAmount", BigDecimal.ZERO)
                .setOnInsert("createdAt", LocalDateTime.now(SERVICE_ZONE));
        mongoTemplate.upsert(query, update, PaymentBatch.class);
    }

    private boolean initialDebitIfNeeded(BatchLineMessage message) {
        String batchId = message.batchId();
        CompletableFuture<Boolean> outcome = debitOutcomes.computeIfAbsent(batchId, id -> new CompletableFuture<>());

        Query claimQuery = new Query(Criteria.where(FIELD_BATCH_ID).is(batchId).and(FIELD_STATUS).is(STATUS_PROCESSING));
        Update claimUpdate = new Update().set(FIELD_STATUS, "DEBITED");
        UpdateResult claimed = mongoTemplate.updateFirst(claimQuery, claimUpdate, PaymentBatch.class);

        if (claimed.getModifiedCount() != 1) {
            try {
                return outcome.get(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[RF-03][DEBIT] Interrupted waiting for debit result batchId={}", batchId);
                return false;
            } catch (Exception e) {
                log.warn("[RF-03][DEBIT] No se pudo confirmar a tiempo el resultado del débito para batchId={}: {}",
                        batchId, e.getMessage());
                PaymentBatch current = mongoTemplate.findOne(
                        new Query(Criteria.where(FIELD_BATCH_ID).is(batchId)), PaymentBatch.class);
                return current != null && !STATUS_FAILED.equals(current.getStatus());
            }
        }

        BigDecimal totalAmount = message.declaredTotalAmount();
        String debitAccount = (message.originatingAccount() != null && !message.originatingAccount().isBlank())
                ? message.originatingAccount()
                : properties.getCorporateAccountNumber();

        log.info("[RF-03][DEBIT] Debitando monto total declarado={} de cuenta={} para batchId={}",
                totalAmount, debitAccount, batchId);

        boolean success;
        try {
            coreBankingClient.corporateDebit(batchId, debitAccount, totalAmount, BigDecimal.ZERO);
            log.info("[RF-03][DEBIT] Débito inicial exitoso para batchId={}", batchId);
            success = true;
        } catch (Exception e) {
            String reason = resolveDebitFailureReason(e);
            log.error("[RF-03][DEBIT] Débito inicial fallido para batchId={}: {}", batchId, reason);
            Query failQuery = new Query(Criteria.where(FIELD_BATCH_ID).is(batchId));
            Update failUpdate = new Update()
                    .set(FIELD_STATUS, STATUS_FAILED)
                    .set("failureReason", reason)
                    .set(FIELD_UPDATED_AT, LocalDateTime.now(SERVICE_ZONE));
            mongoTemplate.updateFirst(failQuery, failUpdate, PaymentBatch.class);
            success = false;
        }

        outcome.complete(success);
        return success;
    }

    private String resolveDebitFailureReason(Exception e) {
        if (e instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            String body = httpEx.getResponseBodyAsString();
            if (body != null && body.contains("Insufficient balance")) {
                return "Fondos insuficientes en la cuenta de origen.";
            }
            if (body != null && !body.isBlank()) {
                return body;
            }
        }
        return e.getMessage();
    }

    private String getBatchFailureReason(String batchId) {
        PaymentBatch batch = mongoTemplate.findOne(
                new Query(Criteria.where(FIELD_BATCH_ID).is(batchId)), PaymentBatch.class);
        if (batch != null && batch.getFailureReason() != null && !batch.getFailureReason().isBlank()) {
            return batch.getFailureReason();
        }
        return "fondos insuficientes u otro error en la cuenta de origen";
    }

    private void updateBatchCounters(BatchLineMessage message, boolean success) {
        Query query = new Query(Criteria.where(FIELD_BATCH_ID).is(message.batchId()));
        Update update = new Update().set(FIELD_UPDATED_AT, LocalDateTime.now(SERVICE_ZONE));

        if (success) {
            update.inc("successfulRecords", 1).inc("successfulAmount", message.amount());
        } else {
            update.inc("rejectedRecords", 1).inc("rejectedAmount", message.amount());
        }

        FindAndModifyOptions opts = FindAndModifyOptions.options().returnNew(true);
        PaymentBatch updated = mongoTemplate.findAndModify(query, update, opts, PaymentBatch.class);

        if (updated != null && updated.getDeclaredTotalRecords() > 0) {
            int processed = updated.getSuccessfulRecords() + updated.getRejectedRecords();
            if (processed >= updated.getDeclaredTotalRecords() && tryClaimCompletion(updated.getBatchId())) {
                completeBatch(updated);
            }
        }
    }

    private String resolveOutcomeStatus(PaymentBatch batch) {
        if (batch.getSuccessfulRecords() <= 0) {
            return STATUS_FAILED;
        }
        if (batch.getRejectedRecords() > 0) {
            return "COMPLETED_WITH_ISSUES";
        }
        return "COMPLETED";
    }

    private void completeBatch(PaymentBatch batch) {
        log.info("Completing batch: {}", batch.getBatchId());
        String outcomeStatus = resolveOutcomeStatus(batch);

        if (properties.isDispatchLocalCompletionEnabled()) {
            Query q = new Query(Criteria.where(FIELD_BATCH_ID).is(batch.getBatchId()));
            Update u = new Update()
                    .set(FIELD_STATUS, outcomeStatus)
                    .set(FIELD_UPDATED_AT, LocalDateTime.now(SERVICE_ZONE))
                    .set("completedAt", LocalDateTime.now(SERVICE_ZONE));
            mongoTemplate.updateFirst(q, u, PaymentBatch.class);
            log.info("Batch {} in local mode: {}", outcomeStatus, batch.getBatchId());
            return;
        }

        BigDecimal refund = batch.getRejectedAmount();
        String debitAccount = (batch.getOriginatingAccount() != null && !batch.getOriginatingAccount().isBlank())
                ? batch.getOriginatingAccount()
                : properties.getCorporateAccountNumber();

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            try {
                log.info("[RF-03][REFUND] Devolviendo monto rechazado={} a cuenta={} para batchId={}",
                        refund, debitAccount, batch.getBatchId());
                coreBankingClient.corporateRefund(batch.getBatchId(), debitAccount, refund);
            } catch (Exception e) {
                log.error("[RF-03][REFUND] Devolución fallida para batchId={} (lote permanece {}): {}",
                        batch.getBatchId(), outcomeStatus, e.getMessage());
                refund = BigDecimal.ZERO;
            }
        }

        try {
            TariffCalculationGrpcRequest tariffReq = TariffCalculationGrpcRequest.newBuilder()
                    .setSuccessfulTx(batch.getSuccessfulRecords())
                    .setBatchId(batch.getBatchId())
                    .build();
            TariffCalculationGrpcResponse tariffResp = tariffClient.calculateTariff(tariffReq);

            BigDecimal commission = new BigDecimal(tariffResp.getTotalCharge());
            if (commission.compareTo(BigDecimal.ZERO) > 0) {
                log.info("[RF-04][COMMISSION] Debitando comisión={} de cuenta={} para batchId={}",
                        commission, debitAccount, batch.getBatchId());
                coreBankingClient.corporateDebit(batch.getBatchId(), debitAccount, BigDecimal.ZERO, commission);
            }
        } catch (Exception e) {
            log.error("[RF-04] Cobro de comisión fallido para batchId={} (lote permanece {}): {}",
                    batch.getBatchId(), outcomeStatus, e.getMessage());
        }

        Query q = new Query(Criteria.where(FIELD_BATCH_ID).is(batch.getBatchId()));
        Update u = new Update()
                .set(FIELD_STATUS, outcomeStatus)
                .set("refundAmount", refund)
                .set("completedAt", LocalDateTime.now(SERVICE_ZONE));
        mongoTemplate.updateFirst(q, u, PaymentBatch.class);

        log.info("[RF-03] Batch {}: batchId={}, exitosas={}, rechazadas={}, devuelto={}",
                outcomeStatus, batch.getBatchId(), batch.getSuccessfulRecords(), batch.getRejectedRecords(), refund);
    }

    private boolean tryClaimCompletion(String batchId) {
        Query query = new Query(Criteria.where(FIELD_BATCH_ID).is(batchId).and(FIELD_STATUS).is("DEBITED"));
        Update update = new Update().set(FIELD_STATUS, "COMPLETING");
        UpdateResult result = mongoTemplate.updateFirst(query, update, PaymentBatch.class);
        return result.getModifiedCount() == 1;
    }

    private OffUsClearingMessage adaptForClearingHouse(BatchLineMessage message, PaymentDetail detail) {
        OffUsClearingMessage clearingMessage = new OffUsClearingMessage();
        clearingMessage.setBatchId(UUID.fromString(message.batchId()));
        clearingMessage.setTransactionId(resolveTransactionId(detail.getTransactionUuid()));
        clearingMessage.setRoutingCode(message.routingCode());
        clearingMessage.setOriginAccount(message.originatingAccount());
        clearingMessage.setDestinationAccount(message.accountDestination());
        clearingMessage.setAmount(message.amount());
        clearingMessage.setCurrency("USD");
        clearingMessage.setConcept(message.reference());
        clearingMessage.setValueDate(LocalDate.now(SERVICE_ZONE));
        return clearingMessage;
    }

    private UUID resolveTransactionId(String transactionUuid) {
        if (transactionUuid == null || transactionUuid.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(transactionUuid);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }

    private PaymentDetail buildInitialDetail(BatchLineMessage msg) {
        PaymentDetail d = new PaymentDetail();
        d.setBatchId(msg.batchId());
        d.setLineNumber(msg.lineNumber());
        d.setTransactionUuid(UUID.randomUUID().toString());
        d.setRoutingCode(msg.routingCode());
        d.setAccountDestination(msg.accountDestination());
        d.setAmount(msg.amount());
        d.setReference(msg.reference());
        d.setBeneficiaryName(msg.beneficiaryName());
        d.setBeneficiaryEmail(msg.beneficiaryEmail());
        d.setStatus(STATUS_PROCESSING);
        return d;
    }

}
