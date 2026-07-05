package ec.edu.espe.switchpayments.switchbatch.dispatch.repository;

import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentBatch;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PaymentDispatchBatchRepository extends MongoRepository<PaymentBatch, String> {
    Optional<PaymentBatch> findByBatchId(String batchId);
}
