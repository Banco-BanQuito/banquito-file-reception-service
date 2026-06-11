package ec.edu.espe.switchbatch.dto;

import java.time.LocalDate;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreHolidayResponse(
        LocalDate date,
        Boolean holiday,
        String name,
        Boolean weekend,
        @JsonAlias("isBusinessDay")
        Boolean businessDay) {

    public Optional<Boolean> resolveBusinessDay() {
        if (holiday != null && weekend != null) {
            return Optional.of(!holiday && !weekend);
        }
        return Optional.ofNullable(businessDay);
    }
}
