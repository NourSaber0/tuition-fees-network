package com.tuitionnetwork.notifications.repository;

import com.tuitionnetwork.notifications.domain.BackOfficeNotification;
import com.tuitionnetwork.notifications.domain.NotifType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackOfficeNotificationRepository extends JpaRepository<BackOfficeNotification, UUID> {

    long countByReadFlagFalse();

    /**
     * Feed query. {@code type} null = all categories; {@code unreadOnly} true = only unread.
     * Newest first.
     */
    @Query("""
            SELECT n FROM BackOfficeNotification n
            WHERE (:type IS NULL OR n.notifType = :type)
              AND (:unreadOnly = false OR n.readFlag = false)
            ORDER BY n.createdAt DESC
            """)
    Page<BackOfficeNotification> feed(@Param("type") NotifType type,
                                     @Param("unreadOnly") boolean unreadOnly,
                                     Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE BackOfficeNotification n SET n.readFlag = true WHERE n.readFlag = false")
    int markAllRead();
}
