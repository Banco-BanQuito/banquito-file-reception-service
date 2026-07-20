package ec.edu.espe.switchpayments.switchbatch.service.impl;

import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import ec.edu.espe.switchpayments.switchbatch.dto.ParsedBatch;
import ec.edu.espe.switchpayments.switchbatch.dto.ParsedPaymentLine;
import ec.edu.espe.switchpayments.switchbatch.event.PaymentLinesReadyEvent;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentFileValidation;
import ec.edu.espe.switchpayments.switchbatch.repository.BatchStatusLogRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentFileValidationRepository;
import ec.edu.espe.switchpayments.switchbatch.service.ICoreBankingClient;
import ec.edu.espe.switchpayments.switchbatch.service.IPaymentLinePublisher;
import ec.edu.espe.switchpayments.switchbatch.service.IBankCodeCatalogService;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PaymentLinesReadyListener {
    private static final Logger logger = LoggerFactory.getLogger(PaymentLinesReadyListener.class);
    private final IPaymentLinePublisher paymentLinePublisher;
    private final TaskScheduler taskScheduler;
    private final ICoreBankingClient coreBankingClient;
    private final IBankCodeCatalogService bankCodeCatalogService;
    private final PaymentFileValidationRepository validationRepository;
    private final PaymentBatchRepository paymentBatchRepository;
    private final BatchStatusLogRepository statusLogRepository;
    public PaymentLinesReadyListener(IPaymentLinePublisher paymentLinePublisher,
                                     TaskScheduler taskScheduler,
                                     ICoreBankingClient coreBankingClient,
                                     IBankCodeCatalogService bankCodeCatalogService,
                                     PaymentFileValidationRepository validationRepository,
                                     PaymentBatchRepository paymentBatchRepository,
                                     BatchStatusLogRepository statusLogRepository) {
        this.paymentLinePublisher = paymentLinePublisher;
        this.taskScheduler = taskScheduler;
        this.coreBankingClient = coreBankingClient;
        this.bankCodeCatalogService = bankCodeCatalogService;
        this.validationRepository = validationRepository;
        this.paymentBatchRepository = paymentBatchRepository;
        this.statusLogRepository = statusLogRepository;
    }
    @Async
    @EventListener
    public void onPaymentLinesReady(PaymentLinesReadyEvent event) {
        ParsedBatch batch = event.batch();
        String batchId = event.batchId();
        logger.info("[ASYNC] Iniciando validación y fragmentación del lote {} ({} líneas declaradas).",
                batchId, batch.declaredRecords());

        boolean customerServiceActive = coreBankingClient.hasActiveMassPaymentService(
                batch.clientRuc(), batch.serviceType());
        if (!customerServiceActive) {
            logger.warn("[ASYNC] Lote {} rechazado: servicio de pagos masivos inactivo para RUC {}.",
                    batchId, batch.clientRuc());
            updateBatchStatus(batchId, "REJECTED");
            saveValidation(batchId, batch, event.duplicateValid(), false);
            return;
        }
        logger.info("[ASYNC] Procesando líneas utilizando hilos concurrentes para optimizar la carga.");
        List<ParsedPaymentLine> acceptedLines = batch.lines();
        boolean sourceAccountValid = coreBankingClient.isFavoriteAccount(
                batch.sourceAccountNumber(), batch.clientRuc());
        boolean fullyValid = sourceAccountValid && acceptedLines.size() == batch.lines().size();
        saveValidation(batchId, batch, event.duplicateValid(), fullyValid);
        if (!sourceAccountValid) {
            logger.warn("[ASYNC] Lote {} sin cuenta origen favorita válida. Ninguna línea publicada.", batchId);
            return;
        }
        List<BatchLineMessage> messages = toMessages(batchId, batch, acceptedLines);
        logger.info("[ASYNC] Publicando un total de {} líneas en Pub/Sub para lote {}.", messages.size(), batchId);

        if (event.scheduledProcessAt().isAfter(Instant.now())) {
            logger.info("[ASYNC] Lote {} programado para {}. Publicación diferida.", batchId, event.scheduledProcessAt());
            taskScheduler.schedule(
                    () -> paymentLinePublisher.publish(batchId, event.scheduledProcessAt(), messages),
                    event.scheduledProcessAt());
            return;
        }
        paymentLinePublisher.publish(batchId, event.scheduledProcessAt(), messages);
    }
    private List<BatchLineMessage> toMessages(String batchId, ParsedBatch batch, List<ParsedPaymentLine> lines) {
        return lines.stream()
                .map(line -> new BatchLineMessage(
                        batchId,
                        line.lineNumber(),
                        line.routingCode(),
                        bankCodeCatalogService.classify(line.routingCode()),
                        line.destinationAccountNumber(),
                        batch.sourceAccountNumber(),
                        batch.declaredRecords(),
                        batch.headerTotalAmount(),
                        line.amount(),
                        line.reference(),
                        line.beneficiaryName(),
                        line.beneficiaryEmail()))
                .toList();
    }
    private void saveValidation(String batchId, ParsedBatch batch, boolean duplicateValid, boolean customerServiceValid) {
        PaymentFileValidation validation = new PaymentFileValidation();
        validation.setPaymentBatchId(batchId);
        validation.setHeaderTotalRecords(batch.headerTotalRecords());
        validation.setHeaderTotalAmount(batch.headerTotalAmount());
        validation.setFooterTotalRecords(batch.footerTotalRecords());
        validation.setFooterTotalAmount(batch.footerTotalAmount());
        validation.setSecurityHash(batch.securityHash());
        validation.setStructureValid(true);
        validation.setAmountControlValid(true);
        validation.setCustomerServiceValid(customerServiceValid);
        validation.setDuplicateFileValid(duplicateValid);
        validation.setValidationResult(customerServiceValid ? "SUCCESS" : "PARTIAL_SUCCESS");
        validation.setValidatedAt(Instant.now());
        validationRepository.save(validation);
    }
    private void updateBatchStatus(String batchId, String newStatus) {
        paymentBatchRepository.findById(batchId).ifPresent(doc -> {
            String previousStatus = doc.getStatus();
            doc.setStatus(newStatus);
            paymentBatchRepository.save(doc);
            saveBatchStatusLog(batchId, previousStatus, newStatus);
        });
    }

    private void saveBatchStatusLog(String batchId, String previousStatus, String newStatus) {
        ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog log = new ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog();
        log.setPaymentBatchId(batchId);
        log.setPreviousStatus(previousStatus);
        log.setNewStatus(newStatus);
        log.setChangedAt(Instant.now());
        statusLogRepository.save(log);
    }
}
