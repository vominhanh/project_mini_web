package com.example.demo.delegate;

import com.example.demo.dto.notification.WorkflowEmailCommand;
import com.example.demo.service.work_flow.WorkflowEventPublisher;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("sendRejectionEmail")
public class SendRejectionEmailDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(SendRejectionEmailDelegate.class);
    private final WorkflowEventPublisher workflowEventPublisher;

    public SendRejectionEmailDelegate(WorkflowEventPublisher workflowEventPublisher) {
        this.workflowEventPublisher = workflowEventPublisher;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String ownerEmail = String.valueOf(execution.getVariable("ownerEmail"));
        String roomName = String.valueOf(execution.getVariable("roomName"));
        Object retryCount = execution.getVariable("retryCount");

        if (ownerEmail == null || ownerEmail.isBlank() || "null".equalsIgnoreCase(ownerEmail)) {
            log.warn("Skip rejection email: ownerEmail is empty for room={}", roomName);
            return;
        }

        WorkflowEmailCommand command = new WorkflowEmailCommand();
        command.setToEmail(ownerEmail);
        command.setSubject("[Room Booking] Phong bi tu choi");
        command.setBody("Xin chao,\n\nPhong '" + roomName + "' tam thoi bi tu choi.\nVui long cap nhat thong tin va gui duyet lai.\nSo lan cap nhat hien tai: " + retryCount + "\n\nCam on.");
        workflowEventPublisher.publishEmail(command);
        log.info("Rejection email event published: to={}, room={}, retryCount={}", ownerEmail, roomName, retryCount);
    }
}
