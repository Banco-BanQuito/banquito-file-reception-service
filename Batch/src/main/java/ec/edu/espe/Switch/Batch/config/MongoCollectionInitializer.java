package ec.edu.espe.Switch.Batch.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import ec.edu.espe.Switch.Batch.model.PaymentBatchDocument;

@Component
public class MongoCollectionInitializer implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    public MongoCollectionInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        if (!mongoTemplate.collectionExists(PaymentBatchDocument.class)) {
            mongoTemplate.createCollection(PaymentBatchDocument.class);
        }
    }
}
