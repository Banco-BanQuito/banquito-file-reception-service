package ec.edu.espe.Switch.Batch.event;

import java.time.Instant;
import java.util.List;

import ec.edu.espe.Switch.Batch.dto.BatchLineMessage;

public record PaymentLinesReadyEvent(
        String batchId,
        Instant scheduledProcessAt,
        List<BatchLineMessage> messages) {
}
