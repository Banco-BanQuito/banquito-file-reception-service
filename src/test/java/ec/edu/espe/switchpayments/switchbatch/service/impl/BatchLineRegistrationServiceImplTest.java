package ec.edu.espe.switchpayments.switchbatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

import ec.edu.espe.switchpayments.switchbatch.dto.ParsedBatch;
import ec.edu.espe.switchpayments.switchbatch.dto.ParsedPaymentLine;
import ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchLineDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.BatchStatusLogRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchLineRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;

class BatchLineRegistrationServiceImplTest {

    private final PaymentBatchRepository paymentBatchRepository = mock(PaymentBatchRepository.class);
    private final PaymentBatchLineRepository paymentBatchLineRepository = mock(PaymentBatchLineRepository.class);
    private final BatchStatusLogRepository batchStatusLogRepository = mock(BatchStatusLogRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final BulkOperations bulkOperations = mock(BulkOperations.class);

    private BatchLineRegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BatchLineRegistrationServiceImpl(
                paymentBatchRepository, paymentBatchLineRepository, batchStatusLogRepository, mongoTemplate);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, PaymentBatchLineDocument.class))
                .thenReturn(bulkOperations);
        when(bulkOperations.insert(anyList())).thenReturn(bulkOperations);
    }

    @Test
    void registerLinesAsync_debeInsertarLineasYMarcarBatchConElEstadoListo() {
        PaymentBatchDocument existing = new PaymentBatchDocument();
        existing.setStatus("OLD_STATUS");
        when(paymentBatchRepository.findById("batch-1")).thenReturn(Optional.of(existing));

        service.registerLinesAsync("batch-1", batchWithLines(2), "EN_PROCESO");

        verify(bulkOperations).insert(anyList());
        verify(bulkOperations).execute();
        verify(mongoTemplate).updateFirst(any(), any(), eq(PaymentBatchDocument.class));

        ArgumentCaptor<BatchStatusLog> logCaptor = ArgumentCaptor.forClass(BatchStatusLog.class);
        verify(batchStatusLogRepository).save(logCaptor.capture());
        assertEquals("OLD_STATUS", logCaptor.getValue().getPreviousStatus());
        assertEquals("EN_PROCESO", logCaptor.getValue().getNewStatus());
    }

    @Test
    void registerLinesAsync_debeUsarEstadoPorDefecto_cuandoElBatchNoExisteAunEnMongo() {
        when(paymentBatchRepository.findById("batch-2")).thenReturn(Optional.empty());

        service.registerLinesAsync("batch-2", batchWithLines(1), "EN_PROCESO");

        ArgumentCaptor<BatchStatusLog> logCaptor = ArgumentCaptor.forClass(BatchStatusLog.class);
        verify(batchStatusLogRepository).save(logCaptor.capture());
        assertEquals("RECEIVING", logCaptor.getValue().getPreviousStatus());
    }

    @Test
    void registerLinesAsync_debeInsertarEnDosLotes_cuandoSuperaElTamanoDeChunk() {
        when(paymentBatchRepository.findById("batch-3")).thenReturn(Optional.empty());

        service.registerLinesAsync("batch-3", batchWithLines(1500), "EN_PROCESO");

        verify(bulkOperations, times(2)).insert(anyList());
        verify(bulkOperations, times(2)).execute();
    }

    @Test
    void registerLinesAsync_debeMarcarBatchComoIngestionFallida_cuandoMongoFalla() {
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, PaymentBatchLineDocument.class))
                .thenThrow(new RuntimeException("mongo no disponible"));
        when(paymentBatchRepository.findById("batch-4")).thenReturn(Optional.empty());

        service.registerLinesAsync("batch-4", batchWithLines(1), "EN_PROCESO");

        ArgumentCaptor<BatchStatusLog> logCaptor = ArgumentCaptor.forClass(BatchStatusLog.class);
        verify(batchStatusLogRepository).save(logCaptor.capture());
        assertEquals("INGESTION_FAILED", logCaptor.getValue().getNewStatus());
    }

    private ParsedBatch batchWithLines(int count) {
        List<ParsedPaymentLine> lines = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            lines.add(new ParsedPaymentLine(i, "001", "0912345678", "Beneficiario " + i,
                    "9876543210", BigDecimal.TEN, "REF-" + i, "beneficiario@example.com"));
        }
        return new ParsedBatch("0912345678", "NOMINA", "1234567890", "hash-1",
                count, BigDecimal.valueOf(count * 10L), count, BigDecimal.valueOf(count * 10L),
                "SEC-1", lines);
    }
}
