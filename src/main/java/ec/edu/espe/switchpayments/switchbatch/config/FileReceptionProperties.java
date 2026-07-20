package ec.edu.espe.switchpayments.switchbatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-reception")
public class FileReceptionProperties {

    private int duplicateWindowDays = 30;
    private String fileDelimiter = ",";
    private boolean rabbitEnabled = false;
    private String rabbitExchange = "payment.exchange";
    private String rabbitQueueOnUs = "payment.lines.onus.queue";
    private String rabbitQueueOffUs = "payment.lines.offus.queue";
    private String rabbitQueueInvalid = "payment.lines.invalid.queue";
    private String rabbitRoutingKeyOnUs = "onus";
    private String rabbitRoutingKeyOffUs = "offus";
    private String rabbitRoutingKeyInvalid = "invalid";
    private long grpcDeadlineSeconds = 10;
    private String tariffGrpcHost = "localhost";
    private int tariffGrpcPort = 9090;
    private String notificationGrpcHost = "localhost";
    private int notificationGrpcPort = 9092;
    private int cutoffHour = 18;
    private String coreBaseUrl = "http://localhost:8080";
    private String coreHolidayEndpoint = "/api/v2/calendar/holidays/check";
    private String coreAccountValidationEndpoint = "/api/v1/accounts/validate";
    private String coreFavoriteAccountEndpoint = "/api/v2/accounts/customer/{customerId}/favorite";
    private String coreMassPaymentServiceEndpoint = "/api/v1/customers/mass-payments/active";
    private String coreBalanceEndpoint = "/api/v2/accounts/{accountNumber}/balance";
    private String coreBatchCreditEndpoint = "/api/v2/payments/batch-credit";
    private String coreCorporateDebitEndpoint = "/api/v2/payments/corporate-debit";
    private String coreCorporateRefundEndpoint = "/api/v2/payments/corporate-refund";
    private boolean coreValidationEnabled = true;
    private boolean forceBusinessDay = false;
    private String corporateAccountNumber = "0000000000";
    private boolean dispatchLocalCompletionEnabled = false;
    private String pubsubProjectId = "project-47695a8e-7cb2-4352-af2";
    private String pubsubPaymentLinesTopic = "banquito-payment-lines";
    private String pubsubClearingEventsTopic = "banquito-clearing-events";
    private String pubsubPaymentLinesOnUsSubscription = "payment-lines-onus-sub";
    private String pubsubPaymentLinesOffUsSubscription = "payment-lines-offus-sub";
    private String pubsubPaymentLinesInvalidSubscription = "payment-lines-invalid-sub";
    private String pubsubRoutingKeyOnUs = "onus";
    private String pubsubRoutingKeyOffUs = "offus";
    private String pubsubRoutingKeyInvalid = "invalid";
    private String pubsubRoutingKeyClearingOutbound = "clearing.outbound";

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

    public String getTariffGrpcHost() {
        return tariffGrpcHost;
    }

    public void setTariffGrpcHost(String tariffGrpcHost) {
        this.tariffGrpcHost = tariffGrpcHost;
    }

    public int getTariffGrpcPort() {
        return tariffGrpcPort;
    }

    public void setTariffGrpcPort(int tariffGrpcPort) {
        this.tariffGrpcPort = tariffGrpcPort;
    }

    public String getNotificationGrpcHost() {
        return notificationGrpcHost;
    }

    public void setNotificationGrpcHost(String notificationGrpcHost) {
        this.notificationGrpcHost = notificationGrpcHost;
    }

    public int getNotificationGrpcPort() {
        return notificationGrpcPort;
    }

    public void setNotificationGrpcPort(int notificationGrpcPort) {
        this.notificationGrpcPort = notificationGrpcPort;
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

    public String getCoreBatchCreditEndpoint() {
        return coreBatchCreditEndpoint;
    }

    public void setCoreBatchCreditEndpoint(String coreBatchCreditEndpoint) {
        this.coreBatchCreditEndpoint = coreBatchCreditEndpoint;
    }

    public String getCoreCorporateDebitEndpoint() {
        return coreCorporateDebitEndpoint;
    }

    public void setCoreCorporateDebitEndpoint(String coreCorporateDebitEndpoint) {
        this.coreCorporateDebitEndpoint = coreCorporateDebitEndpoint;
    }

    public String getCoreCorporateRefundEndpoint() {
        return coreCorporateRefundEndpoint;
    }

    public void setCoreCorporateRefundEndpoint(String coreCorporateRefundEndpoint) {
        this.coreCorporateRefundEndpoint = coreCorporateRefundEndpoint;
    }

    public String getCorporateAccountNumber() {
        return corporateAccountNumber;
    }

    public void setCorporateAccountNumber(String corporateAccountNumber) {
        this.corporateAccountNumber = corporateAccountNumber;
    }

    public boolean isDispatchLocalCompletionEnabled() {
        return dispatchLocalCompletionEnabled;
    }

    public void setDispatchLocalCompletionEnabled(boolean dispatchLocalCompletionEnabled) {
        this.dispatchLocalCompletionEnabled = dispatchLocalCompletionEnabled;
    }

    public String getPubsubProjectId() {
        return pubsubProjectId;
    }

    public void setPubsubProjectId(String pubsubProjectId) {
        this.pubsubProjectId = pubsubProjectId;
    }

    public String getPubsubPaymentLinesTopic() {
        return pubsubPaymentLinesTopic;
    }

    public void setPubsubPaymentLinesTopic(String pubsubPaymentLinesTopic) {
        this.pubsubPaymentLinesTopic = pubsubPaymentLinesTopic;
    }

    public String getPubsubClearingEventsTopic() {
        return pubsubClearingEventsTopic;
    }

    public void setPubsubClearingEventsTopic(String pubsubClearingEventsTopic) {
        this.pubsubClearingEventsTopic = pubsubClearingEventsTopic;
    }

    public String getPubsubPaymentLinesOnUsSubscription() {
        return pubsubPaymentLinesOnUsSubscription;
    }

    public void setPubsubPaymentLinesOnUsSubscription(String pubsubPaymentLinesOnUsSubscription) {
        this.pubsubPaymentLinesOnUsSubscription = pubsubPaymentLinesOnUsSubscription;
    }

    public String getPubsubPaymentLinesOffUsSubscription() {
        return pubsubPaymentLinesOffUsSubscription;
    }

    public void setPubsubPaymentLinesOffUsSubscription(String pubsubPaymentLinesOffUsSubscription) {
        this.pubsubPaymentLinesOffUsSubscription = pubsubPaymentLinesOffUsSubscription;
    }

    public String getPubsubPaymentLinesInvalidSubscription() {
        return pubsubPaymentLinesInvalidSubscription;
    }

    public void setPubsubPaymentLinesInvalidSubscription(String pubsubPaymentLinesInvalidSubscription) {
        this.pubsubPaymentLinesInvalidSubscription = pubsubPaymentLinesInvalidSubscription;
    }

    public String getPubsubRoutingKeyOnUs() {
        return pubsubRoutingKeyOnUs;
    }

    public void setPubsubRoutingKeyOnUs(String pubsubRoutingKeyOnUs) {
        this.pubsubRoutingKeyOnUs = pubsubRoutingKeyOnUs;
    }

    public String getPubsubRoutingKeyOffUs() {
        return pubsubRoutingKeyOffUs;
    }

    public void setPubsubRoutingKeyOffUs(String pubsubRoutingKeyOffUs) {
        this.pubsubRoutingKeyOffUs = pubsubRoutingKeyOffUs;
    }

    public String getPubsubRoutingKeyInvalid() {
        return pubsubRoutingKeyInvalid;
    }

    public void setPubsubRoutingKeyInvalid(String pubsubRoutingKeyInvalid) {
        this.pubsubRoutingKeyInvalid = pubsubRoutingKeyInvalid;
    }

    public String getPubsubRoutingKeyClearingOutbound() {
        return pubsubRoutingKeyClearingOutbound;
    }

    public void setPubsubRoutingKeyClearingOutbound(String pubsubRoutingKeyClearingOutbound) {
        this.pubsubRoutingKeyClearingOutbound = pubsubRoutingKeyClearingOutbound;
    }
}
