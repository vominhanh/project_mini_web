package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class WorkflowEmailService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public WorkflowEmailService(JavaMailSender mailSender,
                                @Value("${app.mail.from:${spring.mail.username:}}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    public void sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank() || "null".equalsIgnoreCase(toEmail)) {
            log.warn("Skip email because recipient is empty");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
