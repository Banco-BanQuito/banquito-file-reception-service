package ec.edu.espe.switchpayments.switchbatch.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ec.edu.espe.switchpayments.switchbatch.dto.ParsedBatch;
import ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchLineDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.BatchStatusLogRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchLineRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;
import ec.edu.espe.switchpayments.switchbatch.service.IBatchLineRegistrationService;

@Service
public class BatchLineRegistrationServiceImpl implements IBatchLineRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(BatchLineRegistrationServiceImpl.class);
    private static final int BATCH_LINE_CHUNK_SIZE = 500;

    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentBatchLineRepository paymentBatchLineRepository;
    private final BatchStatusLogRepository batchStatusLogRepository;

    public BatchLineRegistrationServiceImpl(PaymentBatchRepository paymentBatchRepository,
                                            PaymentBatchLineRepository paymentBatchLineRepository,
                                            BatchStatusLogRepository batchStatusLogRepository) {
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentBatchLineRepository = paymentBatchLineRepository;
        this.batchStatusLogRepository = batchStatusLogRepository;
    }

    @Async
    @Override
    public void registerLinesAsync(String batchId, ParsedBatch batch, String readyStatus) {
        try {
            log.info("[INGESTION] Registrando {} lineas para batchId={}", batch.declaredRecords(), batchId);
            List<PaymentBatchLineDocument> chunk = new ArrayList<>(BATCH_LINE_CHUNK_SIZE);
            for (var line : batch.lines()) {
                chunk.add(toDocument(batchId, line));
                if (chunk.size() == BATCH_LINE_CHUNK_SIZE) {
                    paymentBatchLineRepository.saveAll(chunk);
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                paymentBatchLineRepository.saveAll(chunk);
            }
            markBatch(batchId, "RECEIVING", readyStatus);
            log.info("[INGESTION] Batch {} listo para el siguiente paso con estado {}", batchId, readyStatus);
        } catch (RuntimeException e) {
            log.error("[INGESTION] Error registrando lineas para batchId={}: {}", batchId, e.getMessage(), e);
            markBatch(batchId, "RECEIVING", "INGESTION_FAILED");
        }
    }

    private PaymentBatchLineDocument toDocument(String batchId, ec.edu.espe.switchpayments.switchbatch.dto.ParsedPaymentLine line) {
        PaymentBatchLineDocument document = new PaymentBatchLineDocument();
        document.setId(batchId + "-" + line.lineNumber());
        document.setBatchId(batchId);
        document.setLineNumber(line.lineNumber());
        document.setRoutingCode(line.routingCode());
        document.setBeneficiaryIdentification(line.beneficiaryIdentification());
        document.setBeneficiaryName(line.beneficiaryName());
        document.setDestinationAccountNumber(line.destinationAccountNumber());
        document.setAmount(line.amount());
        document.setReference(line.reference());
        document.setBeneficiaryEmail(line.beneficiaryEmail());
        return document;
    }

    private void markBatch(String batchId, String previousStatus, String newStatus) {
        paymentBatchRepository.findById(batchId).ifPresent(batch -> {
            String currentStatus = batch.getStatus();
            batch.setStatus(newStatus);
            paymentBatchRepository.save(batch);
            saveStatusLog(batchId, currentStatus != null ? currentStatus : previousStatus, newStatus);
        });
    }

    private void saveStatusLog(String batchId, String previousStatus, String newStatus) {
        BatchStatusLog statusLog = new BatchStatusLog();
        statusLog.setPaymentBatchId(batchId);
        statusLog.setPreviousStatus(previousStatus);
        statusLog.setNewStatus(newStatus);
        statusLog.setChangedAt(Instant.now());
        batchStatusLogRepository.save(statusLog);
    }
}
