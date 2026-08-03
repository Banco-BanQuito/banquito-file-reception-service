package ec.edu.espe.switchpayments.switchbatch.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ec.edu.espe.switchpayments.switchbatch.dto.BatchStatusResponse;
import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dto.FileReceptionResponse;
import ec.edu.espe.switchpayments.switchbatch.dto.ParsedBatch;
import ec.edu.espe.switchpayments.switchbatch.exception.DuplicateBatchException;
import ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.BatchStatusLogRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchLineRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;
import ec.edu.espe.switchpayments.switchbatch.service.IBatchLineRegistrationService;
import ec.edu.espe.switchpayments.switchbatch.service.IBusinessDayService;
import ec.edu.espe.switchpayments.switchbatch.service.ICoreBankingClient;
import ec.edu.espe.switchpayments.switchbatch.service.ICsvBatchParser;
import ec.edu.espe.switchpayments.switchbatch.service.IFileReceptionService;

@Service
public class FileReceptionServiceImpl implements IFileReceptionService {
    private static final Logger logger = LoggerFactory.getLogger(FileReceptionServiceImpl.class);
    private static final java.util.List<String> DUPLICATE_SUCCESS_STATUSES = java.util.List.of(
            "RECEIVED", "ENQUEUED", "SCHEDULED", "COMPLETED", "SUCCESS");
    private final ICsvBatchParser csvBatchParser;
    private final FileReceptionProperties properties;
    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentBatchLineRepository paymentBatchLineRepository;
    private final BatchStatusLogRepository batchStatusLogRepository;
    private final MongoTemplate mongoTemplate;
    private final IBusinessDayService businessDayService;
    private final ICoreBankingClient coreBankingClient;
    private final IBatchLineRegistrationService batchLineRegistrationService;
    private final Clock clock;
    @Autowired
    public FileReceptionServiceImpl(ICsvBatchParser csvBatchParser,
                                    FileReceptionProperties properties,
                                    PaymentBatchRepository paymentBatchRepository,
                                    PaymentBatchLineRepository paymentBatchLineRepository,
                                    BatchStatusLogRepository batchStatusLogRepository,
                                    MongoTemplate mongoTemplate,
                                    IBusinessDayService businessDayService,
                                    ICoreBankingClient coreBankingClient,
                                    IBatchLineRegistrationService batchLineRegistrationService) {
        this(csvBatchParser, properties, paymentBatchRepository, paymentBatchLineRepository, batchStatusLogRepository,
                mongoTemplate,
                businessDayService, coreBankingClient, batchLineRegistrationService, Clock.systemDefaultZone());
    }
    public FileReceptionServiceImpl(ICsvBatchParser csvBatchParser,
                                    FileReceptionProperties properties,
                                    PaymentBatchRepository paymentBatchRepository,
                                    PaymentBatchLineRepository paymentBatchLineRepository,
                                    BatchStatusLogRepository batchStatusLogRepository,
                                    MongoTemplate mongoTemplate,
                                    IBusinessDayService businessDayService,
                                    ICoreBankingClient coreBankingClient,
                                    IBatchLineRegistrationService batchLineRegistrationService,
                                    Clock clock) {
        this.csvBatchParser = csvBatchParser;
        this.properties = properties;
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentBatchLineRepository = paymentBatchLineRepository;
        this.batchStatusLogRepository = batchStatusLogRepository;
        this.mongoTemplate = mongoTemplate;
        this.businessDayService = businessDayService;
        this.coreBankingClient = coreBankingClient;
        this.batchLineRegistrationService = batchLineRegistrationService;
        this.clock = clock;
    }
    @Override
    public FileReceptionResponse receive(MultipartFile file, String serviceType, String clientRuc) throws IOException {
        validateFile(file);
        ParsedBatch batch = csvBatchParser.parse(file.getInputStream(), serviceType, clientRuc);
        if (!coreBankingClient.hasSufficientBalance(batch.sourceAccountNumber(), batch.declaredAmount())) {
            throw new IllegalArgumentException(
                    "Saldo insuficiente en la cuenta de origen " + batch.sourceAccountNumber()
                            + " para cubrir el monto declarado (" + batch.declaredAmount() + ").");
        }
        String batchId = UUID.randomUUID().toString();
        Instant receivedAt = Instant.now(clock);
        IngestionSchedule schedule = resolveIngestionSchedule(receivedAt);
        boolean duplicateValid = !isDuplicate(file.getOriginalFilename(), batch.fileHash(), receivedAt);
        String initialStatus = duplicateValid ? "RECEIVING" : "DUPLICATE";
        PaymentBatchDocument batchDocument = saveBatch(file, batch, batchId, receivedAt,
                schedule.scheduledProcessAt(), initialStatus);
        saveStatusLog(batchDocument.getId(), null, batchDocument.getStatus());
        if (!duplicateValid) {
            throw new DuplicateBatchException("Lote duplicado");
        }
        batchLineRegistrationService.registerLinesAsync(batchId, batch, schedule.status());

        logger.info("Lote {} aceptado estructuralmente ({} lineas). Registro de lineas continua en segundo plano.",
                batchId, batch.declaredRecords());

        return new FileReceptionResponse(
                batchId,
                "EN_PROCESO",
                "Lote recibido exitosamente. " + batch.declaredRecords() + " linea(s) quedaron en registro asincronico.",
                receivedAt,
                batch.declaredRecords(),
                batch.declaredAmount());
    }

    @Override
    public BatchStatusResponse getStatus(String batchId) {
        Document dispatchBatch = mongoTemplate.findOne(
                Query.query(Criteria.where("batchId").is(batchId)),
                Document.class,
                "payment_dispatch_batch");
        if (dispatchBatch != null) {
            return dispatchStatus(dispatchBatch);
        }
        PaymentBatchDocument batch = paymentBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + batchId));
        return initialStatus(batch);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase().endsWith(".csv") || filename.toLowerCase().endsWith(".txt"))) {
            throw new IllegalArgumentException("Solo se aceptan archivos CSV o TXT");
        }
    }
    private boolean isDuplicate(String fileName, String hash, Instant receivedAt) {
        Instant threshold = receivedAt.minus(properties.getDuplicateWindowDays(), ChronoUnit.DAYS);
        return paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                fileName, hash, DUPLICATE_SUCCESS_STATUSES, threshold);
    }
    private PaymentBatchDocument saveBatch(MultipartFile file, ParsedBatch batch, String batchId,
                                           Instant receivedAt, Instant scheduledProcessAt, String status) {
        PaymentBatchDocument document = new PaymentBatchDocument();
        document.setId(batchId);
        document.setFileName(file.getOriginalFilename());
        document.setFileHash(batch.fileHash());
        document.setClientRuc(batch.clientRuc());
        document.setSourceAccountNumber(batch.sourceAccountNumber());
        document.setReceivedAt(receivedAt);
        document.setScheduledProcessAt(scheduledProcessAt);
        document.setDeclaredTotalRecords(batch.declaredRecords());
        document.setDeclaredTotalAmount(batch.declaredAmount());
        document.setStatus(status);
        document.setChannel("KONG_SWITCH");
        return paymentBatchRepository.save(document);
    }

    private void saveStatusLog(String batchId, String previousStatus, String newStatus) {
        BatchStatusLog log = new BatchStatusLog();
        log.setPaymentBatchId(batchId);
        log.setPreviousStatus(previousStatus);
        log.setNewStatus(newStatus);
        log.setChangedAt(Instant.now());
        batchStatusLogRepository.save(log);
    }
    private IngestionSchedule resolveIngestionSchedule(Instant receivedAt) {
        ZoneId zone = clock.getZone();
        LocalDateTime receivedDateTime = receivedAt.atZone(zone).toLocalDateTime();
        boolean businessDay = businessDayService.isBusinessDay(receivedDateTime.toLocalDate());
        LocalTime cutoffTime = LocalTime.of(properties.getCutoffHour(), 0);
        if (businessDay && receivedDateTime.toLocalTime().isBefore(cutoffTime)) {
            return new IngestionSchedule("EN_PROCESO", receivedAt);
        }
        LocalDate nextBusinessDay = businessDayService.nextBusinessDay(receivedDateTime.toLocalDate());
        Instant scheduledProcessAt = LocalDateTime.of(nextBusinessDay, LocalTime.of(0, 1))
                .atZone(zone)
                .toInstant();
        return new IngestionSchedule("PROGRAMADO", scheduledProcessAt);
    }

    private BatchStatusResponse initialStatus(PaymentBatchDocument batch) {
        int declared = safeInt(batch.getDeclaredTotalRecords());
        int registered = Math.toIntExact(Math.min(paymentBatchLineRepository.countByBatchId(batch.getId()), Integer.MAX_VALUE));
        String status = normalizeStatus(batch.getStatus());
        String message = "Registrando lineas del archivo: " + registered + "/" + declared;
        return new BatchStatusResponse(
                batch.getId(),
                status,
                declared,
                0,
                0,
                Math.max(declared, 0),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                batch.getReceivedAt(),
                null,
                null,
                message,
                null);
    }

    private BatchStatusResponse dispatchStatus(Document batch) {
        int declared = intValue(batch.get("declaredTotalRecords"));
        int successful = intValue(batch.get("successfulRecords"));
        int rejected = intValue(batch.get("rejectedRecords"));
        int inProcess = Math.max(declared - successful - rejected, 0);
        return new BatchStatusResponse(
                textValue(batch.get("batchId")),
                normalizeStatus(textValue(batch.get("status"))),
                declared,
                successful,
                rejected,
                inProcess,
                decimalValue(batch.get("successfulAmount")),
                decimalValue(batch.get("rejectedAmount")),
                instantValue(batch.get("createdAt")),
                instantValue(batch.get("updatedAt")),
                instantValue(batch.get("completedAt")),
                null,
                textValue(batch.get("failureReason")));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNKNOWN";
        }
        return switch (status.trim().toUpperCase()) {
            case "RECEIVING", "EN_PROCESO", "CLASSIFYING", "CLASSIFIED", "PUBLISHING", "PUBLISHED" -> "PROCESSING";
            case "PROGRAMADO", "SCHEDULED", "RECEIVED" -> "RECEIVED";
            case "COMPLETANDO" -> "COMPLETING";
            case "COMPLETADO", "FINALIZADO", "COMPLETED" -> "COMPLETED";
            case "FAILED", "FALLIDO", "ERROR", "INGESTION_FAILED", "CLASSIFICATION_FAILED", "PUBLISH_FAILED" -> "FAILED";
            case "RECHAZADO", "REJECTED", "DUPLICATE" -> "REJECTED";
            default -> status.trim().toUpperCase();
        };
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int intValue(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            return Integer.parseInt(s);
        }
        return 0;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value instanceof String s && !s.isBlank()) {
            return new BigDecimal(s);
        }
        return BigDecimal.ZERO;
    }

    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Instant instantValue(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toInstant(ZoneOffset.UTC);
        }
        return null;
    }

    private record IngestionSchedule(String status, Instant scheduledProcessAt) {}
}
