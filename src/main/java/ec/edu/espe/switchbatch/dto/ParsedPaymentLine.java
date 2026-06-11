package ec.edu.espe.switchbatch.dto;

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
