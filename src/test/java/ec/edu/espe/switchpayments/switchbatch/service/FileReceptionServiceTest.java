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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dto.FileReceptionResponse;
import ec.edu.espe.switchpayments.switchbatch.exception.DuplicateBatchException;
import ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.BatchStatusLogRepository;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;
import ec.edu.espe.switchpayments.switchbatch.service.impl.CsvBatchParserImpl;
import ec.edu.espe.switchpayments.switchbatch.service.impl.FileReceptionServiceImpl;

class FileReceptionServiceTest {
    private final PaymentBatchRepository paymentBatchRepository = org.mockito.Mockito.mock(PaymentBatchRepository.class);
    private final BatchStatusLogRepository batchStatusLogRepository = org.mockito.Mockito.mock(BatchStatusLogRepository.class);
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
                batchStatusLogRepository,
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
