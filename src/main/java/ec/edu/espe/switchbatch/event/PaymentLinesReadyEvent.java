package ec.edu.espe.switchbatch.event;

import java.time.Instant;
import java.util.List;

import ec.edu.espe.switchbatch.dto.BatchLineMessage;

public record PaymentLinesReadyEvent(
        String batchId,
        Instant scheduledProcessAt,
        List<BatchLineMessage> messages) {
}
