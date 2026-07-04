package ec.edu.espe.switchpayments.switchbatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-reception")
public class FileReceptionProperties {

    private int duplicateWindowDays = 30;
    private String fileDelimiter = ",";
    private PaymentLineTransport paymentLineTransport = PaymentLineTransport.RABBITMQ;
    private boolean rabbitEnabled = false;
    private String rabbitExchange = "payment.exchange";
    private String rabbitQueueOnUs = "payment.lines.onus.queue";
    private String rabbitQueueOffUs = "payment.lines.offus.queue";
    private String rabbitQueueInvalid = "payment.lines.invalid.queue";
    private String rabbitRoutingKeyOnUs = "onus";
    private String rabbitRoutingKeyOffUs = "offus";
    private String rabbitRoutingKeyInvalid = "invalid";
    private String grpcHost = "localhost";
    private int grpcPort = 9090;
    private long grpcDeadlineSeconds = 10;
    private int cutoffHour = 18;
    private String coreBaseUrl = "http://localhost:8080";
    private String coreHolidayEndpoint = "/api/v2/calendar/holidays/check";
    private String coreAccountValidationEndpoint = "/api/v1/accounts/validate";
    private String coreFavoriteAccountEndpoint = "/api/v2/accounts/customer/{customerId}/favorite";
    private String coreMassPaymentServiceEndpoint = "/api/v1/customers/mass-payments/active";
    private String coreBalanceEndpoint = "/api/v2/accounts/{accountNumber}/balance";
    private boolean coreValidationEnabled = true;
    private boolean forceBusinessDay = false;

    public int getDuplicateWindowDays() {
        return duplicateWindowDays;
    }

    public void setDuplicateWindowDays(int duplicateWindowDays) {
        this.duplicateWindowDays = duplicateWindowDays;
    }

    public String getFileDelimiter() {
        return fileDelimiter;
    }

    public void setFileDelimiter(String fileDelimiter) {
        this.fileDelimiter = fileDelimiter;
    }

    public PaymentLineTransport getPaymentLineTransport() {
        return paymentLineTransport;
    }

    public void setPaymentLineTransport(PaymentLineTransport paymentLineTransport) {
        this.paymentLineTransport = paymentLineTransport;
    }

    public boolean isRabbitEnabled() {
        return rabbitEnabled;
    }

    public void setRabbitEnabled(boolean rabbitEnabled) {
        this.rabbitEnabled = rabbitEnabled;
    }

    public String getRabbitExchange() {
        return rabbitExchange;
    }

    public void setRabbitExchange(String rabbitExchange) {
        this.rabbitExchange = rabbitExchange;
    }

    public String getRabbitQueueOnUs() {
        return rabbitQueueOnUs;
    }

    public void setRabbitQueueOnUs(String rabbitQueueOnUs) {
        this.rabbitQueueOnUs = rabbitQueueOnUs;
    }

    public String getRabbitQueueOffUs() {
        return rabbitQueueOffUs;
    }

    public void setRabbitQueueOffUs(String rabbitQueueOffUs) {
        this.rabbitQueueOffUs = rabbitQueueOffUs;
    }

    public String getRabbitQueueInvalid() {
        return rabbitQueueInvalid;
    }

    public void setRabbitQueueInvalid(String rabbitQueueInvalid) {
        this.rabbitQueueInvalid = rabbitQueueInvalid;
    }

    public String getRabbitRoutingKeyOnUs() {
        return rabbitRoutingKeyOnUs;
    }

    public void setRabbitRoutingKeyOnUs(String rabbitRoutingKeyOnUs) {
        this.rabbitRoutingKeyOnUs = rabbitRoutingKeyOnUs;
    }

    public String getRabbitRoutingKeyOffUs() {
        return rabbitRoutingKeyOffUs;
    }

    public void setRabbitRoutingKeyOffUs(String rabbitRoutingKeyOffUs) {
        this.rabbitRoutingKeyOffUs = rabbitRoutingKeyOffUs;
    }

    public String getRabbitRoutingKeyInvalid() {
        return rabbitRoutingKeyInvalid;
    }

    public void setRabbitRoutingKeyInvalid(String rabbitRoutingKeyInvalid) {
        this.rabbitRoutingKeyInvalid = rabbitRoutingKeyInvalid;
    }

    public String getGrpcHost() {
        return grpcHost;
    }

    public void setGrpcHost(String grpcHost) {
        this.grpcHost = grpcHost;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public void setGrpcPort(int grpcPort) {
        this.grpcPort = grpcPort;
    }

    public long getGrpcDeadlineSeconds() {
        return grpcDeadlineSeconds;
    }

    public void setGrpcDeadlineSeconds(long grpcDeadlineSeconds) {
        this.grpcDeadlineSeconds = grpcDeadlineSeconds;
    }

    public int getCutoffHour() {
        return cutoffHour;
    }

    public void setCutoffHour(int cutoffHour) {
        this.cutoffHour = cutoffHour;
    }

    public String getCoreBaseUrl() {
        return coreBaseUrl;
    }

    public void setCoreBaseUrl(String coreBaseUrl) {
        this.coreBaseUrl = coreBaseUrl;
    }

    public String getCoreHolidayEndpoint() {
        return coreHolidayEndpoint;
    }

    public void setCoreHolidayEndpoint(String coreHolidayEndpoint) {
        this.coreHolidayEndpoint = coreHolidayEndpoint;
    }

    public String getCoreAccountValidationEndpoint() {
        return coreAccountValidationEndpoint;
    }

    public void setCoreAccountValidationEndpoint(String coreAccountValidationEndpoint) {
        this.coreAccountValidationEndpoint = coreAccountValidationEndpoint;
    }

    public String getCoreFavoriteAccountEndpoint() {
        return coreFavoriteAccountEndpoint;
    }

    public void setCoreFavoriteAccountEndpoint(String coreFavoriteAccountEndpoint) {
        this.coreFavoriteAccountEndpoint = coreFavoriteAccountEndpoint;
    }

    public String getCoreMassPaymentServiceEndpoint() {
        return coreMassPaymentServiceEndpoint;
    }

    public void setCoreMassPaymentServiceEndpoint(String coreMassPaymentServiceEndpoint) {
        this.coreMassPaymentServiceEndpoint = coreMassPaymentServiceEndpoint;
    }

    public String getCoreBalanceEndpoint() {
        return coreBalanceEndpoint;
    }

    public void setCoreBalanceEndpoint(String coreBalanceEndpoint) {
        this.coreBalanceEndpoint = coreBalanceEndpoint;
    }

    public boolean isCoreValidationEnabled() {
        return coreValidationEnabled;
    }

    public void setCoreValidationEnabled(boolean coreValidationEnabled) {
        this.coreValidationEnabled = coreValidationEnabled;
    }

    public boolean isForceBusinessDay() {
        return forceBusinessDay;
    }

    public void setForceBusinessDay(boolean forceBusinessDay) {
        this.forceBusinessDay = forceBusinessDay;
    }
}
