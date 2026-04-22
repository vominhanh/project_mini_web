package com.example.demo.delegate;

import com.example.demo.dto.notification.WorkflowNotificationEvent;
import com.example.demo.service.work_flow.WorkflowEventPublisher;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component("notificationUserToAdminUpdate")
public class NotificationUserToAdminUpdateDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(NotificationUserToAdminUpdateDelegate.class);

    private final WorkflowEventPublisher workflowEventPublisher;

    public NotificationUserToAdminUpdateDelegate(WorkflowEventPublisher workflowEventPublisher) {
        this.workflowEventPublisher = workflowEventPublisher;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long roomId = readRoomId(execution.getVariable("roomId"));
        String roomName = String.valueOf(execution.getVariable("roomName"));
        String ownerEmail = String.valueOf(execution.getVariable("ownerEmail"));
        Integer retryCount = readRetryCount(execution.getVariable("retryCount"));

        WorkflowNotificationEvent event = new WorkflowNotificationEvent();
        event.setEventType("USER_TO_ADMIN_UPDATE");
        event.setRoomId(roomId);
        event.setRoomName(roomName);
        event.setOwnerEmail(ownerEmail);
        event.setApproved(null);
        event.setRetryCount(retryCount);
        event.setStatus("PENDING_REVIEW_AFTER_UPDATE");
        event.setMessage("Nguoi dung da cap nhat phong, can admin duyet lai");
        event.setCreatedAt(LocalDateTime.now());
        workflowEventPublisher.publishNotification(event);

        log.info("User-to-admin update notification event published: roomId={}, roomName={}, ownerEmail={}, retryCount={}",
                roomId, roomName, ownerEmail, retryCount);
    }

    private Long readRoomId(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer readRetryCount(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Long l) {
            return l.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
