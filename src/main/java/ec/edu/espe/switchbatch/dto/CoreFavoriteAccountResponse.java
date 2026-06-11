package ec.edu.espe.switchbatch.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreFavoriteAccountResponse(
        @JsonAlias({"accountNumber", "account_number", "number", "numeroCuenta", "cuenta"})
        String accountNumber,
        String status,
        Boolean favorite,
        BigDecimal availableBalance,
        BigDecimal currentBalance,
        Map<String, Object> account,
        Map<String, Object> balances) {

    public Optional<String> resolvedAccountNumber() {
        Optional<String> directAccountNumber = asText(accountNumber);
        if (directAccountNumber.isPresent()) {
            return directAccountNumber;
        }
        return findStringValue(account,
                "accountNumber",
                "account_number",
                "number",
                "numeroCuenta",
                "cuenta");
    }

    private Optional<String> findStringValue(Object value, String... keys) {
        if (value instanceof Map<?, ?> map) {
            for (String key : keys) {
                Optional<String> directValue = asText(map.get(key));
                if (directValue.isPresent()) {
                    return directValue;
                }
            }
            for (Object nestedValue : map.values()) {
                Optional<String> found = findStringValue(nestedValue, keys);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> asText(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        if (value instanceof Number number) {
            return Optional.of(number.toString());
        }
        return Optional.empty();
    }
}
