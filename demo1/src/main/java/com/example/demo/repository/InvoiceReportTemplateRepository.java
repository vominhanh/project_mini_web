package com.example.demo.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InvoiceReportTemplateRepository {

    /**
     * Bản ghi nhị phân JRXML/logo lưu trong bảng invoice_report_template (không phải entity JPA).
     */
    public record StoredInvoiceTemplate(
            String jrxmlName,
            byte[] jrxmlContent,
            String logoName,
            byte[] logoContent,
            Instant updatedAt) {
    }

    public static final int SINGLE_TEMPLATE_ID = 1;

    private final JdbcTemplate jdbcTemplate;

    public void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS invoice_report_template (
                    id INT PRIMARY KEY,
                    jrxml_name VARCHAR(255) NOT NULL,
                    jrxml_content BYTEA NOT NULL,
                    logo_name VARCHAR(255) NOT NULL,
                    logo_content BYTEA NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    public int countById(int id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice_report_template WHERE id = ?",
                Integer.class,
                id);
        return count == null ? 0 : count;
    }

    public Optional<StoredInvoiceTemplate> findById(int id) {
        List<StoredInvoiceTemplate> rows = jdbcTemplate.query(
                """
                        SELECT jrxml_name, jrxml_content, logo_name, logo_content, updated_at
                        FROM invoice_report_template
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new StoredInvoiceTemplate(
                        rs.getString("jrxml_name"),
                        rs.getBytes("jrxml_content"),
                        rs.getString("logo_name"),
                        rs.getBytes("logo_content"),
                        rs.getTimestamp("updated_at").toInstant()),
                id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    public void upsert(int id, String jrxmlName, byte[] jrxmlContent, String logoName, byte[] logoContent) {
        jdbcTemplate.update(
                """
                        INSERT INTO invoice_report_template(id, jrxml_name, jrxml_content, logo_name, logo_content, updated_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (id)
                        DO UPDATE SET
                            jrxml_name = EXCLUDED.jrxml_name,
                            jrxml_content = EXCLUDED.jrxml_content,
                            logo_name = EXCLUDED.logo_name,
                            logo_content = EXCLUDED.logo_content,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                id, jrxmlName, jrxmlContent, logoName, logoContent);
    }
}
