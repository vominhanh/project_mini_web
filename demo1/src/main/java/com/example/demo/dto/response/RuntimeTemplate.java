package com.example.demo.dto.response;

import net.sf.jasperreports.engine.JasperReport;

import java.time.Instant;

public record RuntimeTemplate(
        JasperReport compiledReport,
        String repositoryBasePath,
        String jrxmlName,
        String logoName,
        Instant updatedAt) {
}
