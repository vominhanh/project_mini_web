package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.fill.JRFiller;
import net.sf.jasperreports.engine.fill.SimpleJasperReportSource;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import net.sf.jasperreports.repo.SimpleRepositoryResourceContext;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static net.sf.jasperreports.engine.JRParameter.REPORT_CONNECTION;

@Service
@RequiredArgsConstructor
public class InvoiceReportService {

    private final DataSource dataSource;
    private final InvoiceReportTemplateConfigService templateConfigService;

    private JasperPrint fill(Connection conn, List<String> selectedColumns) throws JRException {
        final InvoiceReportTemplateConfigService.RuntimeTemplate runtime;
        try {
            runtime = templateConfigService.compileCurrentTemplate(selectedColumns);
        } catch (IOException e) {
            throw new JRException("Khong the tai report template tu database.", e);
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(REPORT_CONNECTION, conn);
        JasperReportsContext ctx = DefaultJasperReportsContext.getInstance();
        var rc = SimpleRepositoryResourceContext.of(runtime.repositoryBasePath());
        return JRFiller.fill(
                ctx,
                SimpleJasperReportSource.from(runtime.compiledReport(), null, rc),
                parameters,
                conn);
    }

    public void exportPdf(OutputStream out, List<String> selectedColumns) throws JRException, SQLException {
        try (Connection c = dataSource.getConnection()) {
            JasperExportManager.exportReportToPdfStream(fill(c, selectedColumns), out);
        }
    }

    public void exportXlsx(OutputStream out, List<String> selectedColumns) throws JRException, SQLException {
        try (Connection c = dataSource.getConnection()) {
            var x = new JRXlsxExporter();
            x.setExporterInput(new SimpleExporterInput(fill(c, selectedColumns)));
            x.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            var cfg = new SimpleXlsxReportConfiguration();
            cfg.setDetectCellType(true);
            cfg.setCollapseRowSpan(false);
            x.setConfiguration(cfg);
            x.exportReport();
        }
    }
}
