package ec.edu.espe.switchpayments.switchbatch.dispatch.client;

import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.OffUsClearingMessage;
import ec.edu.espe.switchpayments.switchbatch.grpc.clearing.ClearingServiceGrpc;
import ec.edu.espe.switchpayments.switchbatch.grpc.clearing.OffUsPaymentRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Fase 5 Parte 2 (TAREA B): reemplaza a PubSubClearingPublisher como via de entrega de pagos
 * Off-Us a banquito-clearinghouse-service. Replica el mismo patron ya probado en
 * payment-line-subscriber-service (ClearinghouseClient.sendOffUsPayment) contra el mismo
 * contrato ClearingService, para que OffUsConsumerService.process(...) persista y liquide el
 * pago (NOSTRO_SETTLEMENT_OUTBOUND) sin depender de un topico Pub/Sub sin consumidor activo.
 */
@Component
public class ClearinghouseClient {

    private final ClearingServiceGrpc.ClearingServiceBlockingStub clearingService;
    private final FileReceptionProperties properties;

    public ClearinghouseClient(ClearingServiceGrpc.ClearingServiceBlockingStub clearingService,
                               FileReceptionProperties properties) {
        this.clearingService = clearingService;
        this.properties = properties;
    }

    public void sendOffUsPayment(OffUsClearingMessage message) {
        clearingService
                .withDeadlineAfter(properties.getGrpcDeadlineSeconds(), TimeUnit.SECONDS)
                .registerOffUsPayment(toRequest(message));
    }

    private OffUsPaymentRequest toRequest(OffUsClearingMessage message) {
        return OffUsPaymentRequest.newBuilder()
                .setBatchId(message.getBatchId().toString())
                .setTransactionId(message.getTransactionId().toString())
                .setRoutingCode(nullToEmpty(message.getRoutingCode()))
                .setOriginAccount(nullToEmpty(message.getOriginAccount()))
                .setDestinationAccount(nullToEmpty(message.getDestinationAccount()))
                .setAmount(message.getAmount() != null ? message.getAmount().toPlainString() : "")
                .setCurrency(nullToEmpty(message.getCurrency()))
                .setConcept(nullToEmpty(message.getConcept()))
                .setValueDate(message.getValueDate() != null ? message.getValueDate().toString() : "")
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
