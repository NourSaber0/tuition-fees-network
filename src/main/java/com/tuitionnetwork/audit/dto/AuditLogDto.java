package com.tuitionnetwork.audit.dto;

import com.tuitionnetwork.audit.domain.AuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuditLogDto {
    private UUID id;
    private UUID auditId;
    private String user;
    private UUID actorId;
    private String role;
    private String actorType;
    private String action;
    private String entity;
    private String entityId;
    private String targetResource;
    private String prevValue;
    private String newValue;
    private LocalDateTime timestamp;
    private String ipAddress;
    private String severity;

    public AuditLogDto() {
    }

    public AuditLogDto(UUID auditId, UUID actorId, String actorType, String action, String targetResource, LocalDateTime timestamp, String severity) {
        this.id = auditId;
        this.auditId = auditId;
        this.actorId = actorId;
        this.user = actorId != null ? actorId.toString() : "System";
        this.role = actorType;
        this.actorType = actorType;
        this.action = action;
        this.targetResource = targetResource;
        this.entity = deriveEntity(targetResource, action);
        this.entityId = deriveEntityId(targetResource);
        this.timestamp = timestamp;
        this.severity = severity != null ? severity : "INFO";
        this.ipAddress = "127.0.0.1";
    }

    public static AuditLogDto from(AuditLog a) {
        if (a == null) return null;
        AuditLogDto dto = new AuditLogDto();
        dto.setId(a.getAuditId());
        dto.setAuditId(a.getAuditId());
        dto.setActorId(a.getActorId());
        dto.setUser(a.getUserName() != null ? a.getUserName() : (a.getActorId() != null ? a.getActorId().toString() : "System"));
        dto.setRole(a.getActorType());
        dto.setActorType(a.getActorType());
        dto.setAction(a.getAction());
        dto.setTargetResource(a.getTargetResource());
        dto.setEntity(a.getEntity() != null ? a.getEntity() : deriveEntity(a.getTargetResource(), a.getAction()));
        dto.setEntityId(a.getEntityId() != null ? a.getEntityId() : deriveEntityId(a.getTargetResource()));
        dto.setPrevValue(a.getPrevValue());
        dto.setNewValue(a.getNewValue());
        dto.setTimestamp(a.getTimestamp());
        dto.setIpAddress(a.getIpAddress() != null ? a.getIpAddress() : "127.0.0.1");
        dto.setSeverity(a.getSeverity() != null ? a.getSeverity().toLowerCase() : "info");
        return dto;
    }

    private static String deriveEntity(String targetResource, String action) {
        if (targetResource != null && !targetResource.isBlank()) {
            String[] parts = targetResource.split("[:\\s]+");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0];
            }
        }
        if (action != null && action.contains("_")) {
            return action.substring(0, action.indexOf('_'));
        }
        return "System";
    }

    private static String deriveEntityId(String targetResource) {
        if (targetResource != null && !targetResource.isBlank()) {
            String[] parts = targetResource.split("[:\\s]+");
            if (parts.length > 1 && !parts[1].isBlank()) {
                return parts[1];
            }
            return targetResource;
        }
        return "-";
    }

    public UUID getId() { return id != null ? id : auditId; }
    public void setId(UUID id) { this.id = id; this.auditId = id; }
    public UUID getAuditId() { return auditId != null ? auditId : id; }
    public void setAuditId(UUID auditId) { this.auditId = auditId; this.id = auditId; }
    public String getUser() { return user != null ? user : (actorId != null ? actorId.toString() : "System"); }
    public void setUser(String user) { this.user = user; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public String getRole() { return role != null ? role : actorType; }
    public void setRole(String role) { this.role = role; this.actorType = role; }
    public String getActorType() { return actorType != null ? actorType : role; }
    public void setActorType(String actorType) { this.actorType = actorType; this.role = actorType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }
    public String getPrevValue() { return prevValue; }
    public void setPrevValue(String prevValue) { this.prevValue = prevValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
