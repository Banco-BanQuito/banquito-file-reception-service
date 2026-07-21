package ec.edu.espe.switchpayments.switchbatch.dispatch.controller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import ec.edu.espe.switchpayments.switchbatch.dispatch.dto.BatchStatusResponse;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentBatch;
import ec.edu.espe.switchpayments.switchbatch.dispatch.repository.PaymentDispatchBatchRepository;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/payments/batches")
public class PaymentBatchStatusController {

    private final PaymentDispatchBatchRepository batchRepository;
    private final PaymentBatchRepository receivedBatchRepository;

    public PaymentBatchStatusController(PaymentDispatchBatchRepository batchRepository,
                                        PaymentBatchRepository receivedBatchRepository) {
        this.batchRepository = batchRepository;
        this.receivedBatchRepository = receivedBatchRepository;
    }

    @GetMapping("/{batchId}/status")
    public ResponseEntity<BatchStatusResponse> getBatchStatus(@PathVariable String batchId) {
        return batchRepository.findByBatchId(batchId)
                .map(this::toResponse)
                .or(() -> receivedBatchRepository.findById(batchId).map(this::toInitialResponse))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private BatchStatusResponse toResponse(PaymentBatch batch) {
        BatchStatusResponse r = new BatchStatusResponse();
        r.setBatchId(batch.getBatchId());
        r.setStatus(batch.getStatus());
        r.setDeclaredTotalRecords(batch.getDeclaredTotalRecords());
        r.setSuccessfulRecords(batch.getSuccessfulRecords());
        r.setRejectedRecords(batch.getRejectedRecords());
        r.setSuccessfulAmount(batch.getSuccessfulAmount());
        r.setRejectedAmount(batch.getRejectedAmount());
        r.setCreatedAt(batch.getCreatedAt());
        r.setUpdatedAt(batch.getUpdatedAt());
        r.setCompletedAt(batch.getCompletedAt());
        r.setFailureReason(batch.getFailureReason());
        return r;
    }

    private BatchStatusResponse toInitialResponse(PaymentBatchDocument batch) {
        BatchStatusResponse r = new BatchStatusResponse();
        r.setBatchId(batch.getId());
        r.setStatus(batch.getStatus());
        if (batch.getReceivedAt() != null) {
            r.setCreatedAt(LocalDateTime.ofInstant(batch.getReceivedAt(), ZoneOffset.UTC));
        }
        return r;
    }
}
