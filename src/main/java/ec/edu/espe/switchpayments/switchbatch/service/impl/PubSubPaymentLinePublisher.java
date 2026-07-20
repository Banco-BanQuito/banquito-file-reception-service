package ec.edu.espe.switchpayments.switchbatch.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dto.BatchLineMessage;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class PubSubPaymentLinePublisher {

    private final ObjectMapper objectMapper;
    private final FileReceptionProperties properties;
    private final Publisher publisher;

    public PubSubPaymentLinePublisher(ObjectMapper objectMapper, FileReceptionProperties properties) throws Exception {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.publisher = Publisher.newBuilder(ProjectTopicName.of(
                properties.getPubsubProjectId(), properties.getPubsubPaymentLinesTopic())).build();
    }

    public void publish(BatchLineMessage message, Instant scheduledProcessAt) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(message);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(payload))
                    .putAttributes("routingKey", routingKeyFor(message))
                    .putAttributes("source", "file-reception-service")
                    .putAttributes("scheduledProcessAt", scheduledProcessAt.toString())
                    .build();
            ApiFuture<String> messageId = publisher.publish(pubsubMessage);
            messageId.get();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar linea de pago en Pub/Sub", e);
        }
    }

    @PreDestroy
    public void shutdown() throws Exception {
        publisher.shutdown();
        publisher.awaitTermination(10, TimeUnit.SECONDS);
    }

    private String routingKeyFor(BatchLineMessage message) {
        if ("ON_US".equals(message.routingClassification())) {
            return properties.getPubsubRoutingKeyOnUs();
        }
        if ("OFF_US".equals(message.routingClassification())) {
            return properties.getPubsubRoutingKeyOffUs();
        }
        return properties.getPubsubRoutingKeyInvalid();
    }
}
