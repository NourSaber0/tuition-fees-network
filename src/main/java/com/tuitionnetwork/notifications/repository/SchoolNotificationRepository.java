package com.tuitionnetwork.notifications.repository;

import com.tuitionnetwork.notifications.domain.SchoolNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolNotificationRepository extends JpaRepository<SchoolNotification, UUID> {

    Page<SchoolNotification> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId, Pageable pageable);

    Page<SchoolNotification> findByInstitutionIdAndReadFlagOrderByCreatedAtDesc(UUID institutionId, boolean readFlag, Pageable pageable);

    Page<SchoolNotification> findByInstitutionIdAndTypeIgnoreCaseOrderByCreatedAtDesc(UUID institutionId, String type, Pageable pageable);

    Page<SchoolNotification> findByInstitutionIdAndTypeIgnoreCaseAndReadFlagOrderByCreatedAtDesc(UUID institutionId, String type, boolean readFlag, Pageable pageable);

    Page<SchoolNotification> findByInstitutionIdAndTypeIgnoreCaseAndNotificationStatusIgnoreCaseOrderByCreatedAtDesc(UUID institutionId, String type, String notificationStatus, Pageable pageable);

    long countByInstitutionIdAndReadFlagFalse(UUID institutionId);

    Optional<SchoolNotification> findByIdAndInstitutionId(UUID id, UUID institutionId);

    List<SchoolNotification> findAllByNotificationRef(String notificationRef);

    Optional<SchoolNotification> findByNotificationRefAndInstitutionId(String notificationRef, UUID institutionId);

    List<SchoolNotification> findByInstitutionId(UUID institutionId);

    @Modifying
    @Query("UPDATE SchoolNotification n SET n.readFlag = true, n.readAt = :now WHERE n.institutionId = :institutionId AND n.readFlag = false")
    int markAllReadForInstitution(@Param("institutionId") UUID institutionId, @Param("now") LocalDateTime now);
}
