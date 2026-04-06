package com.myproject.spring_boot_data.controller;

import com.myproject.spring_boot_data.service.InvoiceReportService;
import net.sf.jasperreports.engine.JRException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/invoice")
public class InvoiceReportController {

    private final InvoiceReportService invoiceReportService;

    public InvoiceReportController(InvoiceReportService invoiceReportService) {
        this.invoiceReportService = invoiceReportService;
    }


    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf() throws JRException, SQLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        invoiceReportService.exportPdf(out);
        String filename = "facepay-auth-log-" + LocalDate.now() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(out.toByteArray());
    }


    @GetMapping(
            value = "/xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportXlsx() throws JRException, SQLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        invoiceReportService.exportXlsx(out);
        String filename = "facepay-auth-log-" + LocalDate.now() + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(out.toByteArray());
    }
}
