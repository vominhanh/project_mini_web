package com.example.demo.delegate;

import com.example.demo.dto.notification.WorkflowNotificationEvent;
import com.example.demo.repository.RoomRepository;
import com.example.demo.service.work_flow.WorkflowEventPublisher;
import com.example.demo.service.work_flow.WorkflowNotificationService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component("notificationRejectUpdate")
public class NotificationRejectUpdateDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(NotificationRejectUpdateDelegate.class);

    private final RoomRepository roomRepository;
    private final WorkflowEventPublisher workflowEventPublisher;
    private final WorkflowNotificationService workflowNotificationService;

    public NotificationRejectUpdateDelegate(RoomRepository roomRepository,
                                            WorkflowEventPublisher workflowEventPublisher,
                                            WorkflowNotificationService workflowNotificationService) {
        this.roomRepository = roomRepository;
        this.workflowEventPublisher = workflowEventPublisher;
        this.workflowNotificationService = workflowNotificationService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long roomId = readRoomId(execution.getVariable("roomId"));
        String roomName = String.valueOf(execution.getVariable("roomName"));
        String ownerEmail = String.valueOf(execution.getVariable("ownerEmail"));
        Object retryCount = execution.getVariable("retryCount");

        if (roomId != null) {
            roomRepository.findById(roomId).ifPresent(room -> {
                room.setStatus("REJECTED");
                room.setIsAvailable(false);
                room.setUpdatedAt(LocalDateTime.now());
                roomRepository.save(room);
            });
            workflowNotificationService.completeAdminNotificationsForRoom(roomId);
        }

        WorkflowNotificationEvent event = new WorkflowNotificationEvent();
        event.setEventType("REJECTED_NEEDS_UPDATE");
        event.setRoomId(roomId);
        event.setRoomName(roomName);
        event.setOwnerEmail(ownerEmail);
        event.setApproved(false);
        event.setStatus("REJECTED");
        event.setRetryCount(retryCount instanceof Integer i ? i : 0);
        event.setMessage("Phong bi tu choi. Vui long cap nhat va gui duyet lai");
        event.setCreatedAt(LocalDateTime.now());
        workflowEventPublisher.publishNotification(event);

        log.info("Reject-update notification event published: roomId={}, roomName={}, ownerEmail={}, retryCount={}",
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
}
