package ec.edu.espe.Switch.Batch.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BatchLineMessage(
        @JsonProperty("batch_id")
        String batchId,
        @JsonProperty("line_number")
        int lineNumber,
        @JsonProperty("routing_code")
        String routingCode,
        @JsonProperty("account_destination")
        String accountDestination,
        BigDecimal amount,
        String reference,
        @JsonProperty("beneficiary_name")
        String beneficiaryName,
        @JsonProperty("beneficiary_email")
        String beneficiaryEmail) {
}
