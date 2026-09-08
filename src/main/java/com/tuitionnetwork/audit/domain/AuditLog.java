package com.tuitionnetwork.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "actor_id")
    private UUID actorId; // Guardian or Admin ID (or System)

    @Column(name = "actor_type", nullable = false)
    private String actorType; // Guardian, InstitutionAdmin, System

    @Column(name = "action", nullable = false)
    private String action; // e.g., Search_ID, Payment_Initiated

    @Column(name = "target_resource")
    private String targetResource; // e.g., Hashed National ID

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "severity", nullable = false)
    private String severity = "INFO"; // INFO, WARNING, CRITICAL

    @Column(name = "entity")
    private String entity;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "prev_value", length = 1000)
    private String prevValue;

    @Column(name = "new_value", length = 1000)
    private String newValue;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_name")
    private String userName;

    public AuditLog() {
    }

    public AuditLog(UUID actorId, String actorType, String action, String targetResource) {
        this(actorId, actorType, action, targetResource, "INFO");
    }

    public AuditLog(UUID actorId, String actorType, String action, String targetResource, String severity) {
        this.actorId = actorId;
        this.actorType = actorType;
        this.action = action;
        this.targetResource = targetResource;
        this.severity = severity == null ? "INFO" : severity;
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(UUID actorId, String actorType, String action, String targetResource, String severity,
                    String entity, String entityId, String prevValue, String newValue, String ipAddress, String userName) {
        this.actorId = actorId;
        this.actorType = actorType;
        this.action = action;
        this.targetResource = targetResource;
        this.severity = severity == null ? "INFO" : severity;
        this.entity = entity;
        this.entityId = entityId;
        this.prevValue = prevValue;
        this.newValue = newValue;
        this.ipAddress = ipAddress;
        this.userName = userName;
        this.timestamp = LocalDateTime.now();
    }

    @PrePersist
    protected void onPersist() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    public UUID getAuditId() {
        return auditId;
    }

    public void setAuditId(UUID auditId) {
        this.auditId = auditId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetResource() {
        return targetResource;
    }

    public void setTargetResource(String targetResource) {
        this.targetResource = targetResource;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getPrevValue() { return prevValue; }
    public void setPrevValue(String prevValue) { this.prevValue = prevValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog auditLog)) return false;
        return Objects.equals(auditId, auditLog.auditId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auditId);
    }
}
