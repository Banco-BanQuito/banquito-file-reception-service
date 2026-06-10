package ec.edu.espe.Switch.Batch.repository;

import java.time.Instant;
import java.util.Collection;

import org.springframework.data.mongodb.repository.MongoRepository;

import ec.edu.espe.Switch.Batch.model.PaymentBatchDocument;

public interface PaymentBatchRepository extends MongoRepository<PaymentBatchDocument, String> {

    boolean existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(String fileName,
                                                                     String fileHash,
                                                                     Collection<String> statuses,
                                                                     Instant receivedAt);
}
