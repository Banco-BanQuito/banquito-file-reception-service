package ec.edu.espe.switchpayments.switchbatch.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BatchStatusResponse(
        String batchId,
        String status,
        int declaredTotalRecords,
        int successfulRecords,
        int rejectedRecords,
        int inProcessRecords,
        BigDecimal successfulAmount,
        BigDecimal rejectedAmount,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        String message,
        String failureReason) {
}
