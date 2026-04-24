package com.example.demo.service.work_flow;

import com.example.demo.dto.notification.WorkflowNotificationEvent;
import com.example.demo.entity.Notification;
import com.example.demo.repository.WorkflowNotificationRepository;
import com.example.demo.util.TokenRoleResolver;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkflowNotificationService {
    private final WorkflowNotificationRepository workflowNotificationRepository;

    public WorkflowNotificationService(WorkflowNotificationRepository workflowNotificationRepository) {
        this.workflowNotificationRepository = workflowNotificationRepository;
    }

    public WorkflowNotificationEvent saveEvent(WorkflowNotificationEvent event) {
        Notification notification = new Notification();
        notification.setEventType(event.getEventType());
        notification.setRoomId(event.getRoomId());
        notification.setRoomName(event.getRoomName());
        notification.setOwnerEmail(normalize(event.getOwnerEmail()));
        notification.setRecipientEmail(resolveRecipientEmail(event));
        notification.setRecipientRole(resolveRecipientRole(event));
        notification.setApproved(event.getApproved());
        notification.setRetryCount(event.getRetryCount());
        notification.setStatus(event.getStatus());
        notification.setMessage(event.getMessage());
        notification.setCreatedAt(event.getCreatedAt() == null ? LocalDateTime.now() : event.getCreatedAt());
        notification.setCompleted(isCompletedByDefault(event));
        notification.setCompletedAt(notification.getCompleted() ? LocalDateTime.now() : null);
        notification.setTargetPanel(resolveTargetPanel(event));

        Notification saved = workflowNotificationRepository.save(notification);
        WorkflowNotificationEvent response = toEvent(saved);
        response.setId(saved.getId());
        return response;
    }

    public List<WorkflowNotificationEvent> findActiveForAccessToken(String accessToken) {
        List<String> roles = TokenRoleResolver.extractRoles(accessToken);
        String effectiveRole = TokenRoleResolver.resolveEffectiveRole(roles);
        if ("admin".equalsIgnoreCase(effectiveRole)) {
            return workflowNotificationRepository
                    .findByRecipientRoleIgnoreCaseAndCompletedFalseOrderByCreatedAtDesc("ADMIN")
                    .stream()
                    .map(this::toEvent)
                    .toList();
        }

        String email = normalize(firstNonBlank(
                stringOf(TokenRoleResolver.extractCurrentUser(accessToken).get("email")),
                stringOf(TokenRoleResolver.extractCurrentUser(accessToken).get("username"))
        ));
        if (email.isBlank()) {
            return List.of();
        }

        return workflowNotificationRepository
                .findByRecipientEmailIgnoreCaseAndCompletedFalseOrderByCreatedAtDesc(email)
                .stream()
                .map(this::toEvent)
                .toList();
    }

    public void completeAdminNotificationsForRoom(Long roomId) {
        if (roomId == null) {
            return;
        }
        completeNotifications(
                workflowNotificationRepository.findByRoomIdAndRecipientRoleIgnoreCaseAndCompletedFalse(roomId, "ADMIN")
        );
    }

    public void completeUserNotificationsForRoom(Long roomId, String ownerEmail) {
        if (roomId == null || ownerEmail == null || ownerEmail.isBlank()) {
            return;
        }
        completeNotifications(
                workflowNotificationRepository.findByRoomIdAndRecipientEmailIgnoreCaseAndCompletedFalse(roomId, normalize(ownerEmail))
        );
    }

    private void completeNotifications(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Notification notification : notifications) {
            notification.setCompleted(true);
            notification.setCompletedAt(now);
        }
        workflowNotificationRepository.saveAll(notifications);
    }

    private WorkflowNotificationEvent toEvent(Notification notification) {
        WorkflowNotificationEvent event = new WorkflowNotificationEvent();
        event.setId(notification.getId());
        event.setEventType(notification.getEventType());
        event.setRoomId(notification.getRoomId());
        event.setRoomName(notification.getRoomName());
        event.setOwnerEmail(notification.getOwnerEmail());
        event.setApproved(notification.getApproved());
        event.setRetryCount(notification.getRetryCount());
        event.setStatus(notification.getStatus());
        event.setMessage(notification.getMessage());
        event.setCreatedAt(notification.getCreatedAt());
        event.setTargetPanel(notification.getTargetPanel());
        return event;
    }

    private String resolveRecipientRole(WorkflowNotificationEvent event) {
        if ("USER_TO_ADMIN".equalsIgnoreCase(event.getEventType())
                || "USER_TO_ADMIN_UPDATE".equalsIgnoreCase(event.getEventType())) {
            return "ADMIN";
        }
        if (normalize(event.getOwnerEmail()).isBlank()) {
            return "USER";
        }
        return "USER";
    }

    private String resolveRecipientEmail(WorkflowNotificationEvent event) {
        if ("USER_TO_ADMIN".equalsIgnoreCase(event.getEventType())
                || "USER_TO_ADMIN_UPDATE".equalsIgnoreCase(event.getEventType())) {
            return "";
        }
        return normalize(event.getOwnerEmail());
    }

    private boolean isCompletedByDefault(WorkflowNotificationEvent event) {
        return !"USER_TO_ADMIN".equalsIgnoreCase(event.getEventType())
                && !"USER_TO_ADMIN_UPDATE".equalsIgnoreCase(event.getEventType())
                && !"REJECTED_NEEDS_UPDATE".equalsIgnoreCase(event.getEventType());
    }

    private String resolveTargetPanel(WorkflowNotificationEvent event) {
        if ("USER_TO_ADMIN".equalsIgnoreCase(event.getEventType())
                || "USER_TO_ADMIN_UPDATE".equalsIgnoreCase(event.getEventType())) {
            return "booking";
        }
        if ("REJECTED_NEEDS_UPDATE".equalsIgnoreCase(event.getEventType())) {
            return "create_room";
        }
        return "booking";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private String stringOf(Object value) {
        return value == null ? "" : value.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
