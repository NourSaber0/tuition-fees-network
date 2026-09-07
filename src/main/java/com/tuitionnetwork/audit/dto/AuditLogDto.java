package com.tuitionnetwork.audit.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuditLogDto {
    private UUID auditId;
    private UUID actorId;
    private String actorType;
    private String action;
    private String targetResource;
    private LocalDateTime timestamp;
    private String severity;

    public AuditLogDto() {
    }

    public AuditLogDto(UUID auditId, UUID actorId, String actorType, String action, String targetResource, LocalDateTime timestamp, String severity) {
        this.auditId = auditId;
        this.actorId = actorId;
        this.actorType = actorType;
        this.action = action;
        this.targetResource = targetResource;
        this.timestamp = timestamp;
        this.severity = severity;
    }

    public UUID getAuditId() { return auditId; }
    public void setAuditId(UUID auditId) { this.auditId = auditId; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
