package ec.edu.espe.switchpayments.switchbatch.dispatch.repository;

import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentDetail;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentDetailRepository extends MongoRepository<PaymentDetail, String> {
    boolean existsByBatchIdAndLineNumber(String batchId, int lineNumber);
}
