package ec.edu.espe.Switch.Batch.dto;

import java.math.BigDecimal;

public record ParsedPaymentLine(
        int lineNumber,
        String routingCode,
        String beneficiaryIdentification,
        String beneficiaryName,
        String destinationAccountNumber,
        BigDecimal amount,
        String reference,
        String beneficiaryEmail) {
}
