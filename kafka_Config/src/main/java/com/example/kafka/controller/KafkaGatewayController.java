package com.example.kafka.controller;

import com.example.kafka.service.KafkaGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kafka/publish")
public class KafkaGatewayController {
    private final KafkaGatewayService kafkaGatewayService;

    public KafkaGatewayController(KafkaGatewayService kafkaGatewayService) {
        this.kafkaGatewayService = kafkaGatewayService;
    }

    @PostMapping("/{channel}")
    public ResponseEntity<Void> publish(@PathVariable String channel, @RequestBody JsonNode payload) {
        if ("workflow-notification".equals(channel)) {
            kafkaGatewayService.publishWorkflowNotification(payload);
            return ResponseEntity.accepted().build();
        }
        if ("workflow-email".equals(channel)) {
            kafkaGatewayService.publishWorkflowEmail(payload);
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.badRequest().build();
    }
}
