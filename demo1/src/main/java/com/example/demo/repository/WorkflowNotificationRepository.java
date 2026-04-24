package com.example.demo.repository;

import com.example.demo.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowNotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientRoleIgnoreCaseAndCompletedFalseOrderByCreatedAtDesc(String recipientRole);

    List<Notification> findByRecipientEmailIgnoreCaseAndCompletedFalseOrderByCreatedAtDesc(String recipientEmail);

    List<Notification> findByRoomIdAndRecipientRoleIgnoreCaseAndCompletedFalse(Long roomId, String recipientRole);

    List<Notification> findByRoomIdAndRecipientEmailIgnoreCaseAndCompletedFalse(Long roomId, String recipientEmail);
}
