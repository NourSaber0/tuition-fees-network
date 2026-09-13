package com.tuitionnetwork.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "upload_row")
public class UploadRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "csv_upload_id", nullable = false)
    private CsvUpload csvUpload;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "student_ref")
    private String studentRef;

    @Column(name = "fee_name")
    private String feeName;

    @Column(name = "category")
    private String category;

    @Column(name = "amount_egp", precision = 19, scale = 2)
    private BigDecimal amountEGP;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "term")
    private String term;

    @Column(name = "status", nullable = false)
    private String status; // Accepted, Rejected, Valid, Invalid

    @Column(name = "error_reason", length = 1000)
    private String errorReason;

    @Column(name = "raw_row_data", length = 2000)
    private String rawRowData;

    @Column(name = "fee_line_id")
    private UUID feeLineId;

    public UploadRow() {
    }

    public UploadRow(CsvUpload csvUpload, int rowNumber, String studentRef, String feeName,
                     String category, BigDecimal amountEGP, LocalDate dueDate, String term,
                     String status, String errorReason, String rawRowData) {
        this(csvUpload, rowNumber, studentRef, feeName, category, amountEGP, dueDate, term, status, errorReason, rawRowData, null);
    }

    public UploadRow(CsvUpload csvUpload, int rowNumber, String studentRef, String feeName,
                     String category, BigDecimal amountEGP, LocalDate dueDate, String term,
                     String status, String errorReason, String rawRowData, UUID feeLineId) {
        this.csvUpload = csvUpload;
        this.rowNumber = rowNumber;
        this.studentRef = studentRef;
        this.feeName = feeName;
        this.category = category;
        this.amountEGP = amountEGP;
        this.dueDate = dueDate;
        this.term = term;
        this.status = status;
        this.errorReason = errorReason;
        this.rawRowData = rawRowData;
        this.feeLineId = feeLineId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CsvUpload getCsvUpload() {
        return csvUpload;
    }

    public void setCsvUpload(CsvUpload csvUpload) {
        this.csvUpload = csvUpload;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getStudentRef() {
        return studentRef;
    }

    public void setStudentRef(String studentRef) {
        this.studentRef = studentRef;
    }

    public String getFeeName() {
        return feeName;
    }

    public void setFeeName(String feeName) {
        this.feeName = feeName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getAmountEGP() {
        return amountEGP;
    }

    public void setAmountEGP(BigDecimal amountEGP) {
        this.amountEGP = amountEGP;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public String getRawRowData() {
        return rawRowData;
    }

    public void setRawRowData(String rawRowData) {
        this.rawRowData = rawRowData;
    }

    public UUID getFeeLineId() {
        return feeLineId;
    }

    public void setFeeLineId(UUID feeLineId) {
        this.feeLineId = feeLineId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UploadRow that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
