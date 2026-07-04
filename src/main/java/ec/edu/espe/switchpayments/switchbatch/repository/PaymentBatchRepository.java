package ec.edu.espe.switchpayments.switchbatch.repository;

import java.time.Instant;
import java.util.Collection;

import org.springframework.data.mongodb.repository.MongoRepository;

import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;

public interface PaymentBatchRepository extends MongoRepository<PaymentBatchDocument, String> {

    boolean existsByFileNameAndFileHashAndStatusInAndReceivedAtAfter(String fileName,
                                                                     String fileHash,
                                                                     Collection<String> statuses,
                                                                     Instant receivedAt);
}
