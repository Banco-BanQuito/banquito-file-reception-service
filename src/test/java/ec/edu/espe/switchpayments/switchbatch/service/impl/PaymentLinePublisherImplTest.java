package ec.edu.espe.switchpayments.switchbatch.service.impl;

import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentLinePublisherImplTest {

    @Mock
    private PubSubPaymentLinePublisher pubSubPublisher;

    private PaymentLinePublisherImpl publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentLinePublisherImpl(pubSubPublisher);
    }

    @Test
    void publishDelegatesEachMessageToPubSubPublisher() {
        BatchLineMessage first = buildMessage("ON_US");
        BatchLineMessage second = buildMessage("OFF_US");
        Instant scheduledAt = Instant.now();

        publisher.publish("batch-1", scheduledAt, List.of(first, second));

        verify(pubSubPublisher).publish(first, scheduledAt);
        verify(pubSubPublisher).publish(second, scheduledAt);
        verify(pubSubPublisher, times(2)).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(scheduledAt));
    }

    @Test
    void publishDoesNothingWhenMessageListIsEmpty() {
        publisher.publish("batch-1", Instant.now(), List.of());

        verify(pubSubPublisher, times(0)).publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private BatchLineMessage buildMessage(String routingClassification) {
        return new BatchLineMessage(
                "batch-1", 1, "001", routingClassification, "0009999999", "0001111111",
                1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                "REF-1", "Beneficiario Test", "test@test.com");
    }
}
