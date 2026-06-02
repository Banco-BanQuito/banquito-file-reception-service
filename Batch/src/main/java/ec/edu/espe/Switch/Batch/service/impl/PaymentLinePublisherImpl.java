package ec.edu.espe.Switch.Batch.service.impl;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ec.edu.espe.Switch.Batch.config.FileReceptionProperties;
import ec.edu.espe.Switch.Batch.dto.BatchLineMessage;
import ec.edu.espe.Switch.Batch.service.IPaymentLinePublisher;

@Service
public class PaymentLinePublisherImpl implements IPaymentLinePublisher {

    private static final Logger logger = LoggerFactory.getLogger(PaymentLinePublisherImpl.class);

    private final FileReceptionProperties properties;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    public PaymentLinePublisherImpl(FileReceptionProperties properties, ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        this.properties = properties;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
    }

    @Override
    @Async
    public void publish(String batchId, Instant scheduledProcessAt, List<BatchLineMessage> messages) {
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
}
