package com.example.demo.service.jasper;

import com.example.demo.dto.response.RuntimeTemplate;
import com.example.demo.entity.Room;
import com.example.demo.repository.RoomRepository;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceReportService {

    private static final String TABLE_DATA_SOURCE_PARAM = "TABLE_DATA_SOURCE";
    private static final String TEMPLATE_DATA_SOURCE_PARAM = "TEMPLATE_DATA_SOURCE";
    private static final String USER_ROLE_ADMIN = "admin";
    private static final String USER_ROLE_DEFAULT = "user";
    private static final Set<String> USER_COLUMNS = Set.of("id", "firstname", "lastname", "email", "username", "role");
    private static final Set<String> ROOM_COLUMNS = Set.of("id", "name", "price", "capacity", "city");

    private final RemoteFederationAuthService remoteFederationAuthService;
    private final InvoiceReportTemplateConfigService templateConfigService;
    private final RoomRepository roomRepository;

    private JasperPrint fill(List<String> selectedColumns, String accessToken) throws JRException {
        final RuntimeTemplate runtime;
        try {
            runtime = templateConfigService.compileCurrentTemplate(selectedColumns);
        } catch (IOException e) {
            throw new JRException("Khong the tai report template.", e);
        }

        Map<String, Object> parameters = buildReportParameters(loadUsers(accessToken));

        JasperReportsContext ctx = DefaultJasperReportsContext.getInstance();
        var rc = SimpleRepositoryResourceContext.of(runtime.repositoryBasePath());
        return JRFiller.fill(
                ctx,
                SimpleJasperReportSource.from(runtime.compiledReport(), null, rc),
                parameters,
                new JREmptyDataSource(1));
    }

    private Map<String, Object> buildReportParameters(List<Map<String, Object>> users) {
        Map<String, Object> parameters = new HashMap<>();
        List<Map<String, ?>> reportRows = toReportRows(users);
        parameters.put(TABLE_DATA_SOURCE_PARAM, new JRMapCollectionDataSource(reportRows));
        parameters.put(TEMPLATE_DATA_SOURCE_PARAM, new JRBeanCollectionDataSource(loadRoomsForReport()));
        return parameters;
    }

    private List<Room> loadRoomsForReport() {
        return roomRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    private List<Map<String, Object>> loadUsers(String accessToken) {
        List<Map<String, Object>> users = remoteFederationAuthService.getAllUsers(accessToken);
        return users.isEmpty() ? remoteFederationAuthService.getReportUsers(accessToken) : users;
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

    public ReportPreviewData preview(List<String> selectedColumns, String accessToken) {
        ColumnSelection selection = normalizeSelection(selectedColumns);
        List<Map<String, Object>> userRows = toObjectRows(toReportRows(loadUsers(accessToken)))
                .stream()
                .map(row -> filterRowByColumns(row, selection.userColumns(), USER_COLUMNS))
                .toList();
        List<Map<String, Object>> roomRows = loadRoomsForReport()
                .stream()
                .map(this::toTemplateRow)
                .map(row -> filterRowByColumns(row, selection.roomColumns(), ROOM_COLUMNS))
                .toList();
        return new ReportPreviewData(
                new ArrayList<>(selection.userColumns().isEmpty() ? USER_COLUMNS : selection.userColumns()),
                new ArrayList<>(selection.roomColumns().isEmpty() ? ROOM_COLUMNS : selection.roomColumns()),
                userRows,
                roomRows
        );
    }

    private List<Map<String, ?>> toReportRows(List<Map<String, Object>> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<Map<String, ?>> rows = new java.util.ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            rows.add(toReportRow(users.get(i), i));
        }
        return rows;
    }

    private List<Map<String, Object>> toObjectRows(List<Map<String, ?>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, ?> row : rows) {
            result.add(new LinkedHashMap<>(row));
        }
        return result;
    }

    private Map<String, Object> toReportRow(Map<String, Object> user, int index) {
        String[] nameParts = resolveName(user);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", safeLong(firstNonBlank(user.get("id"), user.get("userId")), (long) index + 1));
        row.put("firstname", nameParts[0]);
        row.put("lastname", nameParts[1]);
        String email = firstNonBlank(user.get("email"));
        row.put("email", email);
        row.put("username", firstNonBlank(user.get("username"), user.get("userName"), email));
        row.put("role", normalizeExportRole(user.get("role")));
        return row;
    }

    private Map<String, Object> toTemplateRow(Room room) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", room.getId());
        row.put("name", room.getName());
        row.put("price", room.getPrice());
        row.put("capacity", room.getCapacity());
        row.put("city", room.getCity());
        return row;
    }

    private Map<String, Object> filterRowByColumns(Map<String, Object> source, Set<String> selected, Set<String> allowed) {
        Set<String> effective = selected == null || selected.isEmpty() ? allowed : selected;
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String col : effective) {
            filtered.put(col, source.get(col));
        }
        return filtered;
    }

    private ColumnSelection normalizeSelection(List<String> selectedColumns) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            return new ColumnSelection(Set.of(), Set.of());
        }
        Set<String> user = new LinkedHashSet<>();
        Set<String> room = new LinkedHashSet<>();
        for (String item : selectedColumns) {
            if (item == null || item.isBlank()) {
                continue;
            }
            for (String token : item.split(",")) {
                String raw = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
                if (raw.isBlank()) {
                    continue;
                }
                if (raw.startsWith("user:") || raw.startsWith("table:")) {
                    String col = raw.substring(raw.indexOf(':') + 1).trim();
                    if (USER_COLUMNS.contains(col)) {
                        user.add(col);
                    }
                    continue;
                }
                if (raw.startsWith("room:") || raw.startsWith("template:")) {
                    String col = raw.substring(raw.indexOf(':') + 1).trim();
                    if (ROOM_COLUMNS.contains(col)) {
                        room.add(col);
                    }
                    continue;
                }
                if (USER_COLUMNS.contains(raw)) {
                    user.add(raw);
                } else if (ROOM_COLUMNS.contains(raw)) {
                    room.add(raw);
                }
            }
        }
        return new ColumnSelection(user, room);
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

    private record ColumnSelection(Set<String> userColumns, Set<String> roomColumns) {
    }

    public record ReportPreviewData(
            List<String> userColumns,
            List<String> roomColumns,
            List<Map<String, Object>> userRows,
            List<Map<String, Object>> roomRows) {
    }

}
