package com.example.demo.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

@Service
@RequiredArgsConstructor
public class InvoiceReportTemplateConfigService {

    private static final int SINGLE_TEMPLATE_ID = 1;
    private static final String DEFAULT_LOGO_ALIAS = "Eximbank_Logo.png";
    private static final String DEFAULT_TEMPLATE_CLASSPATH = "report/Invoice.jrxml";
    private static final String DEFAULT_LOGO_CLASSPATH = "report/Eximbank_Logo.png";
    private static final Set<String> ALLOWED_EXPORT_COLUMNS = Set.of("id", "firstname", "lastname", "email", "username");

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    void init() throws IOException {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS invoice_report_template (
                    id INT PRIMARY KEY,
                    jrxml_name VARCHAR(255) NOT NULL,
                    jrxml_content BYTEA NOT NULL,
                    logo_name VARCHAR(255) NOT NULL,
                    logo_content BYTEA NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice_report_template WHERE id = ?",
                Integer.class,
                SINGLE_TEMPLATE_ID);
        if (count == null || count == 0) {
            upsertTemplate(
                    filenameFromPath(DEFAULT_TEMPLATE_CLASSPATH),
                    readClasspathFile(DEFAULT_TEMPLATE_CLASSPATH),
                    filenameFromPath(DEFAULT_LOGO_CLASSPATH),
                    readClasspathFile(DEFAULT_LOGO_CLASSPATH));
        }
    }

    public ReportTemplateSnapshot getCurrentTemplate() {
        List<ReportTemplateSnapshot> rows = jdbcTemplate.query(
                """
                        SELECT jrxml_name, jrxml_content, logo_name, logo_content, updated_at
                        FROM invoice_report_template
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new ReportTemplateSnapshot(
                        rs.getString("jrxml_name"),
                        rs.getBytes("jrxml_content"),
                        rs.getString("logo_name"),
                        rs.getBytes("logo_content"),
                        rs.getTimestamp("updated_at").toInstant()),
                SINGLE_TEMPLATE_ID);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Khong tim thay cau hinh report trong database.");
        }
        return rows.getFirst();
    }

    public ReportTemplateSnapshot updateTemplate(
            String jrxmlName,
            byte[] jrxmlContent,
            String logoName,
            byte[] logoContent) {
        ReportTemplateSnapshot current = getCurrentTemplate();
        String nextJrxmlName = hasText(jrxmlName) ? jrxmlName.trim() : current.jrxmlName();
        byte[] nextJrxmlContent = nonEmptyBytes(jrxmlContent) ? jrxmlContent : current.jrxmlContent();
        String nextLogoName = hasText(logoName) ? logoName.trim() : current.logoName();
        byte[] nextLogoContent = nonEmptyBytes(logoContent) ? logoContent : current.logoContent();

        upsertTemplate(nextJrxmlName, nextJrxmlContent, nextLogoName, nextLogoContent);
        return getCurrentTemplate();
    }

    public RuntimeTemplate compileCurrentTemplate() throws JRException, IOException {
        return compileCurrentTemplate(null);
    }

    public RuntimeTemplate compileCurrentTemplate(List<String> selectedColumns) throws JRException, IOException {
        ReportTemplateSnapshot current = getCurrentTemplate();
        byte[] effectiveJrxml = filterJrxmlColumns(current.jrxmlContent(), selectedColumns);
        JasperReport compiled;
        try (InputStream in = stripPdfFonts(new ByteArrayInputStream(effectiveJrxml))) {
            compiled = JasperCompileManager.compileReport(in);
        }

        Path reportDir = Files.createTempDirectory("jasper-db-res-");
        Files.write(reportDir.resolve(current.logoName()), current.logoContent());
        if (!DEFAULT_LOGO_ALIAS.equals(current.logoName())) {
            // Backward compatibility: many legacy jrxml templates reference this fixed logo name.
            Files.write(reportDir.resolve(DEFAULT_LOGO_ALIAS), current.logoContent());
        }
        return new RuntimeTemplate(
                compiled,
                normalizeBasePath(reportDir),
                current.jrxmlName(),
                current.logoName(),
                current.updatedAt());
    }

    private static byte[] filterJrxmlColumns(byte[] jrxmlContent, List<String> selectedColumns) throws IOException {
        Set<String> chosen = normalizeSelectedColumns(selectedColumns);
        if (chosen.isEmpty() || chosen.size() == ALLOWED_EXPORT_COLUMNS.size()) {
            return jrxmlContent;
        }

        try {
            String xml = new String(jrxmlContent, StandardCharsets.UTF_8);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

            List<Element> tables = descendantsByLocalName(doc.getDocumentElement(), "table");
            if (tables.isEmpty()) {
                return jrxmlContent;
            }

            boolean filteredAnyTable = false;
            for (Element table : tables) {
                List<Element> columns = childElementsByLocalName(table, "column");
                if (columns.isEmpty()) {
                    continue;
                }

                List<Element> kept = new java.util.ArrayList<>();
                int validColumnsFound = 0;
                int validColumnsTotalWidth = 0;
                for (Element col : columns) {
                    String columnKey = extractColumnKey(col);
                    if (ALLOWED_EXPORT_COLUMNS.contains(columnKey)) {
                        validColumnsFound++;
                        validColumnsTotalWidth += parseWidth(col.getAttribute("width"));
                        if (chosen.contains(columnKey)) {
                            kept.add(col);
                        }
                    }
                }

                if (validColumnsFound == 0 || kept.isEmpty()) {
                    continue;
                }

                for (Element col : columns) {
                    if (!kept.contains(col) && ALLOWED_EXPORT_COLUMNS.contains(extractColumnKey(col))) {
                        table.removeChild(col);
                    }
                }
                rebalanceColumnWidths(kept, validColumnsTotalWidth);
                filteredAnyTable = true;
            }

            if (!filteredAnyTable) {
                return jrxmlContent;
            }

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tf.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (Exception e) {
            throw new IOException("Khong the loc cot report tu JRXML.", e);
        }
    }

    private static Set<String> normalizeSelectedColumns(List<String> selectedColumns) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String c : selectedColumns) {
            if (c == null || c.isBlank()) {
                continue;
            }
            String[] tokens = c.split(",");
            for (String token : tokens) {
                String key = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
                if (ALLOWED_EXPORT_COLUMNS.contains(key)) {
                    normalized.add(key);
                }
            }
        }
        return normalized;
    }

    private static String extractColumnKey(Element columnElement) {
        String fromFieldExpr = extractColumnFieldFromDetail(columnElement);
        if (!fromFieldExpr.isEmpty()) {
            return fromFieldExpr;
        }
        return extractColumnHeader(columnElement);
    }

    private static String extractColumnFieldFromDetail(Element columnElement) {
        Element detailCell = findFirstByLocalName(columnElement, "detailCell");
        if (detailCell == null) {
            return "";
        }
        Element expr = findFirstByLocalName(detailCell, "textFieldExpression");
        if (expr == null) {
            return "";
        }
        String raw = expr.getTextContent();
        if (raw == null) {
            return "";
        }

        String normalized = raw.trim();
        // Match Jasper field expression format like: $F{id}
        int start = normalized.indexOf("$F{");
        if (start < 0) {
            return "";
        }
        int open = normalized.indexOf('{', start);
        int close = normalized.indexOf('}', open + 1);
        if (open < 0 || close < 0 || close <= open + 1) {
            return "";
        }
        return normalized.substring(open + 1, close).trim().toLowerCase(Locale.ROOT);
    }

    private static String extractColumnHeader(Element columnElement) {
        Element columnHeader = findFirstByLocalName(columnElement, "columnHeader");
        if (columnHeader == null) {
            return "";
        }
        Element text = findFirstByLocalName(columnHeader, "text");
        if (text == null) {
            return "";
        }
        String raw = text.getTextContent();
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static void rebalanceColumnWidths(List<Element> keptColumns, int totalWidth) {
        if (totalWidth <= 0) {
            return;
        }

        int count = keptColumns.size();
        int base = totalWidth / count;
        int remainder = totalWidth % count;

        for (int i = 0; i < keptColumns.size(); i++) {
            int targetWidth = base + (i < remainder ? 1 : 0);
            Element col = keptColumns.get(i);
            col.setAttribute("width", String.valueOf(targetWidth));

            List<Element> reportElements = descendantsByLocalName(col, "reportElement");
            for (Element re : reportElements) {
                if (re.hasAttribute("width")) {
                    re.setAttribute("width", String.valueOf(targetWidth));
                }
            }
        }
    }

    private static int parseWidth(String width) {
        if (width == null || width.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(width.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static List<Element> childElementsByLocalName(Element parent, String localName) {
        List<Element> result = new java.util.ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                result.add((Element) child);
            }
            child = child.getNextSibling();
        }
        return result;
    }

    private static List<Element> descendantsByLocalName(Element parent, String localName) {
        List<Element> result = new java.util.ArrayList<>();
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static Element findFirstByLocalName(Element parent, String localName) {
        if (localName.equals(parent.getLocalName())) {
            return parent;
        }
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node.getNodeType() == Node.ELEMENT_NODE ? (Element) node : null;
    }

    private void upsertTemplate(String jrxmlName, byte[] jrxmlContent, String logoName, byte[] logoContent) {
        jdbcTemplate.update(
                """
                        INSERT INTO invoice_report_template(id, jrxml_name, jrxml_content, logo_name, logo_content, updated_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (id)
                        DO UPDATE SET
                            jrxml_name = EXCLUDED.jrxml_name,
                            jrxml_content = EXCLUDED.jrxml_content,
                            logo_name = EXCLUDED.logo_name,
                            logo_content = EXCLUDED.logo_content,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                SINGLE_TEMPLATE_ID, jrxmlName, jrxmlContent, logoName, logoContent);
    }

    private byte[] readClasspathFile(String location) throws IOException {
        ClassPathResource resource = new ClassPathResource(location.trim());
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static InputStream stripPdfFonts(InputStream in) throws IOException {
        String s = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\s+pdfFontName=\"[^\"]*\"", "")
                .replace("fontName=\"DejaVu Serif\"", "fontName=\"DejaVu Sans\"");
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean nonEmptyBytes(byte[] value) {
        return value != null && value.length > 0;
    }

    private static String filenameFromPath(String path) {
        if (!hasText(path)) {
            return "unknown";
        }
        String normalized = path.trim().replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    private static String normalizeBasePath(Path reportDir) {
        String p = reportDir.toAbsolutePath().normalize().toString().replace('\\', '/');
        return p.endsWith("/") ? p : p + '/';
    }

    public record ReportTemplateSnapshot(
            String jrxmlName,
            byte[] jrxmlContent,
            String logoName,
            byte[] logoContent,
            Instant updatedAt) {
    }

    public record RuntimeTemplate(
            JasperReport compiledReport,
            String repositoryBasePath,
            String jrxmlName,
            String logoName,
            Instant updatedAt) {
    }
}
