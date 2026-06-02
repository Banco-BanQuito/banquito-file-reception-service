package ec.edu.espe.Switch.Batch.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import ec.edu.espe.Switch.Batch.service.impl.PaymentLinePublisherImpl;
import ec.edu.espe.Switch.Batch.service.impl.PaymentLinesReadyListener;

class PaymentLinePublisherTest {

    @Test
    void publishRunsAsyncSoHttp202DoesNotWaitForRabbitPublishing() throws Exception {
        var method = PaymentLinePublisherImpl.class.getMethod("publish", String.class, java.time.Instant.class, java.util.List.class);

        assertTrue(method.isAnnotationPresent(Async.class));
    }

    @Test
    void listenerRunsAsyncSoHttp202DoesNotWaitForRabbitPublishing() throws Exception {
        var method = PaymentLinesReadyListener.class.getMethod(
                "onPaymentLinesReady",
                ec.edu.espe.Switch.Batch.event.PaymentLinesReadyEvent.class);

        assertTrue(method.isAnnotationPresent(Async.class));
    }
}
