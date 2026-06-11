package ec.edu.espe.switchbatch.dto;

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
        @JsonProperty("originating_account")
        String originatingAccount,
        @JsonProperty("declared_total_records")
        int declaredTotalRecords,
        BigDecimal amount,
        String reference,
        @JsonProperty("beneficiary_name")
        String beneficiaryName,
        @JsonProperty("beneficiary_email")
        String beneficiaryEmail) {
}
