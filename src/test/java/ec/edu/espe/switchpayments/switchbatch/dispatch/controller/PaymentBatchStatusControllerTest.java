package ec.edu.espe.switchpayments.switchbatch.dispatch.controller;

import ec.edu.espe.switchpayments.switchbatch.dispatch.dto.BatchStatusResponse;
import ec.edu.espe.switchpayments.switchbatch.dispatch.model.PaymentBatch;
import ec.edu.espe.switchpayments.switchbatch.dispatch.repository.PaymentDispatchBatchRepository;
import ec.edu.espe.switchpayments.switchbatch.model.PaymentBatchDocument;
import ec.edu.espe.switchpayments.switchbatch.repository.PaymentBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentBatchStatusControllerTest {

    @Mock
    private PaymentDispatchBatchRepository batchRepository;
    @Mock
    private PaymentBatchRepository receivedBatchRepository;

    private PaymentBatchStatusController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentBatchStatusController(batchRepository, receivedBatchRepository);
    }

    @Test
    void getBatchStatusReturnsMappedResponseWhenDispatchBatchExists() {
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchId("batch-1");
        batch.setStatus("COMPLETED");
        when(batchRepository.findByBatchId("batch-1")).thenReturn(Optional.of(batch));

        ResponseEntity<BatchStatusResponse> response = controller.getBatchStatus("batch-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void getBatchStatusFallsBackToReceivedBatchWhenDispatchNotFound() {
        when(batchRepository.findByBatchId("batch-2")).thenReturn(Optional.empty());
        PaymentBatchDocument received = new PaymentBatchDocument();
        received.setId("batch-2");
        received.setStatus("RECEIVED");
        when(receivedBatchRepository.findById("batch-2")).thenReturn(Optional.of(received));

        ResponseEntity<BatchStatusResponse> response = controller.getBatchStatus("batch-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo("RECEIVED");
    }

    @Test
    void getBatchStatusReturnsNotFoundWhenBatchDoesNotExistAnywhere() {
        when(batchRepository.findByBatchId("missing")).thenReturn(Optional.empty());
        when(receivedBatchRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(controller.getBatchStatus("missing").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
