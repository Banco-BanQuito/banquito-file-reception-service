package ec.edu.espe.switchpayments.switchbatch.service.impl;

import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLinePublisherImplTest {

    @Mock
    private ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private FileReceptionProperties properties;
    private PaymentLinePublisherImpl publisher;

    @BeforeEach
    void setUp() {
        properties = new FileReceptionProperties();
        properties.setRabbitEnabled(true);
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);
        publisher = new PaymentLinePublisherImpl(properties, rabbitTemplateProvider);
    }

    @Test
    void publish_debeUsarRoutingKeyOnUs_cuandoClasificacionEsOnUs() {
        BatchLineMessage message = buildMessage("ON_US");

        publisher.publish("batch-1", Instant.now(), List.of(message));

        verify(rabbitTemplate).convertAndSend(eq(properties.getRabbitExchange()),
                eq(properties.getRabbitRoutingKeyOnUs()), eq(message), any(MessagePostProcessor.class));
    }

    @Test
    void publish_debeUsarRoutingKeyOffUs_cuandoClasificacionEsOffUs() {
        BatchLineMessage message = buildMessage("OFF_US");

        publisher.publish("batch-1", Instant.now(), List.of(message));

        verify(rabbitTemplate).convertAndSend(eq(properties.getRabbitExchange()),
                eq(properties.getRabbitRoutingKeyOffUs()), eq(message), any(MessagePostProcessor.class));
    }

    @Test
    void publish_debeUsarRoutingKeyInvalid_cuandoClasificacionNoEsReconocida() {
        BatchLineMessage message = buildMessage(null);

        publisher.publish("batch-1", Instant.now(), List.of(message));

        verify(rabbitTemplate).convertAndSend(eq(properties.getRabbitExchange()),
                eq(properties.getRabbitRoutingKeyInvalid()), eq(message), any(MessagePostProcessor.class));
    }

    private BatchLineMessage buildMessage(String routingClassification) {
        return new BatchLineMessage(
                "batch-1", 1, "001", routingClassification, "0009999999", "0001111111",
                1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                "REF-1", "Beneficiario Test", "test@test.com");
    }
}
