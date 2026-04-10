package com.example.demo.service.strategy.report;

import com.example.demo.constant.InvoiceReportExportConstants;
import com.example.demo.service.InvoiceReportService;
import net.sf.jasperreports.engine.JRException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;

@Component
public class PdfInvoiceReportExportStrategy implements InvoiceReportExportStrategy {

    @Override
    public boolean supports(String format) {
        return InvoiceReportExportConstants.FORMAT_PDF.equalsIgnoreCase(format);
    }

    @Override
    public void export(InvoiceReportService invoiceReportService, OutputStream out, List<String> columns, String accessToken)
            throws JRException {
        invoiceReportService.exportPdf(out, columns, accessToken);
    }

    @Override
    public String buildFilename(LocalDate date) {
        return InvoiceReportExportConstants.FILENAME_PREFIX + date + "." + InvoiceReportExportConstants.FORMAT_PDF;
    }

    @Override
    public MediaType mediaType() {
        return MediaType.APPLICATION_PDF;
    }
}
