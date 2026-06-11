package ec.edu.espe.switchbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import ec.edu.espe.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchbatch.dto.BatchLineMessage;
import ec.edu.espe.switchbatch.dto.FileReceptionResponse;
import ec.edu.espe.switchbatch.exception.DuplicateBatchException;
import ec.edu.espe.switchbatch.event.PaymentLinesReadyEvent;
import ec.edu.espe.switchbatch.model.BatchStatusLog;
import ec.edu.espe.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchbatch.model.PaymentFileValidation;
import ec.edu.espe.switchbatch.repository.BatchStatusLogRepository;
import ec.edu.espe.switchbatch.repository.PaymentBatchRepository;
import ec.edu.espe.switchbatch.repository.PaymentFileValidationRepository;
import ec.edu.espe.switchbatch.service.impl.CsvBatchParserImpl;
import ec.edu.espe.switchbatch.service.impl.FileReceptionServiceImpl;

class FileReceptionServiceTest {

    private final PaymentBatchRepository paymentBatchRepository = org.mockito.Mockito.mock(PaymentBatchRepository.class);
    private final PaymentFileValidationRepository validationRepository = org.mockito.Mockito.mock(PaymentFileValidationRepository.class);
    private final BatchStatusLogRepository batchStatusLogRepository = org.mockito.Mockito.mock(BatchStatusLogRepository.class);
    private final IRoutingCodeCatalogService routingCodeCatalogService = org.mockito.Mockito.mock(IRoutingCodeCatalogService.class);
    private final ICoreBankingClient coreBankingClient = org.mockito.Mockito.mock(ICoreBankingClient.class);
    private final IBusinessDayService businessDayService = org.mockito.Mockito.mock(IBusinessDayService.class);
    private final ApplicationEventPublisher eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final IPaymentLinePublisher paymentLinePublisher = org.mockito.Mockito.mock(IPaymentLinePublisher.class);

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
                validationRepository,
                batchStatusLogRepository,
                routingCodeCatalogService,
                coreBankingClient,
                businessDayService,
                eventPublisher);

        when(paymentBatchRepository.save(any(PaymentBatchDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(validationRepository.save(any(PaymentFileValidation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchStatusLogRepository.save(any(BatchStatusLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(businessDayService.isBusinessDay(any(LocalDate.class))).thenReturn(true);
        when(businessDayService.nextBusinessDay(any(LocalDate.class))).thenAnswer(invocation -> invocation.<LocalDate>getArgument(0).plusDays(1));
        when(coreBankingClient.hasActiveMassPaymentService(anyString(), anyString())).thenReturn(true);
        when(coreBankingClient.isFavoriteAccount(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void rejectsDuplicateBatchAndDoesNotPublish() {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(true);
        when(routingCodeCatalogService.isValid("001")).thenReturn(true);
        when(coreBankingClient.isAccountValid(anyString(), anyString())).thenReturn(true);

        MockMultipartFile batchFile = file(validCsvOneLine());
        assertThrows(DuplicateBatchException.class,
                () -> service.receive(batchFile, "NOMINA", "0912345678"));

        verify(paymentLinePublisher, never()).publish(anyString(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(coreBankingClient, never()).hasActiveMassPaymentService(anyString(), anyString());
        verify(coreBankingClient, never()).isAccountValid(anyString(), anyString());
        verify(coreBankingClient, never()).isFavoriteAccount(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsInvalidRoutingCodeLineWithoutRejectingBatch() throws Exception {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(false);
        when(routingCodeCatalogService.isValid("001")).thenReturn(true);
        when(routingCodeCatalogService.isValid("999")).thenReturn(false);
        when(coreBankingClient.isAccountValid(anyString(), anyString())).thenReturn(true);

        service.receive(file(validCsvWithInvalidRoutingLine()), "NOMINA", "0912345678");

        ArgumentCaptor<PaymentLinesReadyEvent> eventCaptor = ArgumentCaptor.forClass(PaymentLinesReadyEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        List<BatchLineMessage> messages = eventCaptor.getValue().messages();
        assertEquals(1, messages.size());
        assertEquals("001", messages.get(0).routingCode());
        assertEquals("1234567890", messages.get(0).originatingAccount());
        assertEquals(2, messages.get(0).declaredTotalRecords());

        ArgumentCaptor<PaymentFileValidation> validationCaptor = ArgumentCaptor.forClass(PaymentFileValidation.class);
        verify(validationRepository).save(validationCaptor.capture());
        assertEquals(false, validationCaptor.getValue().getCustomerServiceValid());
        assertEquals("PARTIAL_SUCCESS", validationCaptor.getValue().getValidationResult());
    }

    @Test
    void returnsAcceptedBeforeCallingRabbitPublisher() throws Exception {
        when(paymentBatchRepository.existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(
                anyString(), anyString(), any(), any())).thenReturn(false);
        when(routingCodeCatalogService.isValid("001")).thenReturn(true);
        when(coreBankingClient.isAccountValid(anyString(), anyString())).thenReturn(true);

        FileReceptionResponse response = service.receive(file(validCsvOneLine()), "NOMINA", "0912345678");

        assertEquals("RECEIVED", response.status());
        verify(paymentLinePublisher, never()).publish(anyString(), any(), any());
        verify(eventPublisher).publishEvent(any(PaymentLinesReadyEvent.class));
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "archivo.csv", "text/csv", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String validCsvOneLine() {
        return """
                0912345678,NOMINA,2026-05-30T14:00:00,1234567890,1,10.00
                1,001,1757158215,Ana Perez,9876543210,10.00,REF-1,ana@example.com
                SEC-1,1,10.00
                """;
    }

    private String validCsvWithInvalidRoutingLine() {
        return """
                0912345678,NOMINA,2026-05-30T14:00:00,1234567890,2,30.00
                1,001,1757158215,Ana Perez,9876543210,10.00,REF-1,ana@example.com
                2,999,1757158216,Luis Mora,9876543211,20.00,REF-2,luis@example.com
                SEC-1,2,30.00
                """;
    }
}
