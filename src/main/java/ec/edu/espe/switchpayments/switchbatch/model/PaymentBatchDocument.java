package ec.edu.espe.switchpayments.switchbatch.model;

import java.time.Instant;
import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "file_payment_batch")
public class PaymentBatchDocument {

    @Id
    private String id;

    @Field("file_name")
    private String fileName;

    @Indexed
    @Field("file_hash")
    private String fileHash;

    @Field("client_ruc")
    private String clientRuc;

    @Field("source_account_number")
    private String sourceAccountNumber;

    @Indexed
    @Field("received_at")
    private Instant receivedAt;

    @Field("scheduled_process_at")
    private Instant scheduledProcessAt;

    @Field("declared_total_records")
    private Integer declaredTotalRecords;

    @Field("declared_total_amount")
    private BigDecimal declaredTotalAmount;

    private String status;
    private String channel;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getClientRuc() {
        return clientRuc;
    }

    public void setClientRuc(String clientRuc) {
        this.clientRuc = clientRuc;
    }

    public String getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public void setSourceAccountNumber(String sourceAccountNumber) {
        this.sourceAccountNumber = sourceAccountNumber;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getScheduledProcessAt() {
        return scheduledProcessAt;
    }

    public void setScheduledProcessAt(Instant scheduledProcessAt) {
        this.scheduledProcessAt = scheduledProcessAt;
    }

    public Integer getDeclaredTotalRecords() {
        return declaredTotalRecords;
    }

    public void setDeclaredTotalRecords(Integer declaredTotalRecords) {
        this.declaredTotalRecords = declaredTotalRecords;
    }

    public BigDecimal getDeclaredTotalAmount() {
        return declaredTotalAmount;
    }

    public void setDeclaredTotalAmount(BigDecimal declaredTotalAmount) {
        this.declaredTotalAmount = declaredTotalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
