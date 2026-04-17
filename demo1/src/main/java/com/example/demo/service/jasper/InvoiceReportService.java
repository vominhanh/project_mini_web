package com.example.demo.service.jasper;

import com.example.demo.dto.response.RuntimeTemplate;
import com.example.demo.dto.response.UserSummaryDto;
import com.example.demo.service.RemoteFederationAuthService;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.fill.JRFiller;
import net.sf.jasperreports.engine.fill.SimpleJasperReportSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import net.sf.jasperreports.repo.SimpleRepositoryResourceContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceReportService {

    private static final String TABLE_DATA_SOURCE_PARAM = "TABLE_DATA_SOURCE";
    private static final String TEMPLATE_DATA_SOURCE_PARAM = "TEMPLATE_DATA_SOURCE";
    private static final String USER_ROLE_ADMIN = "admin";
    private static final String USER_ROLE_DEFAULT = "user";

    private final RemoteFederationAuthService remoteFederationAuthService;
    private final InvoiceReportTemplateConfigService templateConfigService;

    private JasperPrint fill(List<String> selectedColumns, String accessToken) throws JRException {
        final RuntimeTemplate runtime;
        try {
            runtime = templateConfigService.compileCurrentTemplate(selectedColumns);
        } catch (IOException e) {
            throw new JRException("Khong the tai report template.", e);
        }

        Map<String, Object> parameters = buildReportParameters(runtime, loadUsers(accessToken));

        JasperReportsContext ctx = DefaultJasperReportsContext.getInstance();
        var rc = SimpleRepositoryResourceContext.of(runtime.repositoryBasePath());
        return JRFiller.fill(
                ctx,
                SimpleJasperReportSource.from(runtime.compiledReport(), null, rc),
                parameters,
                new JREmptyDataSource(1));
    }

    private Map<String, Object> buildReportParameters(RuntimeTemplate runtime, List<Map<String, Object>> users) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(TABLE_DATA_SOURCE_PARAM, new JRBeanCollectionDataSource(toReportRows(users)));
        parameters.put(TEMPLATE_DATA_SOURCE_PARAM, new JRMapCollectionDataSource(List.of(toTemplateRowMap(runtime))));
        return parameters;
    }

    private List<Map<String, Object>> loadUsers(String accessToken) {
        List<Map<String, Object>> users = remoteFederationAuthService.getAllUsers(accessToken);
        return users.isEmpty() ? remoteFederationAuthService.getReportUsers(accessToken) : users;
    }

    private Map<String, Object> toTemplateRowMap(RuntimeTemplate runtime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jrxml_name", safeText(runtime.jrxmlName()));
        row.put("jrxml_content", "loaded");
        row.put("logo_name", safeText(runtime.logoName()));
        row.put("logo_content", "loaded");
        row.put("updated_at", runtime.updatedAt() == null ? "" : runtime.updatedAt().toString());
        return row;
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

    private List<UserSummaryDto> toReportRows(List<Map<String, Object>> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<UserSummaryDto> rows = new ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            rows.add(toReportRow(users.get(i), i));
        }
        return rows;
    }

    private UserSummaryDto toReportRow(Map<String, Object> user, int index) {
        String[] nameParts = resolveName(user);
        UserSummaryDto row = new UserSummaryDto();
        row.setId(safeLong(firstNonBlank(user.get("id"), user.get("userId")), (long) index + 1));
        row.setFirstname(nameParts[0]);
        row.setLastname(nameParts[1]);
        row.setEmail(firstNonBlank(user.get("email")));
        row.setRole(normalizeExportRole(user.get("role")));
        return row;
    }

    private String[] resolveName(Map<String, Object> user) {
        String firstName = firstNonBlank(user.get("firstname"), user.get("firstName"));
        String lastName = firstNonBlank(user.get("lastname"), user.get("lastName"));
        if (!firstName.isBlank() || !lastName.isBlank()) {
            return new String[]{firstName, lastName};
        }

        String fullName = firstNonBlank(user.get("name"));
        if (fullName.isBlank()) {
            return new String[]{"", ""};
        }

        String[] parts = fullName.trim().split("\\s+", 2);
        return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
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
        return value.contains(USER_ROLE_ADMIN) ? USER_ROLE_ADMIN : USER_ROLE_DEFAULT;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

}
