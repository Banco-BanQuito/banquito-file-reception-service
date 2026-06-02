package ec.edu.espe.Switch.Batch.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.MongoRepository;

import ec.edu.espe.Switch.Batch.model.PaymentBatchDocument;

public interface PaymentBatchRepository extends MongoRepository<PaymentBatchDocument, String> {

    boolean existsByFileHashAndReceivedAtAfter(String fileHash, Instant receivedAt);
}
