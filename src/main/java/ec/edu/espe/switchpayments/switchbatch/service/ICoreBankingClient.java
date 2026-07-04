package ec.edu.espe.switchpayments.switchbatch.service;

import java.math.BigDecimal;

public interface ICoreBankingClient {

    boolean isAccountValid(String accountNumber, String clientRuc);

    boolean isFavoriteAccount(String accountNumber, String customerId);

    boolean hasActiveMassPaymentService(String clientRuc, String serviceType);

    boolean hasSufficientBalance(String accountNumber, BigDecimal requiredAmount);
}
