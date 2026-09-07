package com.tuitionnetwork.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * An operational alert shown in the bank back-office Notifications feed
 * (distinct from the guardian-facing {@link Notification} SMS records).
 * Server-generated only; the client can mark read or dismiss.
 */
@Entity
@Table(name = "back_office_notification")
public class BackOfficeNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "notif_type", nullable = false, length = 32)
    private NotifType notifType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 8)
    private NotifSeverity severity;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    /** Optional reference line, e.g. "Exception: EXC-001 · Status: Under Investigation". */
    @Column(name = "meta", length = 512)
    private String meta;

    @Column(name = "is_read", nullable = false)
    private boolean readFlag = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Optional call-to-action: label + destination screen + entity id. */
    @Column(name = "action_label")
    private String actionLabel;

    @Column(name = "action_screen")
    private String actionScreen;

    @Column(name = "action_entity_id")
    private String actionEntityId;

    public BackOfficeNotification() {
    }

    public BackOfficeNotification(NotifType notifType, NotifSeverity severity, String title, String body) {
        this.notifType = notifType;
        this.severity = severity;
        this.title = title;
        this.body = body;
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public NotifType getNotifType() {
        return notifType;
    }

    public void setNotifType(NotifType notifType) {
        this.notifType = notifType;
    }

    public NotifSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(NotifSeverity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }

    public boolean isReadFlag() {
        return readFlag;
    }

    public void setReadFlag(boolean readFlag) {
        this.readFlag = readFlag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getActionScreen() {
        return actionScreen;
    }

    public void setActionScreen(String actionScreen) {
        this.actionScreen = actionScreen;
    }

    public String getActionEntityId() {
        return actionEntityId;
    }

    public void setActionEntityId(String actionEntityId) {
        this.actionEntityId = actionEntityId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BackOfficeNotification that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
