package ec.edu.espe.switchbatch.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ec.edu.espe.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchbatch.config.PaymentLineTransport;
import ec.edu.espe.switchbatch.dto.BatchLineMessage;
import ec.edu.espe.switchbatch.grpc.BatchLine;
import ec.edu.espe.switchbatch.grpc.PaymentLineIngestionServiceGrpc;
import ec.edu.espe.switchbatch.grpc.PublishBatchLinesRequest;
import ec.edu.espe.switchbatch.grpc.PublishBatchLinesResponse;
import ec.edu.espe.switchbatch.service.IPaymentLinePublisher;

@Service
public class PaymentLinePublisherImpl implements IPaymentLinePublisher {

    private static final Logger logger = LoggerFactory.getLogger(PaymentLinePublisherImpl.class);

    private final FileReceptionProperties properties;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final ObjectProvider<PaymentLineIngestionServiceGrpc.PaymentLineIngestionServiceBlockingStub> paymentLineStubProvider;

    public PaymentLinePublisherImpl(
            FileReceptionProperties properties,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            ObjectProvider<PaymentLineIngestionServiceGrpc.PaymentLineIngestionServiceBlockingStub> paymentLineStubProvider) {
        this.properties = properties;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.paymentLineStubProvider = paymentLineStubProvider;
    }

    @Override
    @Async
    public void publish(String batchId, Instant scheduledProcessAt, List<BatchLineMessage> messages) {
        if (PaymentLineTransport.GRPC.equals(properties.getPaymentLineTransport())) {
            publishWithGrpc(batchId, scheduledProcessAt, messages);
            return;
        }

        publishWithRabbitMq(batchId, scheduledProcessAt, messages);
    }

    private void publishWithRabbitMq(String batchId, Instant scheduledProcessAt, List<BatchLineMessage> messages) {
        if (!properties.isRabbitEnabled()) {
            logger.info("RabbitMQ deshabilitado. {} lineas listas para batch {}", messages.size(), batchId);
            return;
        }

        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate == null) {
            logger.warn("RabbitTemplate no disponible. No se publicaron lineas para batch {}", batchId);
            return;
        }

        MessagePostProcessor timestampPostProcessor = message -> {
            message.getMessageProperties().setTimestamp(java.util.Date.from(scheduledProcessAt));
            return message;
        };

        for (BatchLineMessage message : messages) {
            rabbitTemplate.convertAndSend(properties.getRabbitQueue(), message, timestampPostProcessor);
        }
    }

    private void publishWithGrpc(String batchId, Instant scheduledProcessAt, List<BatchLineMessage> messages) {
        PaymentLineIngestionServiceGrpc.PaymentLineIngestionServiceBlockingStub stub = paymentLineStubProvider.getIfAvailable();
        if (stub == null) {
            logger.warn("Stub gRPC no disponible. No se publicaron lineas para batch {}", batchId);
            return;
        }

        PublishBatchLinesRequest request = PublishBatchLinesRequest.newBuilder()
                .setBatchId(batchId)
                .setScheduledProcessAt(scheduledProcessAt.toString())
                .addAllLines(messages.stream().map(this::toGrpcLine).toList())
                .build();

        PublishBatchLinesResponse response = stub
                .withDeadlineAfter(properties.getGrpcDeadlineSeconds(), TimeUnit.SECONDS)
                .publishBatchLines(request);

        if (response.getAccepted()) {
            logger.info("{} lineas publicadas por gRPC para batch {}", messages.size(), batchId);
        } else {
            logger.warn("El receptor gRPC rechazo batch {}: {}", batchId, response.getMessage());
        }
    }

    private BatchLine toGrpcLine(BatchLineMessage message) {
        return BatchLine.newBuilder()
                .setBatchId(message.batchId())
                .setLineNumber(message.lineNumber())
                .setRoutingCode(message.routingCode())
                .setAccountDestination(message.accountDestination())
                .setAmount(message.amount().toPlainString())
                .setReference(nullToEmpty(message.reference()))
                .setBeneficiaryName(nullToEmpty(message.beneficiaryName()))
                .setBeneficiaryEmail(nullToEmpty(message.beneficiaryEmail()))
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
