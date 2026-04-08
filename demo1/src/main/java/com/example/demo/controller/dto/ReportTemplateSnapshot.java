package com.example.demo.controller.dto;

import java.time.Instant;

public  record ReportTemplateSnapshot (
    String jrxmlName,
    byte[] jrxmlContent,
    String logoName,
    byte[] logoContent,
    Instant updatedAt) {
}
