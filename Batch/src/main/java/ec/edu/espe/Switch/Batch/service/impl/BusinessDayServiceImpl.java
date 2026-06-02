package ec.edu.espe.Switch.Batch.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import ec.edu.espe.Switch.Batch.config.FileReceptionProperties;
import ec.edu.espe.Switch.Batch.service.IBusinessDayService;

@Service
public class BusinessDayServiceImpl implements IBusinessDayService {

    private static final Logger logger = LoggerFactory.getLogger(BusinessDayServiceImpl.class);

    private final FileReceptionProperties properties;
    private final RestClient restClient;

    public BusinessDayServiceImpl(FileReceptionProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getCoreBaseUrl()).build();
    }

    @Override
    public LocalDate nextBusinessDay(LocalDate fromExclusive) {
        LocalDate candidate = fromExclusive.plusDays(1);
        while (!isBusinessDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    @Override
    public boolean isBusinessDay(LocalDate date) {
        if (!properties.isCoreValidationEnabled()) {
            return isWeekday(date);
        }
        try {
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getCoreHolidayEndpoint())
                            .queryParam("date", date)
                            .build())
                    .retrieve()
                    .body(Map.class);
            Object value = response == null ? null : response.get("businessDay");
            if (value == null) {
                value = response == null ? null : response.get("isBusinessDay");
            }
            if (value instanceof Boolean businessDay) {
                return businessDay;
            }
        } catch (RuntimeException e) {
            logger.warn("No se pudo consultar HOLIDAY en Core. Se usara regla lunes-viernes: {}", e.getMessage());
        }
        return isWeekday(date);
    }

    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
