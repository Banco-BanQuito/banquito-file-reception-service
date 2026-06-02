package ec.edu.espe.Switch.Batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-reception")
public class FileReceptionProperties {

    private int duplicateWindowDays = 30;
    private String fileDelimiter = ",";
    private boolean rabbitEnabled = false;
    private String rabbitQueue = "payment.lines.queue";
    private int cutoffHour = 18;
    private String coreBaseUrl = "http://localhost:8080";
    private String coreHolidayEndpoint = "/api/v1/holidays/is-business-day";
    private String coreAccountValidationEndpoint = "/api/v1/accounts/validate";
    private String coreMassPaymentServiceEndpoint = "/api/v1/customers/mass-payments/active";
    private boolean coreValidationEnabled = true;

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

    public String getRabbitQueue() {
        return rabbitQueue;
    }

    public void setRabbitQueue(String rabbitQueue) {
        this.rabbitQueue = rabbitQueue;
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

    public String getCoreMassPaymentServiceEndpoint() {
        return coreMassPaymentServiceEndpoint;
    }

    public void setCoreMassPaymentServiceEndpoint(String coreMassPaymentServiceEndpoint) {
        this.coreMassPaymentServiceEndpoint = coreMassPaymentServiceEndpoint;
    }

    public boolean isCoreValidationEnabled() {
        return coreValidationEnabled;
    }

    public void setCoreValidationEnabled(boolean coreValidationEnabled) {
        this.coreValidationEnabled = coreValidationEnabled;
    }
}
