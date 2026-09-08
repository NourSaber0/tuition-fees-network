package com.tuitionnetwork.audit.repository;

import com.tuitionnetwork.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByActorId(UUID actorId);
    List<AuditLog> findByAction(String action);

    // Spring Data JPA derived query for actorType
    List<AuditLog> findByActorType(String actorType);

    // find within timestamp range
    List<AuditLog> findByTimestampBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);
}
