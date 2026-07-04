package ec.edu.espe.switchpayments.switchbatch.event;

import ec.edu.espe.switchpayments.switchbatch.dto.ParsedBatch;
import java.time.Instant;

public record PaymentLinesReadyEvent(
        String batchId,
        Instant scheduledProcessAt,
        ParsedBatch batch,
        boolean duplicateValid) {
}