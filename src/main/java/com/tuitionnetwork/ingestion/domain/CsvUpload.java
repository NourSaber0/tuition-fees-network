package com.tuitionnetwork.ingestion.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "csv_upload")
public class CsvUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "accepted_rows", nullable = false)
    private int acceptedRows = 0;

    @Column(name = "status", nullable = false)
    private String status = "Completed";

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "format")
    private String format;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @OneToMany(mappedBy = "csvUpload", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UploadError> uploadErrors = new ArrayList<>();

    @OneToMany(mappedBy = "csvUpload", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UploadRow> uploadRows = new ArrayList<>();

    public CsvUpload() {
    }

    public CsvUpload(UUID institutionId, String fileName, int totalRows, int failedRows) {
        this.institutionId = institutionId;
        this.fileName = fileName;
        this.totalRows = totalRows;
        this.failedRows = failedRows;
        this.acceptedRows = Math.max(0, totalRows - failedRows);
        this.status = computeStatus(totalRows, this.acceptedRows, failedRows);
        this.uploadedAt = LocalDateTime.now();
    }

    public CsvUpload(UUID institutionId, String fileName, String fileHash, String format,
                     int totalRows, int acceptedRows, int failedRows, String status) {
        this.institutionId = institutionId;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.format = format;
        this.totalRows = totalRows;
        this.acceptedRows = acceptedRows;
        this.failedRows = failedRows;
        this.status = status != null ? status : computeStatus(totalRows, acceptedRows, failedRows);
        this.uploadedAt = LocalDateTime.now();
    }

    private static String computeStatus(int total, int accepted, int failed) {
        if (total == 0 || failed == 0) {
            return "Completed";
        }
        if (accepted > 0 && failed > 0) {
            return "Completed with Errors";
        }
        return "Failed";
    }

    @PrePersist
    protected void onUpload() {
        if (this.uploadedAt == null) {
            this.uploadedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = computeStatus(totalRows, acceptedRows, failedRows);
        }
    }

    public void addError(UploadError error) {
        uploadErrors.add(error);
        error.setCsvUpload(this);
    }

    public void addUploadRow(UploadRow row) {
        uploadRows.add(row);
        row.setCsvUpload(this);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(int failedRows) {
        this.failedRows = failedRows;
    }

    public int getAcceptedRows() {
        return acceptedRows;
    }

    public void setAcceptedRows(int acceptedRows) {
        this.acceptedRows = acceptedRows;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public List<UploadError> getUploadErrors() {
        return uploadErrors;
    }

    public void setUploadErrors(List<UploadError> uploadErrors) {
        this.uploadErrors = uploadErrors;
    }

    public List<UploadRow> getUploadRows() {
        return uploadRows;
    }

    public void setUploadRows(List<UploadRow> uploadRows) {
        this.uploadRows = uploadRows;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CsvUpload that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
