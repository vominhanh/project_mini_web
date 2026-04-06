package com.example.demo.controller;

import com.example.demo.service.InvoiceReportService;
import com.example.demo.service.InvoiceReportTemplateConfigService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/reports/invoice")
public class InvoiceReportController {

    private final InvoiceReportService invoiceReportService;
    private final InvoiceReportTemplateConfigService templateConfigService;

    public InvoiceReportController(
            InvoiceReportService invoiceReportService,
            InvoiceReportTemplateConfigService templateConfigService) {
        this.invoiceReportService = invoiceReportService;
        this.templateConfigService = templateConfigService;
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@RequestParam(name = "columns", required = false) List<String> columns)
            throws JRException, SQLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        invoiceReportService.exportPdf(out, columns);
        String filename = "facepay-auth-log-" + LocalDate.now() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(out.toByteArray());
    }

    @GetMapping(value = "/xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportXlsx(@RequestParam(name = "columns", required = false) List<String> columns)
            throws JRException, SQLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        invoiceReportService.exportXlsx(out, columns);
        String filename = "facepay-auth-log-" + LocalDate.now() + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(out.toByteArray());
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
        if ((jrxmlFile == null || jrxmlFile.isEmpty()) && (logoFile == null || logoFile.isEmpty())) {
            throw new ResponseStatusException(BAD_REQUEST, "Can upload it nhat 1 file (jrxml hoac logo).");
        }

        if (jrxmlFile != null && !jrxmlFile.isEmpty()
                && (jrxmlFile.getOriginalFilename() == null || !jrxmlFile.getOriginalFilename().endsWith(".jrxml"))) {
            throw new ResponseStatusException(BAD_REQUEST, "File mau report phai co duoi .jrxml");
        }
        if (logoFile != null && !logoFile.isEmpty() && logoFile.getOriginalFilename() != null
                && !logoFile.getOriginalFilename().matches("(?i).+\\.(png|jpg|jpeg)$")) {
            throw new ResponseStatusException(BAD_REQUEST, "Logo chi ho tro png/jpg/jpeg");
        }

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
}
