package com.example.demo.service.jasper;

import com.example.demo.dto.response.RuntimeTemplate;
import com.example.demo.repository.InvoiceReportTemplateRepository.StoredInvoiceTemplate;
import jakarta.annotation.PostConstruct;
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
public class InvoiceReportTemplateConfigService {

    private static final String DEFAULT_LOGO_ALIAS = "Eximbank_Logo.png";
    private static final String DEFAULT_TEMPLATE_CLASSPATH = "report/Invoice.jrxml";
    private static final String DEFAULT_LOGO_CLASSPATH = "report/Eximbank_Logo.png";
    private static final String JR_DATA_SOURCE_CLASS = "net.sf.jasperreports.engine.JRDataSource";
    private static final String DATASET_TEMPLATE_METADATA = "Dataset3";
    private static final String DATASET_MAIN_TABLE = "Dataset2";
    private static final String REPORT_DATA_SOURCE_EXPR = "$P{REPORT_DATA_SOURCE}";
    private static final String TABLE_DATA_SOURCE_EXPR = "$P{TABLE_DATA_SOURCE}";
    private static final String TEMPLATE_DATA_SOURCE_EXPR = "$P{TEMPLATE_DATA_SOURCE}";
    private static final Set<String> ALLOWED_MAIN_COLUMNS = Set.of("id", "firstname", "lastname", "email", "username", "role");
    private static final Set<String> ALLOWED_TEMPLATE_COLUMNS = Set.of("id", "name", "price", "capacity", "city");

    private volatile StoredInvoiceTemplate currentTemplate;

    @PostConstruct
    void init() throws IOException {
        currentTemplate = new StoredInvoiceTemplate(
                filenameFromPath(DEFAULT_TEMPLATE_CLASSPATH),
                readClasspathFile(DEFAULT_TEMPLATE_CLASSPATH),
                filenameFromPath(DEFAULT_LOGO_CLASSPATH),
                readClasspathFile(DEFAULT_LOGO_CLASSPATH),
                Instant.now());
    }

    public StoredInvoiceTemplate getCurrentTemplate() {
        if (currentTemplate == null) {
            throw new IllegalStateException("Khong tim thay report template mac dinh.");
        }
        return currentTemplate;
    }

    public RuntimeTemplate compileCurrentTemplate(List<String> selectedColumns) throws JRException, IOException {
        StoredInvoiceTemplate current = getCurrentTemplate();
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
        ColumnSelection selection = normalizeSelectedColumns(selectedColumns);
        if (selection.keepAllMain() && selection.keepAllTemplate()) {
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
                String datasetName = resolveTableDatasetName(table);
                Set<String> allowedColumns = DATASET_TEMPLATE_METADATA.equals(datasetName)
                        ? ALLOWED_TEMPLATE_COLUMNS
                        : ALLOWED_MAIN_COLUMNS;
                Set<String> chosenColumns = DATASET_TEMPLATE_METADATA.equals(datasetName)
                        ? selection.templateColumns()
                        : selection.mainColumns();

                List<Element> kept = new java.util.ArrayList<>();
                int validColumnsFound = 0;
                int validColumnsTotalWidth = 0;
                for (Element col : columns) {
                    String columnKey = extractColumnKey(col);
                    if (allowedColumns.contains(columnKey)) {
                        validColumnsFound++;
                        validColumnsTotalWidth += parseWidth(col.getAttribute("width"));
                        if (chosenColumns.isEmpty() || chosenColumns.contains(columnKey)) {
                            kept.add(col);
                        }
                    }
                }

                if (validColumnsFound == 0 || kept.isEmpty()) {
                    continue;
                }

                for (Element col : columns) {
                    if (!kept.contains(col) && allowedColumns.contains(extractColumnKey(col))) {
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

            removeElementsAtRootAndSubDatasets(root, "parameter", "REPORT_DATA_SOURCE");
            ensureRootDataSourceParameter(root, "TABLE_DATA_SOURCE");
            ensureRootDataSourceParameter(root, "TEMPLATE_DATA_SOURCE");
            removeElementsAtRootAndSubDatasets(root, "queryString", null);
            replaceConnectionExpressions(root);
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

    private static void ensureRootDataSourceParameter(Element root, String paramName) {
        List<Element> directParams = childElementsByLocalName(root, "parameter");
        for (Element p : directParams) {
            if (!paramName.equals(p.getAttribute("name"))) {
                continue;
            }
            p.setAttribute("class", JR_DATA_SOURCE_CLASS);
            return;
        }

        Document doc = root.getOwnerDocument();
        Element param = doc.createElementNS(root.getNamespaceURI(), "parameter");
        param.setAttribute("name", paramName);
        param.setAttribute("class", JR_DATA_SOURCE_CLASS);
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

    private static void removeElementsAtRootAndSubDatasets(Element root, String elementName, String attrNameFilter) {
        removeDirectChildren(root, elementName, attrNameFilter);
        for (Element subDataset : descendantsByLocalName(root, "subDataset")) {
            removeDirectChildren(subDataset, elementName, attrNameFilter);
        }
    }

    private static void removeDirectChildren(Element parent, String localName, String attrNameFilter) {
        for (Element child : childElementsByLocalName(parent, localName)) {
            if (attrNameFilter != null && !attrNameFilter.equals(child.getAttribute("name"))) {
                continue;
            }
            Node container = child.getParentNode();
            if (container != null) {
                container.removeChild(child);
            }
        }
    }

    private static void replaceConnectionExpressions(Element root) {
        List<Element> datasetRuns = descendantsByLocalName(root, "datasetRun");
        for (Element datasetRun : datasetRuns) {
            Element connectionExpression = findFirstByLocalName(datasetRun, "connectionExpression");
            if (connectionExpression == null) {
                continue;
            }

            Element dataSourceExpression = datasetRun.getOwnerDocument().createElementNS(root.getNamespaceURI(), "dataSourceExpression");
            dataSourceExpression.setTextContent(REPORT_DATA_SOURCE_EXPR);
            datasetRun.replaceChild(dataSourceExpression, connectionExpression);
        }
    }

    private static void normalizeDatasetRunDataSourceExpressions(Element root) {
 
        List<Element> datasetRuns = descendantsByLocalName(root, "datasetRun");
        for (Element datasetRun : datasetRuns) {
            String subDataset = datasetRun.getAttribute("subDataset");
            Element dse = findFirstByLocalName(datasetRun, "dataSourceExpression");
            if (dse == null) {
                if (DATASET_TEMPLATE_METADATA.equals(subDataset)) {
                    Element created = datasetRun.getOwnerDocument().createElementNS(root.getNamespaceURI(), "dataSourceExpression");
                    created.setTextContent(TEMPLATE_DATA_SOURCE_EXPR);
                    datasetRun.appendChild(created);
                }
                continue;
            }

            String text = dse.getTextContent() == null ? "" : dse.getTextContent().trim();
            if (DATASET_TEMPLATE_METADATA.equals(subDataset)) {
                if (REPORT_DATA_SOURCE_EXPR.equals(text) || text.contains("TEMPLATE_DATA_SOURCE")) {
                    dse.setTextContent(TEMPLATE_DATA_SOURCE_EXPR);
                }
            } else if (REPORT_DATA_SOURCE_EXPR.equals(text)) {
                dse.setTextContent(TABLE_DATA_SOURCE_EXPR);
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
        int mainTableTotalWidth = resolveMainTableTotalWidth(root);
        List<Element> componentElements = childElementsByLocalName(band, "componentElement");
        if (componentElements.size() < 2) {
            return;
        }

        for (Element component : componentElements) {
            Element datasetRun = findFirstByLocalName(component, "datasetRun");
            if (datasetRun == null) {
                continue;
            }
            if (!DATASET_TEMPLATE_METADATA.equals(datasetRun.getAttribute("subDataset"))) {
                continue;
            }

            Element reportElement = findFirstByLocalName(component, "reportElement");
            if (reportElement == null) {
                continue;
            }
            reportElement.setAttribute("positionType", "Float");
            reportElement.setAttribute("y", "0");
            int boostedWidth = mainTableTotalWidth > 0 ? mainTableTotalWidth + 120 : 0;
            if (boostedWidth > 0) {
                reportElement.setAttribute("width", String.valueOf(boostedWidth));
            }

            Element table = findFirstByLocalName(component, "table");
            if (table == null) {
                continue;
            }
            List<Element> columns = childElementsByLocalName(table, "column");
            if (columns.isEmpty()) {
                continue;
            }
            int currentWidth = columns.stream().mapToInt(c -> parseWidth(c.getAttribute("width"))).sum();
            rebalanceColumnWidths(columns, boostedWidth > 0 ? boostedWidth : currentWidth);
        }
    }

    private static int resolveMainTableTotalWidth(Element root) {
        for (Element table : descendantsByLocalName(root, "table")) {
            String datasetName = resolveTableDatasetName(table);
            if (!DATASET_MAIN_TABLE.equals(datasetName)) {
                continue;
            }
            List<Element> columns = childElementsByLocalName(table, "column");
            if (columns.isEmpty()) {
                continue;
            }
            return columns.stream().mapToInt(c -> parseWidth(c.getAttribute("width"))).sum();
        }
        return 0;
    }

    private static String resolveTableDatasetName(Element tableElement) {
        Node parent = tableElement.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (matchesLocalName(parentElement, "componentElement")) {
                Element datasetRun = findFirstByLocalName(parentElement, "datasetRun");
                if (datasetRun != null) {
                    String subDataset = datasetRun.getAttribute("subDataset");
                    if (subDataset != null && !subDataset.isBlank()) {
                        return subDataset.trim();
                    }
                }
            }
            parent = parent.getParentNode();
        }
        return "";
    }

    private static ColumnSelection normalizeSelectedColumns(List<String> selectedColumns) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            return ColumnSelection.keepAll();
        }
        Set<String> main = new LinkedHashSet<>();
        Set<String> template = new LinkedHashSet<>();
        for (String c : selectedColumns) {
            if (c == null || c.isBlank()) {
                continue;
            }
            String[] tokens = c.split(",");
            for (String token : tokens) {
                String raw = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
                if (raw.isBlank()) {
                    continue;
                }

                if (raw.startsWith("room:") || raw.startsWith("template:")) {
                    String key = raw.substring(raw.indexOf(':') + 1).trim();
                    if (ALLOWED_TEMPLATE_COLUMNS.contains(key)) {
                        template.add(key);
                    }
                    continue;
                }

                if (raw.startsWith("user:") || raw.startsWith("table:")) {
                    String key = raw.substring(raw.indexOf(':') + 1).trim();
                    if (ALLOWED_MAIN_COLUMNS.contains(key)) {
                        main.add(key);
                    }
                    continue;
                }

                if (ALLOWED_MAIN_COLUMNS.contains(raw)) {
                    main.add(raw);
                } else if (ALLOWED_TEMPLATE_COLUMNS.contains(raw)) {
                    template.add(raw);
                }
            }
        }
        return new ColumnSelection(main, template);
    }

    private record ColumnSelection(Set<String> mainColumns, Set<String> templateColumns) {
        static ColumnSelection keepAll() {
            return new ColumnSelection(Set.of(), Set.of());
        }

        boolean keepAllMain() {
            return mainColumns == null || mainColumns.isEmpty();
        }

        boolean keepAllTemplate() {
            return templateColumns == null || templateColumns.isEmpty();
        }
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

    private static String filenameFromPath(String path) {
        if (path == null || path.isBlank()) {
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
