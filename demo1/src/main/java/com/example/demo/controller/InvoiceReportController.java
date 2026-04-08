package com.example.demo.controller;

import com.example.demo.service.InvoiceReportService;
import com.example.demo.service.InvoiceReportTemplateConfigService;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@RestController
@RequestMapping("/api/reports/invoice")
public class InvoiceReportController {
    private static final String FORMAT_PDF = "pdf";
    private static final String FORMAT_XLSX = "xlsx";
    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String FILENAME_PREFIX = "facepay-auth-log-";
    private static final String BEARER_PREFIX = "Bearer ";

    private final InvoiceReportService invoiceReportService;
    private final InvoiceReportTemplateConfigService templateConfigService;


    public InvoiceReportController(
            InvoiceReportService invoiceReportService,
            InvoiceReportTemplateConfigService templateConfigService) {
        this.invoiceReportService = invoiceReportService;
        this.templateConfigService = templateConfigService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(name = "format") String format,
            @RequestParam(name = "columns", required = false) List<String> columns
        ) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String accessToken = extractBearerToken(authorization);
        ExportFileMeta export = exportByFormat(format, out, columns, accessToken);
        return buildDownloadResponse(out, export);
    }

    @GetMapping("/config")
    public InvoiceReportConfigResponse getConfig() {
        var cfg = templateConfigService.getCurrentTemplate();
        return new InvoiceReportConfigResponse(cfg.jrxmlName(), cfg.logoName(), cfg.updatedAt().toString());
    }

    @PostMapping(value = "/config", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceReportConfigResponse updateConfig(
            @RequestPart(value = "jrxmlFile", required = false) MultipartFile jrxmlFile,
            @RequestPart(value = "logoFile", required = false) MultipartFile logoFile) throws IOException {
        validateConfigUpload(jrxmlFile, logoFile);

        var updated = templateConfigService.updateTemplate(
                jrxmlFile != null ? jrxmlFile.getOriginalFilename() : null,
                jrxmlFile != null ? jrxmlFile.getBytes() : null,
                logoFile != null ? logoFile.getOriginalFilename() : null,
                logoFile != null ? logoFile.getBytes() : null);
        return new InvoiceReportConfigResponse(
                updated.jrxmlName(),
                updated.logoName(),
                updated.updatedAt().toString());
    }

    public record InvoiceReportConfigResponse(
            String jrxmlFileName,
            String logoFileName,
            String updatedAt) {
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            return "";
        }
        if (authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return authorization.trim();
    }

    private ExportFileMeta exportByFormat(
            String format,
            ByteArrayOutputStream out,
            List<String> columns,
            String accessToken) throws JRException {
        if (FORMAT_PDF.equalsIgnoreCase(format)) {
            invoiceReportService.exportPdf(out, columns, accessToken);
            return new ExportFileMeta(
                    FILENAME_PREFIX + LocalDate.now() + "." + FORMAT_PDF,
                    MediaType.APPLICATION_PDF);
        }
        if (FORMAT_XLSX.equalsIgnoreCase(format)) {
            invoiceReportService.exportXlsx(out, columns, accessToken);
            return new ExportFileMeta(
                    FILENAME_PREFIX + LocalDate.now() + "." + FORMAT_XLSX,
                    MediaType.parseMediaType(XLSX_MEDIA_TYPE));
        }
        throw new IllegalArgumentException("Format must be pdf or xlsx");
    }

    private ResponseEntity<byte[]> buildDownloadResponse(ByteArrayOutputStream out, ExportFileMeta export) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(export.mediaType());
        headers.setContentDisposition(ContentDisposition.attachment().filename(export.filename()).build());
        return ResponseEntity.ok().headers(headers).body(out.toByteArray());
    }

    private void validateConfigUpload(MultipartFile jrxmlFile, MultipartFile logoFile) {
        if (isEmpty(jrxmlFile) && isEmpty(logoFile)) {
            throw new ResponseStatusException(BAD_REQUEST, "Can upload it nhat 1 file (jrxml hoac logo).");
        }
        validateJrxmlFile(jrxmlFile);
        validateLogoFile(logoFile);
    }

    private void validateJrxmlFile(MultipartFile jrxmlFile) {
        if (isEmpty(jrxmlFile)) {
            return;
        }
        String originalName = jrxmlFile.getOriginalFilename();
        if (originalName == null || !originalName.endsWith(".jrxml")) {
            throw new ResponseStatusException(BAD_REQUEST, "File mau report phai co duoi .jrxml");
        }
    }

    private void validateLogoFile(MultipartFile logoFile) {
        if (isEmpty(logoFile)) {
            return;
        }
        String originalName = logoFile.getOriginalFilename();
        if (originalName != null && !originalName.matches("(?i).+\\.(png|jpg|jpeg)$")) {
            throw new ResponseStatusException(BAD_REQUEST, "Logo chi ho tro png/jpg/jpeg");
        }
    }

    private boolean isEmpty(MultipartFile file) {
        return file == null || file.isEmpty();
    }

    private record ExportFileMeta(String filename, MediaType mediaType) {
    }
}
