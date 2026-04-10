package com.example.demo.service;

import com.example.demo.dto.RuntimeTemplate;
import com.example.demo.entity.InvoiceReportTemplate;
import com.example.demo.repository.InvoiceReportTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.core.io.ClassPathResource;
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

    private static final String DEFAULT_LOGO_ALIAS = "Eximbank_Logo.png";
    private static final String DEFAULT_TEMPLATE_CLASSPATH = "report/Invoice.jrxml";
    private static final String DEFAULT_LOGO_CLASSPATH = "report/Eximbank_Logo.png";
    private static final Set<String> ALLOWED_EXPORT_COLUMNS = Set.of("id", "firstname", "lastname", "email", "username", "role");

    private final InvoiceReportTemplateRepository invoiceReportTemplateRepository;

    @PostConstruct
    void init() throws IOException {
        invoiceReportTemplateRepository.ensureSchema();

        if (invoiceReportTemplateRepository.countById(InvoiceReportTemplateRepository.SINGLE_TEMPLATE_ID) == 0) {
            upsertTemplate(
                    filenameFromPath(DEFAULT_TEMPLATE_CLASSPATH),
                    readClasspathFile(DEFAULT_TEMPLATE_CLASSPATH),
                    filenameFromPath(DEFAULT_LOGO_CLASSPATH),
                    readClasspathFile(DEFAULT_LOGO_CLASSPATH));
        }
    }

    public InvoiceReportTemplate getCurrentTemplate() {
        return invoiceReportTemplateRepository.findById(InvoiceReportTemplateRepository.SINGLE_TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay cau hinh report trong database."));
    }

    public InvoiceReportTemplate updateTemplate(
            String jrxmlName,
            byte[] jrxmlContent,
            String logoName,
            byte[] logoContent) {
        InvoiceReportTemplate current = getCurrentTemplate();
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
        InvoiceReportTemplate current = getCurrentTemplate();
        byte[] effectiveJrxml = filterJrxmlColumns(current.jrxmlContent(), selectedColumns);
        effectiveJrxml = normalizeForBeanDataSource(effectiveJrxml);
        effectiveJrxml = ensureTemplateDataSourceParameterFallback(effectiveJrxml);
        JasperReport compiled;
        try (InputStream in = stripPdfFonts(new ByteArrayInputStream(effectiveJrxml))) {
            compiled = JasperCompileManager.compileReport(in);
        }

        Path reportDir = Files.createTempDirectory("jasper-db-res-");
        Files.write(reportDir.resolve(current.logoName()), current.logoContent());
        if (!DEFAULT_LOGO_ALIAS.equals(current.logoName())) {
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

    private static byte[] normalizeForBeanDataSource(byte[] jrxmlContent) throws IOException {
        try {
            String xml = new String(jrxmlContent, StandardCharsets.UTF_8);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

            Element root = doc.getDocumentElement();

            removeReportDataSourceParametersEverywhere(root);
            ensureRootTableDataSourceParameter(root);
            ensureRootTemplateDataSourceParameter(root);

            removeQueryStringsEverywhere(root);
            
            replaceConnectionExpressionsWithDataSource(root);
            normalizeDatasetRunDataSourceExpressions(root);
            normalizeSummaryTableLayout(root);

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tf.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (Exception e) {
            throw new IOException("Khong the chuyen report sang che do data source tu BE.", e);
        }
    }

    private static byte[] ensureTemplateDataSourceParameterFallback(byte[] jrxmlContent) {
        if (jrxmlContent == null || jrxmlContent.length == 0) {
            return jrxmlContent;
        }

        String xml = new String(jrxmlContent, StandardCharsets.UTF_8);
        if (!xml.contains("$P{TEMPLATE_DATA_SOURCE}")) {
            return jrxmlContent;
        }
        if (xml.contains("name=\"TEMPLATE_DATA_SOURCE\"")) {
            return jrxmlContent;
        }

        String parameter = "\n\t<parameter name=\"TEMPLATE_DATA_SOURCE\" class=\"net.sf.jasperreports.engine.JRDataSource\"/>\n";
        int insertAt = xml.indexOf("<queryString");
        if (insertAt < 0) {
            insertAt = xml.indexOf("<field ");
        }
        if (insertAt < 0) {
            return jrxmlContent;
        }

        String patched = xml.substring(0, insertAt) + parameter + xml.substring(insertAt);
        return patched.getBytes(StandardCharsets.UTF_8);
    }

    private static void removeReportDataSourceParametersEverywhere(Element root) {
        List<Element> rootParams = childElementsByLocalName(root, "parameter");
        for (Element p : rootParams) {
            if (!"REPORT_DATA_SOURCE".equals(p.getAttribute("name"))) {
                continue;
            }
            Node parent = p.getParentNode();
            if (parent != null) {
                parent.removeChild(p);
            }
        }

        // Xoa o subDataset scope
        List<Element> subDatasets = descendantsByLocalName(root, "subDataset");
        for (Element sub : subDatasets) {
            List<Element> directParams = childElementsByLocalName(sub, "parameter");
            for (Element p : directParams) {
                if (!"REPORT_DATA_SOURCE".equals(p.getAttribute("name"))) {
                    continue;
                }
                Node parent = p.getParentNode();
                if (parent != null) {
                    parent.removeChild(p);
                }
            }
        }
    }

    private static void removeQueryStringsEverywhere(Element root) {
        // Root dataset
        List<Element> rootQuery = childElementsByLocalName(root, "queryString");
        for (Element q : rootQuery) {
            Node parent = q.getParentNode();
            if (parent != null) {
                parent.removeChild(q);
            }
        }

        // Sub datasets
        List<Element> subDatasets = descendantsByLocalName(root, "subDataset");
        for (Element sub : subDatasets) {
            List<Element> qs = childElementsByLocalName(sub, "queryString");
            for (Element q : qs) {
                Node parent = q.getParentNode();
                if (parent != null) {
                    parent.removeChild(q);
                }
            }
        }
    }

    private static void ensureRootTableDataSourceParameter(Element root) {
        final String paramName = "TABLE_DATA_SOURCE";
        List<Element> directParams = childElementsByLocalName(root, "parameter");
        for (Element p : directParams) {
            if (!paramName.equals(p.getAttribute("name"))) {
                continue;
            }
            p.setAttribute("class", "net.sf.jasperreports.engine.JRDataSource");
            return;
        }

        Document doc = root.getOwnerDocument();
        Element param = doc.createElementNS(root.getNamespaceURI(), "parameter");
        param.setAttribute("name", paramName);
        param.setAttribute("class", "net.sf.jasperreports.engine.JRDataSource");
        Node insertionPoint = findRootParameterInsertionPoint(root);
        if (insertionPoint != null) {
            root.insertBefore(param, insertionPoint);
        } else {
            root.appendChild(param);
        }
    }

    private static void ensureRootTemplateDataSourceParameter(Element root) {
        final String paramName = "TEMPLATE_DATA_SOURCE";
        List<Element> directParams = childElementsByLocalName(root, "parameter");
        for (Element p : directParams) {
            if (!paramName.equals(p.getAttribute("name"))) {
                continue;
            }
            p.setAttribute("class", "net.sf.jasperreports.engine.JRDataSource");
            return;
        }

        Document doc = root.getOwnerDocument();
        Element param = doc.createElementNS(root.getNamespaceURI(), "parameter");
        param.setAttribute("name", paramName);
        param.setAttribute("class", "net.sf.jasperreports.engine.JRDataSource");
        Node insertionPoint = findRootParameterInsertionPoint(root);
        if (insertionPoint != null) {
            root.insertBefore(param, insertionPoint);
        } else {
            root.appendChild(param);
        }
    }

    private static Node findRootParameterInsertionPoint(Element root) {
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (isElementAnyOf(
                        child,
                        "queryString",
                        "field",
                        "sortField",
                        "variable",
                        "filterExpression",
                        "group",
                        "background",
                        "title",
                        "pageHeader",
                        "columnHeader",
                        "detail",
                        "columnFooter",
                        "pageFooter",
                        "lastPageFooter",
                        "summary",
                        "noData")) {
                    return child;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static void replaceConnectionExpressionsWithDataSource(Element root) {
        List<Element> datasetRuns = descendantsByLocalName(root, "datasetRun");
        for (Element datasetRun : datasetRuns) {
            Element connectionExpression = findFirstByLocalName(datasetRun, "connectionExpression");
            if (connectionExpression == null) {
                continue;
            }

            Element dataSourceExpression = datasetRun.getOwnerDocument().createElementNS(root.getNamespaceURI(), "dataSourceExpression");
            dataSourceExpression.setTextContent("$P{REPORT_DATA_SOURCE}");
            datasetRun.replaceChild(dataSourceExpression, connectionExpression);
        }
    }

    private static void normalizeDatasetRunDataSourceExpressions(Element root) {
 
        List<Element> datasetRuns = descendantsByLocalName(root, "datasetRun");
        for (Element datasetRun : datasetRuns) {
            Element dse = findFirstByLocalName(datasetRun, "dataSourceExpression");
            if (dse == null) {
                continue;
            }
            String text = dse.getTextContent() == null ? "" : dse.getTextContent().trim();
            String subDataset = datasetRun.getAttribute("subDataset");
            if ("Dataset3".equals(subDataset)) {
                if ("$P{REPORT_DATA_SOURCE}".equals(text) || text.contains("TEMPLATE_DATA_SOURCE")) {
                    dse.setTextContent("((net.sf.jasperreports.engine.JRDataSource)$P{REPORT_PARAMETERS_MAP}.get(\"TEMPLATE_DATA_SOURCE\"))");
                }
            } else if ("$P{REPORT_DATA_SOURCE}".equals(text)) {
                dse.setTextContent("$P{TABLE_DATA_SOURCE}");
            }
        }
    }

    private static void normalizeSummaryTableLayout(Element root) {
        List<Element> summarySections = childElementsByLocalName(root, "summary");
        if (summarySections.isEmpty()) {
            return;
        }
        Element summary = summarySections.getFirst();
        List<Element> bands = childElementsByLocalName(summary, "band");
        if (bands.isEmpty()) {
            return;
        }

        Element band = bands.getFirst();
        List<Element> componentElements = childElementsByLocalName(band, "componentElement");
        if (componentElements.size() < 2) {
            return;
        }

        for (Element component : componentElements) {
            Element datasetRun = findFirstByLocalName(component, "datasetRun");
            if (datasetRun == null) {
                continue;
            }
            if (!"Dataset3".equals(datasetRun.getAttribute("subDataset"))) {
                continue;
            }

            Element reportElement = findFirstByLocalName(component, "reportElement");
            if (reportElement == null) {
                continue;
            }

            // Float giup bang metadata xuong sau bang users khi bang users co nhieu dong.
            reportElement.setAttribute("positionType", "Float");
            reportElement.setAttribute("y", "0");
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
            if (child.getNodeType() == Node.ELEMENT_NODE && matchesLocalName(child, localName)) {
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
        if (matchesLocalName(parent, localName)) {
            return parent;
        }
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node.getNodeType() == Node.ELEMENT_NODE ? (Element) node : null;
    }

    private static boolean matchesLocalName(Node node, String localName) {
        String ln = node.getLocalName();
        if (ln != null) {
            return localName.equals(ln);
        }
        String name = node.getNodeName();
        if (name == null) {
            return false;
        }
        // Handle tag with prefix: jr:parameter, etc.
        if (name.equals(localName)) {
            return true;
        }
        int idx = name.indexOf(':');
        return idx >= 0 && name.substring(idx + 1).equals(localName);
    }

    private static boolean isElementAnyOf(Node node, String... names) {
        for (String name : names) {
            if (matchesLocalName(node, name)) {
                return true;
            }
        }
        return false;
    }

    private void upsertTemplate(String jrxmlName, byte[] jrxmlContent, String logoName, byte[] logoContent) {
        invoiceReportTemplateRepository.upsert(
                InvoiceReportTemplateRepository.SINGLE_TEMPLATE_ID,
                jrxmlName,
                jrxmlContent,
                logoName,
                logoContent);
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
}
