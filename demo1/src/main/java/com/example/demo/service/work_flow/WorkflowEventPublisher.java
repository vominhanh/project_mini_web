package com.example.demo.service.work_flow;

import com.example.demo.dto.notification.WorkflowEmailCommand;
import com.example.demo.dto.notification.WorkflowNotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Service;

@Service
public class WorkflowEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEventPublisher.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String gatewayBaseUrl;

    public WorkflowEventPublisher(RestClient.Builder restClientBuilder,
                                  ObjectMapper objectMapper,
                                  @Value("${app.kafka-gateway.base-url:http://localhost:8083}") String gatewayBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    public void publishNotification(WorkflowNotificationEvent event) {
        log.info(event.toString());
        publish("/api/kafka/publish/workflow-notification", event);
    }

    public void publishEmail(WorkflowEmailCommand command) {
        log.info(command.toString());
        publish("/api/kafka/publish/workflow-email", command);
    }

    private void publish(String endpoint, Object payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            restClient.post()
                    .uri(gatewayBaseUrl + endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(value)
                    .retrieve()
                    .toBodilessEntity();
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize workflow payload for endpoint={}", endpoint, e);
        } catch (Exception e) {
            log.error("Unable to call kafka gateway endpoint={}", endpoint, e);
        }
    }
}
