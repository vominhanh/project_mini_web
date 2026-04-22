package com.example.demo.controller.notification;

import com.example.demo.dto.notification.WorkflowEmailCommand;
import com.example.demo.dto.notification.WorkflowNotificationEvent;
import com.example.demo.service.work_flow.WorkflowEmailService;
import com.example.demo.service.work_flow.WorkflowNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/workflow")
public class WorkflowCallbackController {
    private static final Logger log = LoggerFactory.getLogger(WorkflowCallbackController.class);

    private final WorkflowEmailService workflowEmailService;
    private final WorkflowNotificationService workflowNotificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public WorkflowCallbackController(WorkflowEmailService workflowEmailService,
                                      WorkflowNotificationService workflowNotificationService,
                                      SimpMessagingTemplate messagingTemplate) {
        this.workflowEmailService = workflowEmailService;
        this.workflowNotificationService = workflowNotificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/email")
    public void handleEmail(@RequestBody WorkflowEmailCommand command) {
        workflowEmailService.sendEmail(command.getToEmail(), command.getSubject(), command.getBody());
        log.info("Gateway callback email sent to {}", command.getToEmail());
    }

    @PostMapping("/notification")
    public void handleNotification(@RequestBody WorkflowNotificationEvent event) {
        WorkflowNotificationEvent storedEvent = workflowNotificationService.saveEvent(event);
        messagingTemplate.convertAndSend("/topic/rooms", storedEvent);
        log.info("Gateway callback notification pushed: eventType={}, roomId={}, roomName={}",
                storedEvent.getEventType(), storedEvent.getRoomId(), storedEvent.getRoomName());
    }
}
