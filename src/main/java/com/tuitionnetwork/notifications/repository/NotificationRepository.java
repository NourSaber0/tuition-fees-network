package com.tuitionnetwork.notifications.repository;

import com.tuitionnetwork.notifications.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByGuardianId(UUID guardianId);
    boolean existsByGuardianIdAndTypeAndMessageContaining(UUID guardianId, String type, String message);
}
