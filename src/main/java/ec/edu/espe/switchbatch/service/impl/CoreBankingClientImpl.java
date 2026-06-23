package ec.edu.espe.switchbatch.service.impl;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import ec.edu.espe.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchbatch.dto.CoreFavoriteAccountResponse;
import ec.edu.espe.switchbatch.service.ICoreBankingClient;

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
    public boolean isFavoriteAccount(String accountNumber, String customerId) {
        if (!properties.isCoreValidationEnabled()) {
            return accountNumber != null && !accountNumber.isBlank();
        }
        if (accountNumber == null || accountNumber.isBlank() || customerId == null || customerId.isBlank()) {
            return false;
        }
        try {
            CoreFavoriteAccountResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getCoreFavoriteAccountEndpoint())
                            .build(customerId))
                    .retrieve()
                    .body(CoreFavoriteAccountResponse.class);
            if (response == null) {
                logger.warn("Core no retorno cuenta favorita para cliente {}", customerId);
                return false;
            }
            if (Boolean.FALSE.equals(response.favorite())) {
                logger.warn("Core retorno cuenta no favorita para cliente {}", customerId);
                return false;
            }
            var favoriteAccountNumber = response.resolvedAccountNumber();
            if (favoriteAccountNumber.isEmpty()) {
                logger.warn("Core no retorno numero de cuenta favorita para cliente {}", customerId);
                return false;
            }
            return accountNumber.equals(favoriteAccountNumber.get());
        } catch (RuntimeException e) {
            logger.warn("No se pudo consultar cuenta favorita para cliente {} en Core: {}", customerId, e.getMessage());
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

    // No se gatea con coreValidationEnabled: ese flag desactiva validaciones de
    // party-service (cuenta favorita, servicio activo) para entornos de prueba,
    // pero el saldo es real en account-core y debitarlo de más rompe la dispersión.
    @Override
    public boolean hasSufficientBalance(String accountNumber, BigDecimal requiredAmount) {
        if (accountNumber == null || accountNumber.isBlank() || requiredAmount == null) {
            return false;
        }
        try {
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getCoreBalanceEndpoint())
                            .build(accountNumber))
                    .retrieve()
                    .body(Map.class);
            Object value = response == null ? null : response.get("availableBalance");
            if (value == null) {
                logger.warn("Core no retorno availableBalance para cuenta {}", accountNumber);
                return false;
            }
            BigDecimal availableBalance = new BigDecimal(value.toString());
            return availableBalance.compareTo(requiredAmount) >= 0;
        } catch (RuntimeException e) {
            logger.warn("No se pudo consultar saldo de cuenta {} en Core: {}", accountNumber, e.getMessage());
            return false;
        }
    }

}
