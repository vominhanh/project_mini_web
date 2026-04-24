package com.example.demo.delegate;

import com.example.demo.dto.notification.WorkflowNotificationEvent;
import com.example.demo.service.work_flow.WorkflowEventPublisher;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component("notificationUserToAdmin")
public class NotificationUserToAdminDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(NotificationUserToAdminDelegate.class);

    private final WorkflowEventPublisher workflowEventPublisher;

    public NotificationUserToAdminDelegate(WorkflowEventPublisher workflowEventPublisher) {
        this.workflowEventPublisher = workflowEventPublisher;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Integer retryCount = (Integer) execution.getVariable("retryCount");

        if (retryCount != null && retryCount > 0) {
            return;
        }

        Long roomId = readRoomId(execution.getVariable("roomId"));
        String roomName = String.valueOf(execution.getVariable("roomName"));
        String ownerEmail = String.valueOf(execution.getVariable("ownerEmail"));

        WorkflowNotificationEvent event = new WorkflowNotificationEvent();
        event.setEventType("USER_TO_ADMIN");
        event.setRoomId(roomId);
        event.setRoomName(roomName);
        event.setOwnerEmail(ownerEmail);
        event.setApproved(null);
        event.setStatus("PENDING_APPROVAL");
        event.setMessage("Co yeu cau tao phong moi can admin phe duyet");
        event.setCreatedAt(LocalDateTime.now());
        workflowEventPublisher.publishNotification(event);
        log.info(event.toString());

        log.info("User-to-admin notification event published: roomId={}, roomName={}, ownerEmail={}",
                roomId, roomName, ownerEmail);
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
}
