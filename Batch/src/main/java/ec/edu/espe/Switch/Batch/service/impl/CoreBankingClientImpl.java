package ec.edu.espe.Switch.Batch.service.impl;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import ec.edu.espe.Switch.Batch.config.FileReceptionProperties;
import ec.edu.espe.Switch.Batch.service.ICoreBankingClient;

@Service
public class CoreBankingClientImpl implements ICoreBankingClient {

    private static final Logger logger = LoggerFactory.getLogger(CoreBankingClientImpl.class);

    private final FileReceptionProperties properties;
    private final RestClient restClient;

    public CoreBankingClientImpl(FileReceptionProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getCoreBaseUrl()).build();
    }

    @Override
    public boolean isAccountValid(String accountNumber, String clientRuc) {
        if (!properties.isCoreValidationEnabled()) {
            return accountNumber != null && !accountNumber.isBlank();
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getCoreAccountValidationEndpoint())
                            .queryParam("accountNumber", accountNumber)
                            .queryParam("clientRuc", clientRuc)
                            .build())
                    .retrieve()
                    .body(Map.class);
            Object value = response == null ? null : response.get("valid");
            if (value == null) {
                value = response == null ? null : response.get("isValid");
            }
            if (value instanceof Boolean valid) {
                return valid;
            }
            logger.warn("Core no retorno bandera valid/isValid para cuenta {}", accountNumber);
            return false;
        } catch (RuntimeException e) {
            logger.warn("No se pudo validar cuenta {} en Core: {}", accountNumber, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasActiveMassPaymentService(String clientRuc, String serviceType) {
        if (!properties.isCoreValidationEnabled()) {
            return clientRuc != null && !clientRuc.isBlank();
        }
        if (clientRuc == null || clientRuc.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getCoreMassPaymentServiceEndpoint())
                            .queryParam("clientRuc", clientRuc)
                            .queryParam("serviceType", serviceType)
                            .build())
                    .retrieve()
                    .body(Map.class);
            Object value = response == null ? null : response.get("active");
            if (value == null) {
                value = response == null ? null : response.get("isActive");
            }
            if (value instanceof Boolean active) {
                return active;
            }
            logger.warn("Core no retorno bandera active/isActive para servicio de pagos masivos del RUC {}", clientRuc);
            return false;
        } catch (RuntimeException e) {
            logger.warn("No se pudo validar servicio de pagos masivos para RUC {} en Core: {}", clientRuc, e.getMessage());
            return false;
        }
    }
}
