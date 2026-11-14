package com.smartdesk.notification_service.repository;

import com.smartdesk.notification_service.entity.NotificationLog;
import com.smartdesk.notification_service.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);
    List<NotificationLog> findByStatus(NotificationStatus status);
}
