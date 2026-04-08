package com.example.demo.service;

import com.example.demo.controller.dto.ReportUserRow;
import com.example.demo.controller.dto.RuntimeTemplate;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.fill.JRFiller;
import net.sf.jasperreports.engine.fill.SimpleJasperReportSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import net.sf.jasperreports.repo.SimpleRepositoryResourceContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceReportService {

    private static final String TABLE_DATA_SOURCE_PARAM = "TABLE_DATA_SOURCE";

    private final RemoteFederationAuthService remoteFederationAuthService;
    private final InvoiceReportTemplateConfigService templateConfigService;

    private JasperPrint fill(List<String> selectedColumns, String accessToken) throws JRException {
        final RuntimeTemplate runtime;
        try {
            runtime = templateConfigService.compileCurrentTemplate(selectedColumns);
        } catch (IOException e) {
            throw new JRException("Khong the tai report template tu database.", e);
        }

        List<Map<String, Object>> reportUsers = remoteFederationAuthService.getAllUsers(accessToken);
        if (reportUsers.isEmpty()) {
            reportUsers = remoteFederationAuthService.getReportUsers(accessToken);
        }
        List<ReportUserRow> rows = toReportRows(reportUsers);
        JRDataSource tableDataSource = new JRBeanCollectionDataSource(rows);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put(TABLE_DATA_SOURCE_PARAM, tableDataSource);

        JasperReportsContext ctx = DefaultJasperReportsContext.getInstance();
        var rc = SimpleRepositoryResourceContext.of(runtime.repositoryBasePath());
        return JRFiller.fill(
                ctx,
                SimpleJasperReportSource.from(runtime.compiledReport(), null, rc),
                parameters,
                new JREmptyDataSource(1));
    }

    public void exportPdf(OutputStream out, List<String> selectedColumns, String accessToken) throws JRException {
        JasperExportManager.exportReportToPdfStream(fill(selectedColumns, accessToken), out);
    }

    public void exportXlsx(OutputStream out, List<String> selectedColumns, String accessToken) throws JRException {
        var x = new JRXlsxExporter();
        x.setExporterInput(new SimpleExporterInput(fill(selectedColumns, accessToken)));
        x.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        var cfg = new SimpleXlsxReportConfiguration();
        cfg.setDetectCellType(true);
        cfg.setCollapseRowSpan(false);
        x.setConfiguration(cfg);
        x.exportReport();
    }

    private List<ReportUserRow> toReportRows(List<Map<String, Object>> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<ReportUserRow> rows = new ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            Map<String, Object> user = users.get(i);
            String idText = firstNonBlank(
                    user.get("id"),
                    user.get("userId"));
            Long numericId = safeLong(idText, (long) i + 1);

            String firstName = firstNonBlank(
                    user.get("firstname"),
                    user.get("firstName"));
            String lastName = firstNonBlank(
                    user.get("lastname"),
                    user.get("lastName"));

            if (firstName.isBlank() && lastName.isBlank()) {
                String fullName = firstNonBlank(user.get("name"));
                if (!fullName.isBlank()) {
                    String[] parts = fullName.trim().split("\\s+", 2);
                    firstName = parts[0];
                    if (parts.length > 1) {
                        lastName = parts[1];
                    }
                }
            }

            rows.add(new ReportUserRow(
                    numericId,
                    firstName,
                    lastName,
                    firstNonBlank(user.get("email")),
                    firstNonBlank(user.get("username")),
                    normalizeExportRole(user.get("role"))
            ));
        }
        return rows;
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static Long safeLong(String text, Long fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String normalizeExportRole(Object rawRole) {
        String value = rawRole == null ? "" : rawRole.toString().trim().toLowerCase(Locale.ROOT);
        if (value.contains("admin")) {
            return "admin";
        }
        return "user";
    }
}
