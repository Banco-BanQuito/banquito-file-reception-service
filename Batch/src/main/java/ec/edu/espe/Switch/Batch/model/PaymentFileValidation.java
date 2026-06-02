package ec.edu.espe.Switch.Batch.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_file_validation")
public class PaymentFileValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_batch_id", nullable = false)
    private String paymentBatchId;

    @Column(name = "header_total_records", nullable = false)
    private Integer headerTotalRecords;

    @Column(name = "header_total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal headerTotalAmount;

    @Column(name = "footer_total_records", nullable = false)
    private Integer footerTotalRecords;

    @Column(name = "footer_total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal footerTotalAmount;

    @Column(name = "security_hash", nullable = false, length = 128)
    private String securityHash;

    @Column(name = "structure_valid", nullable = false)
    private Boolean structureValid;

    @Column(name = "amount_control_valid", nullable = false)
    private Boolean amountControlValid;

    @Column(name = "customer_service_valid", nullable = false)
    private Boolean customerServiceValid;

    @Column(name = "duplicate_file_valid", nullable = false)
    private Boolean duplicateFileValid;

    @Column(name = "validation_result", nullable = false, length = 30)
    private String validationResult;

    @Column(name = "validated_at", nullable = false)
    private Instant validatedAt;

    public Long getId() {
        return id;
    }

    public String getPaymentBatchId() {
        return paymentBatchId;
    }

    public void setPaymentBatchId(String paymentBatchId) {
        this.paymentBatchId = paymentBatchId;
    }

    public Integer getHeaderTotalRecords() {
        return headerTotalRecords;
    }

    public void setHeaderTotalRecords(Integer headerTotalRecords) {
        this.headerTotalRecords = headerTotalRecords;
    }

    public BigDecimal getHeaderTotalAmount() {
        return headerTotalAmount;
    }

    public void setHeaderTotalAmount(BigDecimal headerTotalAmount) {
        this.headerTotalAmount = headerTotalAmount;
    }

    public Integer getFooterTotalRecords() {
        return footerTotalRecords;
    }

    public void setFooterTotalRecords(Integer footerTotalRecords) {
        this.footerTotalRecords = footerTotalRecords;
    }

    public BigDecimal getFooterTotalAmount() {
        return footerTotalAmount;
    }

    public void setFooterTotalAmount(BigDecimal footerTotalAmount) {
        this.footerTotalAmount = footerTotalAmount;
    }

    public String getSecurityHash() {
        return securityHash;
    }

    public void setSecurityHash(String securityHash) {
        this.securityHash = securityHash;
    }

    public Boolean getStructureValid() {
        return structureValid;
    }

    public void setStructureValid(Boolean structureValid) {
        this.structureValid = structureValid;
    }

    public Boolean getAmountControlValid() {
        return amountControlValid;
    }

    public void setAmountControlValid(Boolean amountControlValid) {
        this.amountControlValid = amountControlValid;
    }

    public Boolean getCustomerServiceValid() {
        return customerServiceValid;
    }

    public void setCustomerServiceValid(Boolean customerServiceValid) {
        this.customerServiceValid = customerServiceValid;
    }

    public Boolean getDuplicateFileValid() {
        return duplicateFileValid;
    }

    public void setDuplicateFileValid(Boolean duplicateFileValid) {
        this.duplicateFileValid = duplicateFileValid;
    }

    public String getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(String validationResult) {
        this.validationResult = validationResult;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }
}
