package ec.edu.espe.switchpayments.switchbatch.dispatch.controller;

import ec.edu.espe.switchpayments.switchbatch.dispatch.dto.BatchStatusResponse;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentBatch;
import ec.edu.espe.switchpayments.switchbatch.dispatch.repository.PaymentDispatchBatchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/v1/payments/batches", "/api/v2/payments/batches"})
public class BatchStatusController {

    private final PaymentDispatchBatchRepository repository;

    public BatchStatusController(PaymentDispatchBatchRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{batchId}/status")
    public BatchStatusResponse getStatus(@PathVariable String batchId) {
        PaymentBatch batch = repository.findByBatchId(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote no encontrado: " + batchId));

        BatchStatusResponse response = new BatchStatusResponse();
        response.setBatchId(batch.getBatchId());
        response.setStatus(batch.getStatus());
        response.setDeclaredTotalRecords(batch.getDeclaredTotalRecords());
        response.setSuccessfulRecords(batch.getSuccessfulRecords());
        response.setRejectedRecords(batch.getRejectedRecords());
        response.setSuccessfulAmount(batch.getSuccessfulAmount());
        response.setRejectedAmount(batch.getRejectedAmount());
        response.setCreatedAt(batch.getCreatedAt());
        response.setUpdatedAt(batch.getUpdatedAt());
        response.setCompletedAt(batch.getCompletedAt());
        response.setFailureReason(batch.getFailureReason());
        return response;
    }
}
