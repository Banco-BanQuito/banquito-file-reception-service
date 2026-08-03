package ec.edu.espe.switchpayments.switchbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;

import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dto.FileReceptionResponse;
import ec.edu.espe.switchpayments.switchbatch.exception.DuplicateBatchException;
import ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.BatchStatusLogRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchLineRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;
import ec.edu.espe.switchpayments.switchbatch.service.impl.CsvBatchParserImpl;
import ec.edu.espe.switchpayments.switchbatch.service.impl.FileReceptionServiceImpl;

class FileReceptionServiceTest {
    private final PaymentBatchRepository paymentBatchRepository = org.mockito.Mockito.mock(PaymentBatchRepository.class);
    private final PaymentBatchLineRepository paymentBatchLineRepository = org.mockito.Mockito.mock(PaymentBatchLineRepository.class);
    private final BatchStatusLogRepository batchStatusLogRepository = org.mockito.Mockito.mock(BatchStatusLogRepository.class);
    private final MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
    private final IBusinessDayService businessDayService = org.mockito.Mockito.mock(IBusinessDayService.class);
    private final ICoreBankingClient coreBankingClient = org.mockito.Mockito.mock(ICoreBankingClient.class);
    private final IBatchLineRegistrationService batchLineRegistrationService =
            org.mockito.Mockito.mock(IBatchLineRegistrationService.class);
    private FileReceptionServiceImpl service;
    @BeforeEach
    void setUp() {
        FileReceptionProperties properties = new FileReceptionProperties();
        properties.setCutoffHour(23);
        properties.setDuplicateWindowDays(30);
        service = new FileReceptionServiceImpl(
                new CsvBatchParserImpl(properties),
                properties,
                paymentBatchRepository,
                paymentBatchLineRepository,
                batchStatusLogRepository,
                mongoTemplate,
                businessDayService,
                coreBankingClient,
                batchLineRegistrationService,
                Clock.fixed(Instant.parse("2026-05-30T14:00:00Z"), ZoneId.systemDefault()));
        when(coreBankingClient.hasSufficientBalance(anyString(), any(java.math.BigDecimal.class)))
                .thenReturn(true);
        when(paymentBatchRepository.save(any(PaymentBatchDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(batchStatusLogRepository.save(any(BatchStatusLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(businessDayService.isBusinessDay(any(LocalDate.class))).thenReturn(true);
        when(businessDayService.nextBusinessDay(any(LocalDate.class)))
                .thenAnswer(invocation -> invocation.<LocalDate>getArgument(0).plusDays(1));
    }
    @Test
    void receive_debeResponder202_yGuardarLineas_cuandoArchivoEsValido() throws Exception {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(false);

        FileReceptionResponse response = service.receive(file(validCsvOneLine()), "NOMINA", "0912345678");
        assertEquals("EN_PROCESO", response.status());

        verify(batchLineRegistrationService).registerLinesAsync(anyString(), any(), anyString());
    }
    @Test
    void receive_debeLanzarExcepcion_yNOPublicarEvento_cuandoSaldoEsInsuficiente() {
        when(coreBankingClient.hasSufficientBalance(anyString(), any(java.math.BigDecimal.class)))
                .thenReturn(false);

        MockMultipartFile insufficientFundsFile = file(validCsvOneLine());
        assertThrows(IllegalArgumentException.class,
                () -> service.receive(insufficientFundsFile, "NOMINA", "0912345678"));

        verify(paymentBatchRepository, never()).save(any(PaymentBatchDocument.class));
        verify(batchLineRegistrationService, never()).registerLinesAsync(anyString(), any(), anyString());
    }

    @Test
    void receive_debeLanzarExcepcion_yNOPublicarEvento_cuandoLoteEsDuplicado() {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(true);

        MockMultipartFile duplicateFile = file(validCsvOneLine());
        assertThrows(DuplicateBatchException.class,
                () -> service.receive(duplicateFile, "NOMINA", "0912345678"));

        verify(batchLineRegistrationService, never()).registerLinesAsync(anyString(), any(), anyString());
    }
    @Test
    void receive_debeDelegarRegistroAsincronico_sinFiltrarPorRoutingCode() throws Exception {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(false);
        service.receive(file(validCsvWithTwoLines()), "NOMINA", "0912345678");
        verify(batchLineRegistrationService).registerLinesAsync(anyString(), any(), anyString());
    }
    @Test
    void receive_debeGuardarLoteConStatusInicial_antesDePublicarEvento() throws Exception {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(false);

        service.receive(file(validCsvOneLine()), "NOMINA", "0912345678");
        ArgumentCaptor<PaymentBatchDocument> batchCaptor = ArgumentCaptor.forClass(PaymentBatchDocument.class);
        verify(paymentBatchRepository).save(batchCaptor.capture());
        assertEquals("RECEIVING", batchCaptor.getValue().getStatus());
    }

    @Test
    void getStatus_debeResponderProcessing_cuandoLoteEstaRegistrandoLineas() {
        PaymentBatchDocument batch = new PaymentBatchDocument();
        batch.setId("batch-1");
        batch.setStatus("RECEIVING");
        batch.setDeclaredTotalRecords(13000);
        batch.setReceivedAt(Instant.parse("2026-05-30T14:00:00Z"));
        when(paymentBatchRepository.findById("batch-1")).thenReturn(java.util.Optional.of(batch));
        when(paymentBatchLineRepository.countByBatchId("batch-1")).thenReturn(500L);

        var response = service.getStatus("batch-1");

        assertEquals("PROCESSING", response.status());
        assertEquals(13000, response.declaredTotalRecords());
        assertEquals(13000, response.inProcessRecords());
    }

    @Test
    void getStatus_debeLanzarExcepcion_cuandoLoteNoExisteEnNingunaColeccion() {
        when(mongoTemplate.findOne(any(), eq(Document.class), eq("payment_dispatch_batch")))
                .thenReturn(null);
        when(paymentBatchRepository.findById("batch-x")).thenReturn(java.util.Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getStatus("batch-x"));
    }

    @Test
    void getStatus_debeRetornarInfoDeDespacho_cuandoElLoteYaFueTomadoPorDispatch() {
        Document dispatchDoc = new Document();
        dispatchDoc.put("batchId", "batch-2");
        dispatchDoc.put("status", "COMPLETADO");
        dispatchDoc.put("declaredTotalRecords", 10);
        dispatchDoc.put("successfulRecords", 8);
        dispatchDoc.put("rejectedRecords", 2);
        dispatchDoc.put("successfulAmount", new java.math.BigDecimal("80.00"));
        dispatchDoc.put("rejectedAmount", new java.math.BigDecimal("20.00"));
        dispatchDoc.put("createdAt", Instant.parse("2026-05-30T14:00:00Z"));
        when(mongoTemplate.findOne(any(), eq(Document.class), eq("payment_dispatch_batch")))
                .thenReturn(dispatchDoc);

        var response = service.getStatus("batch-2");

        assertEquals("COMPLETED", response.status());
        assertEquals(10, response.declaredTotalRecords());
        assertEquals(8, response.successfulRecords());
        assertEquals(2, response.rejectedRecords());
        assertEquals(0, response.inProcessRecords());
        assertEquals(new java.math.BigDecimal("80.00"), response.successfulAmount());
    }

    @Test
    void getStatus_debeNormalizarEstadosDelDespacho_paraCasosFallidoRechazadoYDesconocido() {
        when(mongoTemplate.findOne(any(), eq(Document.class), eq("payment_dispatch_batch")))
                .thenReturn(dispatchDocumentWithStatus("FAILED"))
                .thenReturn(dispatchDocumentWithStatus("DUPLICATE"))
                .thenReturn(dispatchDocumentWithStatus(null));

        assertEquals("FAILED", service.getStatus("batch-3").status());
        assertEquals("REJECTED", service.getStatus("batch-4").status());
        assertEquals("UNKNOWN", service.getStatus("batch-5").status());
    }

    @Test
    void getStatus_debePromoverAEnProceso_cuandoTodasLasLineasYaFueronRegistradas() {
        PaymentBatchDocument batch = new PaymentBatchDocument();
        batch.setId("batch-6");
        batch.setStatus("RECEIVING");
        batch.setDeclaredTotalRecords(2);
        batch.setScheduledProcessAt(null);
        when(paymentBatchRepository.findById("batch-6")).thenReturn(java.util.Optional.of(batch));
        when(paymentBatchLineRepository.countByBatchId("batch-6")).thenReturn(2L);
        when(mongoTemplate.findOne(any(), eq(Document.class), eq("payment_dispatch_batch"))).thenReturn(null);

        var response = service.getStatus("batch-6");

        assertEquals("PROCESSING", response.status());
        verify(mongoTemplate).updateFirst(any(), any(), eq(PaymentBatchDocument.class));
    }

    @Test
    void getStatus_debePromoverAProgramado_cuandoElSiguienteProcesoQuedaAFuturo() {
        PaymentBatchDocument batch = new PaymentBatchDocument();
        batch.setId("batch-7");
        batch.setStatus("RECEIVING");
        batch.setDeclaredTotalRecords(2);
        batch.setScheduledProcessAt(Instant.parse("2026-06-01T00:01:00Z"));
        when(paymentBatchRepository.findById("batch-7")).thenReturn(java.util.Optional.of(batch));
        when(paymentBatchLineRepository.countByBatchId("batch-7")).thenReturn(2L);
        when(mongoTemplate.findOne(any(), eq(Document.class), eq("payment_dispatch_batch"))).thenReturn(null);

        var response = service.getStatus("batch-7");

        assertEquals("RECEIVED", response.status());
        verify(mongoTemplate).updateFirst(any(), any(), eq(PaymentBatchDocument.class));
    }

    @Test
    void getStatus_noDebePromover_cuandoElLoteNoEstaEnEstadoReceiving() {
        PaymentBatchDocument batch = new PaymentBatchDocument();
        batch.setId("batch-8");
        batch.setStatus("PROGRAMADO");
        batch.setDeclaredTotalRecords(2);
        when(paymentBatchRepository.findById("batch-8")).thenReturn(java.util.Optional.of(batch));
        when(paymentBatchLineRepository.countByBatchId("batch-8")).thenReturn(2L);
        when(mongoTemplate.findOne(any(), eq(Document.class), eq("payment_dispatch_batch"))).thenReturn(null);

        var response = service.getStatus("batch-8");

        assertEquals("RECEIVED", response.status());
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(PaymentBatchDocument.class));
    }

    @Test
    void receive_debeProgramarParaSiguienteDiaHabil_cuandoLlegaFueraDeDiaHabil() throws Exception {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(false);
        when(businessDayService.isBusinessDay(any(LocalDate.class))).thenReturn(false);

        service.receive(file(validCsvOneLine()), "NOMINA", "0912345678");

        verify(batchLineRegistrationService).registerLinesAsync(anyString(), any(), eq("PROGRAMADO"));
    }

    @Test
    void receive_debeLanzarExcepcion_cuandoArchivoEsVacio() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "archivo.csv", "text/csv", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> service.receive(emptyFile, "NOMINA", "0912345678"));
    }

    @Test
    void receive_debeLanzarExcepcion_cuandoExtensionNoEsCsvNiTxt() {
        MockMultipartFile pdfFile = new MockMultipartFile("file", "archivo.pdf", "application/pdf", "data".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.receive(pdfFile, "NOMINA", "0912345678"));
    }

    private Document dispatchDocumentWithStatus(String status) {
        Document doc = new Document();
        doc.put("batchId", "batch-x");
        doc.put("status", status);
        doc.put("declaredTotalRecords", 5);
        doc.put("successfulRecords", 0);
        doc.put("rejectedRecords", 0);
        return doc;
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "archivo.csv", "text/csv",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String validCsvOneLine() {
        return """
                0912345678,NOMINA,2026-05-30T14:00:00,1234567890,1,10.00
                1,001,1757158215,Ana Perez,9876543210,10.00,REF-1,ana@example.com
                SEC-1,1,10.00
                """;
    }
    private String validCsvWithTwoLines() {
        return """
                0912345678,NOMINA,2026-05-30T14:00:00,1234567890,2,30.00
                1,001,1757158215,Ana Perez,9876543210,10.00,REF-1,ana@example.com
                2,002,1757158216,Luis Mora,9876543211,20.00,REF-2,luis@example.com
                SEC-1,2,30.00
                """;
    }
}
