package ec.edu.espe.switchpayments.switchbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dto.FileReceptionResponse;
import ec.edu.espe.switchpayments.switchbatch.exception.DuplicateBatchException;
import ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchLineDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.BatchStatusLogRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchLineRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;
import ec.edu.espe.switchpayments.switchbatch.service.impl.CsvBatchParserImpl;
import ec.edu.espe.switchpayments.switchbatch.service.impl.FileReceptionServiceImpl;

class FileReceptionServiceTest {
    private final PaymentBatchRepository paymentBatchRepository = org.mockito.Mockito.mock(PaymentBatchRepository.class);
    private final PaymentBatchLineRepository paymentBatchLineRepository = org.mockito.Mockito.mock(PaymentBatchLineRepository.class);
    private final BatchStatusLogRepository batchStatusLogRepository = org.mockito.Mockito.mock(BatchStatusLogRepository.class);
    private final IBusinessDayService businessDayService = org.mockito.Mockito.mock(IBusinessDayService.class);
    private final ICoreBankingClient coreBankingClient = org.mockito.Mockito.mock(ICoreBankingClient.class);
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
                businessDayService,
                coreBankingClient,
                Clock.fixed(Instant.parse("2026-05-30T14:00:00Z"), ZoneId.systemDefault()));
        when(coreBankingClient.hasSufficientBalance(anyString(), any(java.math.BigDecimal.class)))
                .thenReturn(true);
        when(paymentBatchRepository.save(any(PaymentBatchDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(batchStatusLogRepository.save(any(BatchStatusLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentBatchLineRepository.saveAll(any()))
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

        ArgumentCaptor<Iterable<PaymentBatchLineDocument>> linesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(paymentBatchLineRepository).saveAll(linesCaptor.capture());
        List<PaymentBatchLineDocument> lines = toList(linesCaptor.getValue());
        assertEquals(1, lines.size());
        assertEquals("001", lines.get(0).getRoutingCode());
    }
    @Test
    void receive_debeLanzarExcepcion_yNOPublicarEvento_cuandoSaldoEsInsuficiente() {
        when(coreBankingClient.hasSufficientBalance(anyString(), any(java.math.BigDecimal.class)))
                .thenReturn(false);

        MockMultipartFile insufficientFundsFile = file(validCsvOneLine());
        assertThrows(IllegalArgumentException.class,
                () -> service.receive(insufficientFundsFile, "NOMINA", "0912345678"));

        verify(paymentBatchRepository, never()).save(any(PaymentBatchDocument.class));
        verify(paymentBatchLineRepository, never()).saveAll(any());
    }

    @Test
    void receive_debeLanzarExcepcion_yNOPublicarEvento_cuandoLoteEsDuplicado() {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(true);

        MockMultipartFile duplicateFile = file(validCsvOneLine());
        assertThrows(DuplicateBatchException.class,
                () -> service.receive(duplicateFile, "NOMINA", "0912345678"));

        verify(paymentBatchLineRepository).saveAll(any());
    }
    @Test
    void receive_debeGuardarTodasLasLineas_sinFiltrarPorRoutingCode() throws Exception {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(false);
        service.receive(file(validCsvWithTwoLines()), "NOMINA", "0912345678");
        ArgumentCaptor<Iterable<PaymentBatchLineDocument>> linesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(paymentBatchLineRepository).saveAll(linesCaptor.capture());

        List<String> routingCodes = toList(linesCaptor.getValue()).stream()
                .map(PaymentBatchLineDocument::getRoutingCode)
                .toList();
        assertEquals(2, routingCodes.size());
        assertEquals(List.of("001", "002"), routingCodes);
    }
    @Test
    void receive_debeGuardarLoteConStatusInicial_antesDePublicarEvento() throws Exception {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(false);

        service.receive(file(validCsvOneLine()), "NOMINA", "0912345678");
        ArgumentCaptor<PaymentBatchDocument> batchCaptor = ArgumentCaptor.forClass(PaymentBatchDocument.class);
        verify(paymentBatchRepository).save(batchCaptor.capture());
        assertEquals("EN_PROCESO", batchCaptor.getValue().getStatus());
    }

    private List<PaymentBatchLineDocument> toList(Iterable<PaymentBatchLineDocument> documents) {
        java.util.ArrayList<PaymentBatchLineDocument> result = new java.util.ArrayList<>();
        documents.forEach(result::add);
        return result;
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
