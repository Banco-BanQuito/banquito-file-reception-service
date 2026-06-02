package ec.edu.espe.Switch.Batch.dto;

import java.math.BigDecimal;
import java.util.List;

public record ParsedBatch(
        String clientRuc,
        String serviceType,
        String sourceAccountNumber,
        String fileHash,
        int headerTotalRecords,
        BigDecimal headerTotalAmount,
        int footerTotalRecords,
        BigDecimal footerTotalAmount,
        String securityHash,
        List<ParsedPaymentLine> lines) {

    public int declaredRecords() {
        return headerTotalRecords;
    }

    public BigDecimal declaredAmount() {
        return headerTotalAmount;
    }
}
