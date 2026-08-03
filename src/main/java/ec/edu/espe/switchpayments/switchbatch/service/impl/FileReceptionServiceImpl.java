package ec.edu.espe.switchpayments.switchbatch.service.impl;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dto.FileReceptionResponse;
import ec.edu.espe.switchpayments.switchbatch.dto.ParsedBatch;
import ec.edu.espe.switchpayments.switchbatch.exception.DuplicateBatchException;
import ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.BatchStatusLogRepository;
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
    private final BatchStatusLogRepository batchStatusLogRepository;
    private final IBusinessDayService businessDayService;
    private final ICoreBankingClient coreBankingClient;
    private final IBatchLineRegistrationService batchLineRegistrationService;
    private final Clock clock;
    @Autowired
    public FileReceptionServiceImpl(ICsvBatchParser csvBatchParser,
                                    FileReceptionProperties properties,
                                    PaymentBatchRepository paymentBatchRepository,
                                    BatchStatusLogRepository batchStatusLogRepository,
                                    IBusinessDayService businessDayService,
                                    ICoreBankingClient coreBankingClient,
                                    IBatchLineRegistrationService batchLineRegistrationService) {
        this(csvBatchParser, properties, paymentBatchRepository, batchStatusLogRepository,
                businessDayService, coreBankingClient, batchLineRegistrationService, Clock.systemDefaultZone());
    }
    public FileReceptionServiceImpl(ICsvBatchParser csvBatchParser,
                                    FileReceptionProperties properties,
                                    PaymentBatchRepository paymentBatchRepository,
                                    BatchStatusLogRepository batchStatusLogRepository,
                                    IBusinessDayService businessDayService,
                                    ICoreBankingClient coreBankingClient,
                                    IBatchLineRegistrationService batchLineRegistrationService,
                                    Clock clock) {
        this.csvBatchParser = csvBatchParser;
        this.properties = properties;
        this.paymentBatchRepository = paymentBatchRepository;
        this.batchStatusLogRepository = batchStatusLogRepository;
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
    private record IngestionSchedule(String status, Instant scheduledProcessAt) {}
}
