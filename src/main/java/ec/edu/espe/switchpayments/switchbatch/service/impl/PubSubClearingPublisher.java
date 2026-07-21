package ec.edu.espe.switchpayments.switchbatch.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.OffUsClearingMessage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PubSubClearingPublisher {

    private static final Logger log = LoggerFactory.getLogger(PubSubClearingPublisher.class);

    private final ObjectMapper objectMapper;
    private final FileReceptionProperties properties;
    private final Publisher publisher;

    public PubSubClearingPublisher(ObjectMapper objectMapper, FileReceptionProperties properties) throws Exception {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.publisher = Publisher.newBuilder(ProjectTopicName.of(
                properties.getPubsubProjectId(), properties.getPubsubClearingEventsTopic())).build();
    }

    public void publish(OffUsClearingMessage message) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(message);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(payload))
                    .putAttributes("routingKey", properties.getPubsubRoutingKeyClearingOutbound())
                    .putAttributes("source", "file-reception-service")
                    .build();
            ApiFuture<String> messageId = publisher.publish(pubsubMessage);
            messageId.get();
        } catch (Exception e) {
            log.error("No se pudo publicar evento de clearing en Pub/Sub. projectId={}, topic={}, routingKey={}, batchId={}, transactionId={}, cause={}",
                    properties.getPubsubProjectId(),
                    properties.getPubsubClearingEventsTopic(),
                    properties.getPubsubRoutingKeyClearingOutbound(),
                    message.getBatchId(),
                    message.getTransactionId(),
                    e.getMessage(),
                    e);
            throw new IllegalStateException("No se pudo publicar evento de clearing en Pub/Sub", e);
        }
    }

    @PreDestroy
    public void shutdown() throws Exception {
        publisher.shutdown();
        publisher.awaitTermination(10, TimeUnit.SECONDS);
    }
}
