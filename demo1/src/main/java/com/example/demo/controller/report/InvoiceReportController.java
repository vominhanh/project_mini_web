package com.example.demo.controller.report;

import com.example.demo.service.jasper.InvoiceReportService;
import com.example.demo.service.jasper.InvoiceReportTemplateConfigService;
import com.example.demo.service.strategy.report.InvoiceReportExportStrategy;
import com.example.demo.util.BearerTokenExtractor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/reports/invoice")
public class InvoiceReportController {

    private final InvoiceReportService invoiceReportService;
    private final InvoiceReportTemplateConfigService templateConfigService;
    private final List<InvoiceReportExportStrategy> exportStrategies;

    public InvoiceReportController(
            InvoiceReportService invoiceReportService,
            InvoiceReportTemplateConfigService templateConfigService,
            List<InvoiceReportExportStrategy> exportStrategies) {
        this.invoiceReportService = invoiceReportService;
        this.templateConfigService = templateConfigService;
        this.exportStrategies = exportStrategies;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(name = "format") String format,
            @RequestParam(name = "columns", required = false) List<String> columns
        ) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String accessToken = BearerTokenExtractor.fromAuthorizationHeader(authorization);
        ExportFileMeta export = exportByFormat(format, out, columns, accessToken);
        return buildDownloadResponse(out, export);
    }

    @GetMapping("/config")
    public InvoiceReportConfigResponse getConfig() {
        var cfg = templateConfigService.getCurrentTemplate();
        return new InvoiceReportConfigResponse(cfg.jrxmlName(), cfg.logoName(), cfg.updatedAt().toString());
    }

    public record InvoiceReportConfigResponse(
            String jrxmlFileName,
            String logoFileName,
            String updatedAt) {
    }

    private ExportFileMeta exportByFormat(
            String format,
            ByteArrayOutputStream out,
            List<String> columns,
            String accessToken) throws JRException {
        LocalDate today = LocalDate.now();
        for (InvoiceReportExportStrategy strategy : exportStrategies) {
            if (strategy.supports(format)) {
                strategy.export(invoiceReportService, out, columns, accessToken);
                return new ExportFileMeta(strategy.buildFilename(today), strategy.mediaType());
            }
        }
        throw new IllegalArgumentException("Format must be pdf or xlsx");
    }

    private ResponseEntity<byte[]> buildDownloadResponse(ByteArrayOutputStream out, ExportFileMeta export) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(export.mediaType());
        headers.setContentDisposition(ContentDisposition.attachment().filename(export.filename()).build());
        return ResponseEntity.ok().headers(headers).body(out.toByteArray());
    }

    private record ExportFileMeta(String filename, MediaType mediaType) {
    }
}
