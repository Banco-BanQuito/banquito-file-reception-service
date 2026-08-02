package ec.edu.espe.switchpayments.switchbatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-reception")
public class FileReceptionProperties {

    private int duplicateWindowDays = 30;
    private String fileDelimiter = ",";
    private boolean embeddedRouterEnabled = true;
    private int cutoffHour = 18;
    private String coreBaseUrl = "http://localhost:8080";
    private String coreHolidayEndpoint = "/api/v2/calendar/holidays/check";
    private String coreAccountValidationEndpoint = "/api/v1/accounts/validate";
    private String coreFavoriteAccountEndpoint = "/api/v2/accounts/customer/{customerId}/favorite";
    private String coreMassPaymentServiceEndpoint = "/api/v1/customers/mass-payments/active";
    private String coreBalanceEndpoint = "/api/v2/accounts/{accountNumber}/balance";
    private boolean coreValidationEnabled = true;
    private boolean forceBusinessDay = false;
    private String corporateAccountNumber = "0000000000";

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

    public boolean isEmbeddedRouterEnabled() {
        return embeddedRouterEnabled;
    }

    public void setEmbeddedRouterEnabled(boolean embeddedRouterEnabled) {
        this.embeddedRouterEnabled = embeddedRouterEnabled;
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

    public String getCorporateAccountNumber() {
        return corporateAccountNumber;
    }

    public void setCorporateAccountNumber(String corporateAccountNumber) {
        this.corporateAccountNumber = corporateAccountNumber;
    }

}

