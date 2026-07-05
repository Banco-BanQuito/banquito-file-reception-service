package ec.edu.espe.switchpayments.switchbatch.dispatch.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "payment_dispatch_batch")
public class PaymentBatch {

    @Id
    private String id;

    @Indexed(unique = true)
    private String batchId;

    private String status;

    private String originatingAccount;

    private int declaredTotalRecords;
    private int successfulRecords;
    private int rejectedRecords;
    private BigDecimal successfulAmount = BigDecimal.ZERO;
    private BigDecimal rejectedAmount = BigDecimal.ZERO;
    private BigDecimal declaredTotalAmount = BigDecimal.ZERO;
    private BigDecimal refundAmount = BigDecimal.ZERO;
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getDeclaredTotalRecords() { return declaredTotalRecords; }
    public void setDeclaredTotalRecords(int declaredTotalRecords) { this.declaredTotalRecords = declaredTotalRecords; }

    public int getSuccessfulRecords() { return successfulRecords; }
    public void setSuccessfulRecords(int successfulRecords) { this.successfulRecords = successfulRecords; }

    public int getRejectedRecords() { return rejectedRecords; }
    public void setRejectedRecords(int rejectedRecords) { this.rejectedRecords = rejectedRecords; }

    public BigDecimal getSuccessfulAmount() { return successfulAmount; }
    public void setSuccessfulAmount(BigDecimal successfulAmount) { this.successfulAmount = successfulAmount; }

    public BigDecimal getRejectedAmount() { return rejectedAmount; }
    public void setRejectedAmount(BigDecimal rejectedAmount) { this.rejectedAmount = rejectedAmount; }

    public BigDecimal getDeclaredTotalAmount() { return declaredTotalAmount; }
    public void setDeclaredTotalAmount(BigDecimal declaredTotalAmount) { this.declaredTotalAmount = declaredTotalAmount; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getOriginatingAccount() { return originatingAccount; }
    public void setOriginatingAccount(String originatingAccount) { this.originatingAccount = originatingAccount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
