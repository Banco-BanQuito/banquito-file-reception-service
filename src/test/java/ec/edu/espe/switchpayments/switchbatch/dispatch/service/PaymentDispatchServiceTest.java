package ec.edu.espe.switchpayments.switchbatch.dispatch.service;

import com.mongodb.client.result.UpdateResult;
import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dispatch.client.NotificationGrpcClient;
import ec.edu.espe.switchpayments.switchbatch.dispatch.client.TariffGrpcClient;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.OffUsClearingMessage;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentBatch;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentDetail;
import ec.edu.espe.switchpayments.switchbatch.dispatch.repository.PaymentDispatchDetailRepository;
import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import ec.edu.espe.switchpayments.switchbatch.service.ICoreBankingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentDispatchServiceTest {

    private static final String BATCH_1 = "11111111-1111-1111-1111-111111111111";
    private static final String BATCH_2 = "22222222-2222-2222-2222-222222222222";

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
    private RabbitTemplate rabbitTemplate;

    private FileReceptionProperties properties;
    private PaymentDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        properties = new FileReceptionProperties();
        properties.setClearingExchange("clearing.exchange");
        properties.setClearingRoutingKey("clearing.outbound");
        properties.setCorporateAccountNumber("0000000000");
        properties.setDispatchLocalCompletionEnabled(false);

        dispatchService = new PaymentDispatchService(detailRepository, mongoTemplate, coreBankingClient,
                tariffClient, notificationClient, rabbitTemplate, properties);

        when(detailRepository.save(any(PaymentDetail.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(PaymentBatch.class)))
                .thenReturn(updateResult);

        PaymentBatch batch = new PaymentBatch();
        batch.setBatchId(BATCH_1);
        batch.setDeclaredTotalRecords(0);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(PaymentBatch.class)))
                .thenReturn(batch);
    }

    @Test
    void processOnUsLine_debeAcreditar_cuandoCodigoEs001() {
        BatchLineMessage message = buildMessage(BATCH_1, 1, "001", "ON_US", "0009876543", new BigDecimal("500.00"));

        dispatchService.processOnUsLine(message);

        verify(coreBankingClient).batchCredit(
                eq(BATCH_1), eq("0009876543"), eq(new BigDecimal("500.00")), any(), any());

        ArgumentCaptor<PaymentDetail> captor = ArgumentCaptor.forClass(PaymentDetail.class);
        verify(detailRepository, atLeast(2)).save(captor.capture());
        PaymentDetail finalDetail = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalDetail.getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    void processOffUsLine_debePublicarEnColaDeSalida_cuandoCodigoEs002() {
        BatchLineMessage message = buildMessage(BATCH_1, 2, "002", "OFF_US", "0009999999", new BigDecimal("200.00"));

        dispatchService.processOffUsLine(message);

        ArgumentCaptor<OffUsClearingMessage> clearingCaptor = ArgumentCaptor.forClass(OffUsClearingMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("clearing.exchange"), eq("clearing.outbound"), clearingCaptor.capture());

        OffUsClearingMessage adapted = clearingCaptor.getValue();
        assertThat(adapted.getBatchId()).hasToString(BATCH_1);
        assertThat(adapted.getRoutingCode()).isEqualTo("002");
        assertThat(adapted.getDestinationAccount()).isEqualTo("0009999999");
        assertThat(adapted.getOriginAccount()).isEqualTo("0001111111");
        assertThat(adapted.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(adapted.getCurrency()).isEqualTo("USD");
        assertThat(adapted.getConcept()).isEqualTo("REF-2");
        assertThat(adapted.getValueDate()).isNotNull();

        ArgumentCaptor<PaymentDetail> captor = ArgumentCaptor.forClass(PaymentDetail.class);
        verify(detailRepository, atLeast(2)).save(captor.capture());
        PaymentDetail finalDetail = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalDetail.getStatus()).isEqualTo("CLEARED");
    }

    @Test
    void processInvalidLine_debeRechazar_cuandoCodigoEsInvalido() {
        BatchLineMessage message = buildMessage(BATCH_1, 3, "999", null, "0009999999", new BigDecimal("100.00"));

        dispatchService.processInvalidLine(message);

        verify(coreBankingClient, never()).batchCredit(any(), any(), any(), any(), any());
        verifyNoInteractions(rabbitTemplate);

        ArgumentCaptor<PaymentDetail> captor = ArgumentCaptor.forClass(PaymentDetail.class);
        verify(detailRepository, atLeast(2)).save(captor.capture());
        PaymentDetail finalDetail = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalDetail.getStatus()).isEqualTo("REJECTED");
        assertThat(finalDetail.getErrorCode()).isEqualTo("ROUTING_CODE_INVALID");
    }

    @Test
    void processOnUsLine_debeIgnorarMensajeDuplicado_cuandoHayDuplicateKey() {
        BatchLineMessage message = buildMessage(BATCH_1, 1, "001", "ON_US", "0009876543", new BigDecimal("500.00"));
        when(detailRepository.save(any(PaymentDetail.class)))
                .thenThrow(new DuplicateKeyException("duplicate batch_line_unique"));

        dispatchService.processOnUsLine(message);

        verifyNoInteractions(coreBankingClient);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void processOnUsLine_debeGuardarDetalleInicial_conEstadoPROCESSING() {
        List<String> statusesPorOrden = new ArrayList<>();
        when(detailRepository.save(any(PaymentDetail.class))).thenAnswer(inv -> {
            PaymentDetail d = inv.getArgument(0);
            statusesPorOrden.add(d.getStatus());
            return d;
        });

        BatchLineMessage message = buildMessage(BATCH_1, 5, "001", "ON_US", "0001234567", new BigDecimal("300.00"));
        dispatchService.processOnUsLine(message);

        assertThat(statusesPorOrden).isNotEmpty();
        assertThat(statusesPorOrden.get(0)).isEqualTo("PROCESSING");
        assertThat(statusesPorOrden.get(statusesPorOrden.size() - 1)).isEqualTo("PROCESSED");

        verify(detailRepository, atLeast(2)).save(argThat(d ->
                BATCH_1.equals(d.getBatchId()) && d.getLineNumber() == 5));
    }

    @Test
    void processOffUsLine_debePublicarEnColaDeSalida_paraTodosLosCodigos() {
        String[] offusCodes = {"003", "004", "005", "010", "017", "021", "023"};
        for (String code : offusCodes) {
            BatchLineMessage message = buildMessage(BATCH_2, 1, code, "OFF_US", "0009999999", new BigDecimal("50.00"));
            dispatchService.processOffUsLine(message);
        }
        verify(rabbitTemplate, times(offusCodes.length))
                .convertAndSend(eq("clearing.exchange"), eq("clearing.outbound"), any(OffUsClearingMessage.class));
    }

    private BatchLineMessage buildMessage(String batchId, int lineNumber,
                                          String routingCode, String routingClassification,
                                          String accountDest, BigDecimal amount) {
        return new BatchLineMessage(
                batchId, lineNumber, routingCode, routingClassification, accountDest, "0001111111",
                10, new BigDecimal("1000.00"), amount,
                "REF-" + lineNumber, "Beneficiario Test", "test@test.com");
    }
}
