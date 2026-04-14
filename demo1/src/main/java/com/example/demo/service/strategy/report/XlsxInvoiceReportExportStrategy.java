package com.example.demo.service.strategy.report;

import com.example.demo.constant.report.InvoiceReportExportConstants;
import com.example.demo.service.jasper.InvoiceReportService;
import net.sf.jasperreports.engine.JRException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;

@Component
public class XlsxInvoiceReportExportStrategy implements InvoiceReportExportStrategy {

    @Override
    public boolean supports(String format) {
        return InvoiceReportExportConstants.FORMAT_XLSX.equalsIgnoreCase(format);
    }

    @Override
    public void export(InvoiceReportService invoiceReportService, OutputStream out, List<String> columns, String accessToken)
            throws JRException {
        invoiceReportService.exportXlsx(out, columns, accessToken);
    }

    @Override
    public String buildFilename(LocalDate date) {
        return InvoiceReportExportConstants.FILENAME_PREFIX + date + "." + InvoiceReportExportConstants.FORMAT_XLSX;
    }

    @Override
    public MediaType mediaType() {
        return MediaType.parseMediaType(InvoiceReportExportConstants.XLSX_MEDIA_TYPE);
    }
}
