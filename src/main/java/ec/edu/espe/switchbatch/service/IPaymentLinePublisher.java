package ec.edu.espe.switchbatch.service;

import java.time.Instant;
import java.util.List;

import ec.edu.espe.switchbatch.dto.BatchLineMessage;

public interface IPaymentLinePublisher {

    void publish(String batchId, Instant scheduledProcessAt, List<BatchLineMessage> messages);
}
