package com.example.demo.service.strategy.report;

import com.example.demo.service.jasper.InvoiceReportService;
import net.sf.jasperreports.engine.JRException;
import org.springframework.http.MediaType;

import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * Chiến lược xuất báo cáo theo định dạng (pdf / xlsx).
 */
public interface InvoiceReportExportStrategy {

    boolean supports(String format);

    void export(InvoiceReportService invoiceReportService, OutputStream out, List<String> columns, String accessToken)
            throws JRException;

    String buildFilename(LocalDate date);

    MediaType mediaType();
}
