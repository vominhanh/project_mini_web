package com.example.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("notificationReject")
public class NotificationRejectDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(NotificationRejectDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Object approved = execution.getVariable("approved");
        Object roomId = execution.getVariable("roomId");
        Object roomName = execution.getVariable("roomName");
        log.info("Room workflow audited: roomId={}, roomName={}, approved={}", roomId, roomName, approved);
    }
}
