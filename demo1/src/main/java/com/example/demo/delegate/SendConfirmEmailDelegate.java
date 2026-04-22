package com.example.demo.delegate;

import com.example.demo.dto.notification.WorkflowEmailCommand;
import com.example.demo.service.work_flow.WorkflowEventPublisher;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("sendConfirmEmail")
public class SendConfirmEmailDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(SendConfirmEmailDelegate.class);
    private final WorkflowEventPublisher workflowEventPublisher;

    public SendConfirmEmailDelegate(WorkflowEventPublisher workflowEventPublisher) {
        this.workflowEventPublisher = workflowEventPublisher;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String ownerEmail = String.valueOf(execution.getVariable("ownerEmail"));
        String roomName = String.valueOf(execution.getVariable("roomName"));

        if (ownerEmail == null || ownerEmail.isBlank() || "null".equalsIgnoreCase(ownerEmail)) {
            log.warn("Skip approved email: ownerEmail is empty for room={}", roomName);
            return;
        }

        WorkflowEmailCommand command = new WorkflowEmailCommand();
        command.setToEmail(ownerEmail);
        command.setSubject("[Room Booking] Phong da duoc phe duyet");
        command.setBody("Xin chao,\n\nPhong '" + roomName + "' cua ban da duoc phe duyet.\nBan co the su dung phong ngay bay gio.\n\nCam on.");
        workflowEventPublisher.publishEmail(command);
        log.info("Approved email event published: to={}, room={}", ownerEmail, roomName);
    }
}
