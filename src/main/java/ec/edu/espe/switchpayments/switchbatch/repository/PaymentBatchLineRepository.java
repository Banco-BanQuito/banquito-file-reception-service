package ec.edu.espe.switchpayments.switchbatch.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchLineDocument;

public interface PaymentBatchLineRepository extends MongoRepository<PaymentBatchLineDocument, String> {

    List<PaymentBatchLineDocument> findByBatchIdOrderByLineNumberAsc(String batchId);

    long countByBatchId(String batchId);
}
