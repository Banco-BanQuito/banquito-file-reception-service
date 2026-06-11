package ec.edu.espe.switchbatch.service;

public interface ICoreBankingClient {

    boolean isAccountValid(String accountNumber, String clientRuc);

    boolean isFavoriteAccount(String accountNumber, String customerId);

    boolean hasActiveMassPaymentService(String clientRuc, String serviceType);
}
