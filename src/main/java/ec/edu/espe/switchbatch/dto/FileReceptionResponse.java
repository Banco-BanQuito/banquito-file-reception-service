package ec.edu.espe.switchbatch.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FileReceptionResponse(
        String batchId,
        String status,
        String message,
        Instant receivedAt,
        int declaredRecords,
        BigDecimal declaredAmount) {
}
