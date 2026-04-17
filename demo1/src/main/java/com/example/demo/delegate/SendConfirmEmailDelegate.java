package com.example.demo.delegate;

import com.example.demo.service.WorkflowEmailService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("sendConfirmEmail")
public class SendConfirmEmailDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(SendConfirmEmailDelegate.class);
    private final WorkflowEmailService workflowEmailService;

    public SendConfirmEmailDelegate(WorkflowEmailService workflowEmailService) {
        this.workflowEmailService = workflowEmailService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String ownerEmail = String.valueOf(execution.getVariable("ownerEmail"));
        String roomName = String.valueOf(execution.getVariable("roomName"));

        if (ownerEmail == null || ownerEmail.isBlank() || "null".equalsIgnoreCase(ownerEmail)) {
            log.warn("Skip approved email: ownerEmail is empty for room={}", roomName);
            return;
        }

        workflowEmailService.sendEmail(
                ownerEmail,
                "[Room Booking] Phong da duoc phe duyet",
                "Xin chao,\n\nPhong '" + roomName + "' cua ban da duoc phe duyet.\nBan co the su dung phong ngay bay gio.\n\nCam on."
        );
        log.info("Approved email sent: to={}, room={}", ownerEmail, roomName);
    }
}
