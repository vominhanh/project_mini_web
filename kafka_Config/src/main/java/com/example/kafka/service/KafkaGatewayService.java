package com.example.kafka.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaGatewayService {
    private static final Logger log = LoggerFactory.getLogger(KafkaGatewayService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String notificationTopic;
    private final String emailTopic;

    public KafkaGatewayService(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper,
                               @Value("${app.kafka.topic.workflow-notification}") String notificationTopic,
                               @Value("${app.kafka.topic.workflow-email}") String emailTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.notificationTopic = notificationTopic;
        this.emailTopic = emailTopic;
    }

    public void publishWorkflowNotification(JsonNode payload) {
        log.info("Publishing workflow notification to topic: {}", notificationTopic);
        publish(notificationTopic, payload);
    }

    public void publishWorkflowEmail(JsonNode payload) {

        publish(emailTopic, payload);
    }

    private void publish(String topic, JsonNode payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, message);
        } catch (Exception ex) {
            log.error("Cannot publish to topic={}", topic, ex);
            throw new IllegalStateException("Cannot publish kafka message", ex);
        }
    }
}
