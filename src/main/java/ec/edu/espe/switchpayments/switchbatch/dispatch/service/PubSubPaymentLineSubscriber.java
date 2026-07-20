package ec.edu.espe.switchpayments.switchbatch.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PubSubPaymentLineSubscriber {

    private static final Logger log = LoggerFactory.getLogger(PubSubPaymentLineSubscriber.class);

    private final ObjectMapper objectMapper;
    private final FileReceptionProperties properties;
    private final PaymentDispatchService dispatchService;
    private final List<Subscriber> subscribers = new ArrayList<>();

    public PubSubPaymentLineSubscriber(ObjectMapper objectMapper,
                                       FileReceptionProperties properties,
                                       PaymentDispatchService dispatchService) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.dispatchService = dispatchService;
    }

    @PostConstruct
    public void start() {
        startSubscriber(properties.getPubsubPaymentLinesOnUsSubscription(), dispatchService::processOnUsLine);
        startSubscriber(properties.getPubsubPaymentLinesOffUsSubscription(), dispatchService::processOffUsLine);
        startSubscriber(properties.getPubsubPaymentLinesInvalidSubscription(), dispatchService::processInvalidLine);
    }

    @PreDestroy
    public void stop() {
        subscribers.forEach(Subscriber::stopAsync);
    }

    private void startSubscriber(String subscriptionName, PaymentLineHandler handler) {
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            try {
                BatchLineMessage payload = objectMapper.readValue(message.getData().toByteArray(), BatchLineMessage.class);
                handler.handle(payload);
                consumer.ack();
            } catch (Exception e) {
                log.error("Error procesando mensaje Pub/Sub de {}", subscriptionName, e);
                consumer.nack();
            }
        };

        Subscriber subscriber = Subscriber.newBuilder(
                ProjectSubscriptionName.of(properties.getPubsubProjectId(), subscriptionName), receiver).build();
        subscriber.startAsync().awaitRunning();
        subscribers.add(subscriber);
        log.info("Pub/Sub subscriber iniciado para {}", subscriptionName);
    }

    @FunctionalInterface
    private interface PaymentLineHandler {
        void handle(BatchLineMessage message);
    }
}
