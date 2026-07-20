package ec.edu.espe.switchpayments.switchbatch.service.impl;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import ec.edu.espe.switchpayments.switchbatch.service.IPaymentLinePublisher;

@Service
public class PaymentLinePublisherImpl implements IPaymentLinePublisher {

    private static final Logger logger = LoggerFactory.getLogger(PaymentLinePublisherImpl.class);

    private final PubSubPaymentLinePublisher publisher;

    public PaymentLinePublisherImpl(PubSubPaymentLinePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    @Async
    public void publish(String batchId, Instant scheduledProcessAt, List<BatchLineMessage> messages) {
        logger.info("[ASYNC] Publicando {} lineas en Pub/Sub para lote {}", messages.size(), batchId);
        for (BatchLineMessage message : messages) {
            publisher.publish(message, scheduledProcessAt);
        }
    }
}
