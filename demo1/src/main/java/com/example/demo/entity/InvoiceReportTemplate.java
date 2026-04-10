package com.example.demo.entity;

import java.time.Instant;

/**
 * Bản ghi cấu hình mẫu báo cáo invoice (ánh xạ bảng invoice_report_template).
 */
public record InvoiceReportTemplate(
        String jrxmlName,
        byte[] jrxmlContent,
        String logoName,
        byte[] logoContent,
        Instant updatedAt) {
}
