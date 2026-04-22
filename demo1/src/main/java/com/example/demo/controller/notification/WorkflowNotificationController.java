package com.example.demo.controller.notification;

import com.example.demo.dto.notification.WorkflowNotificationEvent;
import com.example.demo.service.work_flow.WorkflowNotificationService;
import com.example.demo.util.BearerTokenExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class WorkflowNotificationController {
    private final WorkflowNotificationService workflowNotificationService;

    public WorkflowNotificationController(WorkflowNotificationService workflowNotificationService) {
        this.workflowNotificationService = workflowNotificationService;
    }

    @GetMapping("/active")
    public ResponseEntity<List<WorkflowNotificationEvent>> active(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = BearerTokenExtractor.fromAuthorizationHeader(authorization);
        return ResponseEntity.ok(workflowNotificationService.findActiveForAccessToken(token));
    }
}
