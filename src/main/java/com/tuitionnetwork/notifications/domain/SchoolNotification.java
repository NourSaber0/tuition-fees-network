package com.tuitionnetwork.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "school_notification")
public class SchoolNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "notification_ref", nullable = false, length = 64)
    private String notificationRef;

    @Column(name = "type", nullable = false, length = 32)
    private String type; // payment, upload, reminder, penalty

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "fee_type")
    private String feeType;

    @Column(name = "fee_amount_egp")
    private Long feeAmountEGP;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "days_until_due")
    private Integer daysUntilDue;

    @Column(name = "notification_status", length = 32)
    private String notificationStatus = "Sent"; // Sent, Scheduled, Failed

    @Column(name = "is_read", nullable = false)
    private boolean readFlag = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "related_id")
    private String relatedId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SchoolNotification() {
    }

    public SchoolNotification(UUID institutionId, String notificationRef, String type, String title, String description) {
        this.institutionId = institutionId;
        this.notificationRef = notificationRef;
        this.type = type;
        this.title = title;
        this.description = description;
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
        if (notificationStatus == null) {
            notificationStatus = "Sent";
        }
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

    public String getNotificationRef() {
        return notificationRef;
    }

    public void setNotificationRef(String notificationRef) {
        this.notificationRef = notificationRef;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }

    public Long getFeeAmountEGP() {
        return feeAmountEGP;
    }

    public void setFeeAmountEGP(Long feeAmountEGP) {
        this.feeAmountEGP = feeAmountEGP;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Integer getDaysUntilDue() {
        return daysUntilDue;
    }

    public void setDaysUntilDue(Integer daysUntilDue) {
        this.daysUntilDue = daysUntilDue;
    }

    public String getNotificationStatus() {
        return notificationStatus;
    }

    public void setNotificationStatus(String notificationStatus) {
        this.notificationStatus = notificationStatus;
    }

    public boolean isReadFlag() {
        return readFlag;
    }

    public void setReadFlag(boolean readFlag) {
        this.readFlag = readFlag;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public String getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(String relatedId) {
        this.relatedId = relatedId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchoolNotification that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
