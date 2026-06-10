package ec.edu.espe.Switch.Batch.service.impl;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ec.edu.espe.Switch.Batch.config.FileReceptionProperties;
import ec.edu.espe.Switch.Batch.dto.BatchLineMessage;
import ec.edu.espe.Switch.Batch.dto.FileReceptionResponse;
import ec.edu.espe.Switch.Batch.dto.ParsedBatch;
import ec.edu.espe.Switch.Batch.dto.ParsedPaymentLine;
import ec.edu.espe.Switch.Batch.exception.DuplicateBatchException;
import ec.edu.espe.Switch.Batch.event.PaymentLinesReadyEvent;
import ec.edu.espe.Switch.Batch.model.BatchStatusLog;
import ec.edu.espe.Switch.Batch.model.PaymentBatchDocument;
import ec.edu.espe.Switch.Batch.model.PaymentFileValidation;
import ec.edu.espe.Switch.Batch.repository.BatchStatusLogRepository;
import ec.edu.espe.Switch.Batch.repository.PaymentBatchRepository;
import ec.edu.espe.Switch.Batch.repository.PaymentFileValidationRepository;
import ec.edu.espe.Switch.Batch.service.IBusinessDayService;
import ec.edu.espe.Switch.Batch.service.ICoreBankingClient;
import ec.edu.espe.Switch.Batch.service.ICsvBatchParser;
import ec.edu.espe.Switch.Batch.service.IFileReceptionService;
import ec.edu.espe.Switch.Batch.service.IRoutingCodeCatalogService;

@Service
public class FileReceptionServiceImpl implements IFileReceptionService {

    private static final Logger logger = LoggerFactory.getLogger(FileReceptionServiceImpl.class);

    private final ICsvBatchParser csvBatchParser;
    private final FileReceptionProperties properties;
    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentFileValidationRepository paymentFileValidationRepository;
    private final BatchStatusLogRepository batchStatusLogRepository;
    private final IRoutingCodeCatalogService routingCodeCatalogService;
    private final ICoreBankingClient coreBankingClient;
    private final IBusinessDayService businessDayService;
    private final ApplicationEventPublisher eventPublisher;

    public FileReceptionServiceImpl(ICsvBatchParser csvBatchParser,
                                    FileReceptionProperties properties,
                                    PaymentBatchRepository paymentBatchRepository,
                                    PaymentFileValidationRepository paymentFileValidationRepository,
                                    BatchStatusLogRepository batchStatusLogRepository,
                                    IRoutingCodeCatalogService routingCodeCatalogService,
                                    ICoreBankingClient coreBankingClient,
                                    IBusinessDayService businessDayService,
                                    ApplicationEventPublisher eventPublisher) {
        this.csvBatchParser = csvBatchParser;
        this.properties = properties;
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentFileValidationRepository = paymentFileValidationRepository;
        this.batchStatusLogRepository = batchStatusLogRepository;
        this.routingCodeCatalogService = routingCodeCatalogService;
        this.coreBankingClient = coreBankingClient;
        this.businessDayService = businessDayService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public FileReceptionResponse receive(MultipartFile file, String serviceType, String clientRuc) throws IOException {
        validateFile(file);
        ParsedBatch batch = csvBatchParser.parse(file.getInputStream(), serviceType, clientRuc);

        String batchId = UUID.randomUUID().toString();
        Instant receivedAt = Instant.now();
        IngestionSchedule schedule = resolveIngestionSchedule(receivedAt);
        boolean duplicateValid = !isDuplicate(batch.fileHash(), receivedAt);

        PaymentBatchDocument batchDocument = saveBatch(file, batch, batchId, receivedAt, schedule.scheduledProcessAt(),
                duplicateValid ? schedule.status() : "DUPLICATE");
        saveStatusLog(batchDocument.getId(), null, batchDocument.getStatus());
        if (!duplicateValid) {
            saveValidation(batchDocument.getId(), batch, false, true);
            throw new DuplicateBatchException("Lote duplicado");
        }

        boolean customerServiceActive = coreBankingClient.hasActiveMassPaymentService(batch.clientRuc(), batch.serviceType());
        if (!customerServiceActive) {
            String previousStatus = batchDocument.getStatus();
            batchDocument.setStatus("REJECTED");
            paymentBatchRepository.save(batchDocument);
            saveStatusLog(batchDocument.getId(), previousStatus, "REJECTED");
            saveValidation(batchDocument.getId(), batch, duplicateValid, false);
            throw new IllegalArgumentException("El RUC de cabecera no tiene activo el servicio de pagos masivos");
        }

        List<ParsedPaymentLine> acceptedLines = validCustomerServiceLines(batch);
        boolean sourceAccountValid = coreBankingClient.isFavoriteAccount(batch.sourceAccountNumber(), batch.clientRuc());
        boolean customerServiceValid = sourceAccountValid && acceptedLines.size() == batch.lines().size();
        saveValidation(batchDocument.getId(), batch, duplicateValid, customerServiceValid);

        eventPublisher.publishEvent(new PaymentLinesReadyEvent(
                batchId,
                schedule.scheduledProcessAt(),
                sourceAccountValid ? toMessages(batchId, batch, acceptedLines) : List.of()));

        return new FileReceptionResponse(
                batchId,
                schedule.status(),
                "Lote recibido, procesando en segundo plano",
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

    private boolean isDuplicate(String hash, Instant receivedAt) {
        Instant threshold = receivedAt.minus(properties.getDuplicateWindowDays(), ChronoUnit.DAYS);
        return paymentBatchRepository.existsByFileHashAndReceivedAtAfter(hash, threshold);
    }

    private PaymentBatchDocument saveBatch(MultipartFile file,
                                           ParsedBatch batch,
                                           String batchId,
                                           Instant receivedAt,
                                           Instant scheduledProcessAt,
                                           String status) {
        PaymentBatchDocument document = new PaymentBatchDocument();
        document.setId(batchId);
        document.setFileName(file.getOriginalFilename());
        document.setFileHash(batch.fileHash());
        document.setClientRuc(batch.clientRuc());
        document.setReceivedAt(receivedAt);
        document.setScheduledProcessAt(scheduledProcessAt);
        document.setStatus(status);
        document.setChannel("KONG_SWITCH");
        return paymentBatchRepository.save(document);
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
        validation.setValidationResult(duplicateValid ? (customerServiceValid ? "SUCCESS" : "PARTIAL_SUCCESS") : "DUPLICATE");
        validation.setValidatedAt(Instant.now());
        paymentFileValidationRepository.save(validation);
    }

    private void saveStatusLog(String batchId, String previousStatus, String newStatus) {
        BatchStatusLog log = new BatchStatusLog();
        log.setPaymentBatchId(batchId);
        log.setPreviousStatus(previousStatus);
        log.setNewStatus(newStatus);
        log.setChangedAt(Instant.now());
        batchStatusLogRepository.save(log);
    }

    private List<ParsedPaymentLine> validCustomerServiceLines(ParsedBatch batch) {
        List<ParsedPaymentLine> acceptedLines = batch.lines().stream()
                .filter(line -> routingCodeCatalogService.isValid(line.routingCode()))
                .filter(line -> coreBankingClient.isAccountValid(line.destinationAccountNumber(), batch.clientRuc()))
                .toList();
        int rejected = batch.lines().size() - acceptedLines.size();
        if (rejected > 0) {
            logger.warn("{} lineas rechazadas por ROUTING_CODE o cuenta invalida", rejected);
        }
        return acceptedLines;
    }

    private List<BatchLineMessage> toMessages(String batchId, ParsedBatch batch, List<ParsedPaymentLine> lines) {
        return lines.stream()
                .map(line -> new BatchLineMessage(
                        batchId,
                        line.lineNumber(),
                        line.routingCode(),
                        line.destinationAccountNumber(),
                        batch.sourceAccountNumber(),
                        batch.declaredRecords(),
                        line.amount(),
                        line.reference(),
                        line.beneficiaryName(),
                        line.beneficiaryEmail()))
                .toList();
    }

    private IngestionSchedule resolveIngestionSchedule(Instant receivedAt) {
        ZoneId zone = ZoneId.systemDefault();
        var receivedDateTime = receivedAt.atZone(zone).toLocalDateTime();
        boolean businessDay = businessDayService.isBusinessDay(receivedDateTime.toLocalDate());
        LocalTime cutoffTime = LocalTime.of(properties.getCutoffHour(), 0);
        if (businessDay && !receivedDateTime.toLocalTime().isAfter(cutoffTime)) {
            return new IngestionSchedule("RECEIVED", receivedAt);
        }
        LocalDate nextBusinessDay = businessDayService.nextBusinessDay(receivedDateTime.toLocalDate());
        Instant scheduledProcessAt = LocalDateTime.of(nextBusinessDay, java.time.LocalTime.of(0, 1))
                .atZone(zone)
                .toInstant();
        return new IngestionSchedule("SCHEDULED", scheduledProcessAt);
    }

    private record IngestionSchedule(String status, Instant scheduledProcessAt) {
    }
}
