package ec.edu.espe.Switch.Batch.service;

import java.time.Instant;
import java.util.List;

import ec.edu.espe.Switch.Batch.dto.BatchLineMessage;

public interface IPaymentLinePublisher {

    void publish(String batchId, Instant scheduledProcessAt, List<BatchLineMessage> messages);
}
