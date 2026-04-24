package com.example.kafka.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class KafkaWorkflowForwarder {
    private static final Logger log = LoggerFactory.getLogger(KafkaWorkflowForwarder.class);

    private final RestClient restClient;
    private final String demo1BaseUrl;

    public KafkaWorkflowForwarder(RestClient.Builder restClientBuilder,
                                  @Value("${app.demo1.base-url:http://localhost:8082}") String demo1BaseUrl) {
        this.restClient = restClientBuilder.build();
        this.demo1BaseUrl = demo1BaseUrl;
    }

    @KafkaListener(topics = "${app.kafka.topic.workflow-notification}")
    public void onWorkflowNotification(String payload) {
        forward("/internal/workflow/notification", payload);
    }

    @KafkaListener(topics = "${app.kafka.topic.workflow-email}")
    public void onWorkflowEmail(String payload) {
        forward("/internal/workflow/email", payload);
    }

    private void forward(String endpoint, String payload) {
        try {
            restClient.post()
                    .uri(demo1BaseUrl + endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.error("Cannot forward kafka message to demo1 endpoint={}", endpoint, ex);
        }
    }
}
