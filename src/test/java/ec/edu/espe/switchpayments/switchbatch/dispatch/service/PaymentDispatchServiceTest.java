package ec.edu.espe.switchpayments.switchbatch.dispatch.service;

import com.mongodb.client.result.UpdateResult;
import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dispatch.client.NotificationGrpcClient;
import ec.edu.espe.switchpayments.switchbatch.dispatch.client.TariffGrpcClient;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.OffUsClearingMessage;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentBatch;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentDetail;
import ec.edu.espe.switchpayments.switchbatch.dispatch.repository.PaymentDispatchDetailRepository;
import ec.edu.espe.banquito.banquitotariffservice.grpc.TariffCalculationGrpcResponse;
import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import ec.edu.espe.switchpayments.switchbatch.service.ICoreBankingClient;
import ec.edu.espe.switchpayments.switchbatch.service.impl.PubSubClearingPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentDispatchServiceTest {

    private static final String BATCH_1 = "11111111-1111-1111-1111-111111111111";

    @Mock
    private PaymentDispatchDetailRepository detailRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private ICoreBankingClient coreBankingClient;
    @Mock
    private TariffGrpcClient tariffClient;
    @Mock
    private NotificationGrpcClient notificationClient;
    @Mock
    private PubSubClearingPublisher clearingPublisher;

    private FileReceptionProperties properties;
    private PaymentDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        properties = new FileReceptionProperties();
        properties.setCorporateAccountNumber("0000000000");

        dispatchService = new PaymentDispatchService(detailRepository, mongoTemplate, coreBankingClient,
                tariffClient, notificationClient, clearingPublisher, properties);

        when(detailRepository.save(any(PaymentDetail.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateResult claimResult = mock(UpdateResult.class);
        when(claimResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(PaymentBatch.class)))
                .thenReturn(claimResult);

        PaymentBatch batch = new PaymentBatch();
        batch.setBatchId(BATCH_1);
        batch.setDeclaredTotalRecords(0);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(PaymentBatch.class)))
                .thenReturn(batch);
    }

    private BatchLineMessage buildMessage(String batchId, int lineNumber, String routingCode,
                                          String routingClassification, String accountDest, BigDecimal amount) {
        return new BatchLineMessage(
                batchId, lineNumber, routingCode, routingClassification, accountDest, "0001111111",
                10, new BigDecimal("1000.00"), amount,
                "REF-" + lineNumber, "Beneficiario Test", "test@test.com");
    }

    @Test
    void processOnUsLineCreditsAccountAndFlushesSuccessfulCounter() {
        BatchLineMessage message = buildMessage(BATCH_1, 1, "001", "ON_US", "0009876543", new BigDecimal("500.00"));

        dispatchService.processOnUsLine(message);
        dispatchService.shutdownExecutors();

        verify(coreBankingClient).batchCredit(
                eq(BATCH_1), eq("0001111111"), eq("0009876543"), eq(new BigDecimal("500.00")), any(), any());

        ArgumentCaptor<PaymentDetail> captor = ArgumentCaptor.forClass(PaymentDetail.class);
        verify(detailRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getStatus()).isEqualTo("PROCESSED");

        verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
                .findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PaymentBatch.class));
    }

    @Test
    void processOffUsLinePublishesClearingMessageAndMarksCleared() {
        BatchLineMessage message = buildMessage(BATCH_1, 2, "002", "OFF_US", "0009999999", new BigDecimal("200.00"));

        dispatchService.processOffUsLine(message);

        ArgumentCaptor<OffUsClearingMessage> clearingCaptor = ArgumentCaptor.forClass(OffUsClearingMessage.class);
        verify(clearingPublisher).publish(clearingCaptor.capture());

        OffUsClearingMessage adapted = clearingCaptor.getValue();
        assertThat(adapted.getRoutingCode()).isEqualTo("002");
        assertThat(adapted.getDestinationAccount()).isEqualTo("0009999999");
        assertThat(adapted.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));

        ArgumentCaptor<PaymentDetail> captor = ArgumentCaptor.forClass(PaymentDetail.class);
        verify(detailRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getStatus()).isEqualTo("CLEARED");
    }

    @Test
    void processInvalidLineRejectsWithoutCallingCoreOrClearing() {
        BatchLineMessage message = buildMessage(BATCH_1, 3, "999", null, "0009999999", new BigDecimal("100.00"));

        dispatchService.processInvalidLine(message);

        verify(coreBankingClient, never()).batchCredit(any(), any(), any(), any(), any(), any());
        verify(clearingPublisher, never()).publish(any());

        ArgumentCaptor<PaymentDetail> captor = ArgumentCaptor.forClass(PaymentDetail.class);
        verify(detailRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        PaymentDetail finalDetail = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalDetail.getStatus()).isEqualTo("REJECTED");
        assertThat(finalDetail.getErrorCode()).isEqualTo("ROUTING_CODE_INVALID");
    }

    @Test
    void processOnUsLineIgnoresDuplicateMessage() {
        BatchLineMessage message = buildMessage(BATCH_1, 1, "001", "ON_US", "0009876543", new BigDecimal("500.00"));
        when(detailRepository.save(any(PaymentDetail.class)))
                .thenThrow(new DuplicateKeyException("duplicate batch_line_unique"));

        dispatchService.processOnUsLine(message);

        verify(coreBankingClient, never()).batchCredit(any(), any(), any(), any(), any(), any());
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(PaymentBatch.class));
    }

    @Test
    void ensureBatchDebitedRejectsLineWhenInitialDebitFails() {
        org.mockito.Mockito.doThrow(new RuntimeException("Core no disponible"))
                .when(coreBankingClient).corporateDebit(any(), any(), any(), any());
        when(mongoTemplate.findOne(any(Query.class), eq(PaymentBatch.class))).thenReturn(new PaymentBatch());

        BatchLineMessage message = buildMessage(BATCH_1, 1, "001", "ON_US", "0009876543", new BigDecimal("500.00"));
        dispatchService.processOnUsLine(message);

        verify(coreBankingClient, never()).batchCredit(any(), any(), any(), any(), any(), any());
        ArgumentCaptor<PaymentDetail> captor = ArgumentCaptor.forClass(PaymentDetail.class);
        verify(detailRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        PaymentDetail lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo("REJECTED");
        assertThat(lastSaved.getErrorCode()).isEqualTo("BATCH_DEBIT_FAILED");
    }

    @Test
    void completeBatchChargesCommissionWhenTariffReturnsPositiveCharge() {
        PaymentBatch completedBatch = new PaymentBatch();
        completedBatch.setBatchId(BATCH_1);
        completedBatch.setDeclaredTotalRecords(1);
        completedBatch.setSuccessfulRecords(1);
        completedBatch.setRejectedRecords(0);
        completedBatch.setRejectedAmount(BigDecimal.ZERO);
        completedBatch.setOriginatingAccount("0001111111");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(PaymentBatch.class)))
                .thenReturn(completedBatch);
        when(tariffClient.calculateTariff(any()))
                .thenReturn(TariffCalculationGrpcResponse.newBuilder().setTotalCharge("5.75").build());

        BatchLineMessage message = buildMessage(BATCH_1, 1, "001", "ON_US", "0009876543", new BigDecimal("500.00"));
        dispatchService.processOnUsLine(message);
        dispatchService.shutdownExecutors();

        verify(coreBankingClient).corporateDebit(BATCH_1, "0001111111", BigDecimal.ZERO, new BigDecimal("5.75"));
    }

    @Test
    void completeBatchInLocalModeSkipsCommissionAndRefund() {
        properties.setDispatchLocalCompletionEnabled(true);
        PaymentBatch completedBatch = new PaymentBatch();
        completedBatch.setBatchId(BATCH_1);
        completedBatch.setDeclaredTotalRecords(1);
        completedBatch.setSuccessfulRecords(1);
        completedBatch.setRejectedRecords(0);
        completedBatch.setRejectedAmount(BigDecimal.ZERO);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(PaymentBatch.class)))
                .thenReturn(completedBatch);

        BatchLineMessage message = buildMessage(BATCH_1, 1, "001", "ON_US", "0009876543", new BigDecimal("500.00"));
        dispatchService.processOnUsLine(message);
        dispatchService.shutdownExecutors();

        verify(tariffClient, never()).calculateTariff(any());
        verify(coreBankingClient, never()).corporateRefund(any(), any(), any());
    }
}
