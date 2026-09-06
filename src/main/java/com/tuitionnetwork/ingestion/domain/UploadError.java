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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "upload_error")
public class UploadError {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "csv_upload_id", nullable = false)
    private CsvUpload csvUpload;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @Column(name = "raw_row_data", length = 2000)
    private String rawRowData;

    public UploadError() {
    }

    public UploadError(CsvUpload csvUpload, int rowNumber, String errorMessage, String rawRowData) {
        this.csvUpload = csvUpload;
        this.rowNumber = rowNumber;
        this.errorMessage = errorMessage;
        this.rawRowData = rawRowData;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRawRowData() {
        return rawRowData;
    }

    public void setRawRowData(String rawRowData) {
        this.rawRowData = rawRowData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UploadError that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
