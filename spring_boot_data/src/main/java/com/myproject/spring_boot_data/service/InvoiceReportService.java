package com.myproject.spring_boot_data.service;

import jakarta.annotation.PostConstruct;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static net.sf.jasperreports.engine.JRParameter.REPORT_CONNECTION;

@Service
public class InvoiceReportService {

    // Jasper template có thể được đặt theo 1 trong 2 đường dẫn sau (trong classpath).
    // - `report/Invoice.jrxml`: nếu bạn copy vào `src/main/resources/report/Invoice.jrxml`
    // - `Invoice.jrxml`: nếu bạn đóng gói trực tiếp từ thư mục `MyReports/`
    private static final String INVOICE_JRXML_CLASSPATH_1 = "report/Invoice.jrxml";
    private static final String INVOICE_JRXML_CLASSPATH_2 = "Invoice.jrxml";

    // Jasper template đang hardcode tên file ảnh logo. Nếu file này không tồn tại, report có thể fail runtime.
    // Để tránh việc đó trong môi trường dev, service sẽ tạo sẵn ảnh placeholder 1x1 (PNG) theo đúng tên file.
    private static final String INVOICE_LOGO_FILENAME = "Eximbank logo-01 (4) (2) (1).png";

    private final DataSource dataSource;
    private JasperReport compiledInvoice;

    public InvoiceReportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void compileTemplate() throws Exception {
        System.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        try (InputStream in = loadInvoiceJrxmlInputStream()) {
            this.compiledInvoice = JasperCompileManager.compileReport(in);
        }
    }

    private InputStream loadInvoiceJrxmlInputStream() throws Exception {
        // 1) Load từ classpath đường dẫn chuẩn
        try {
            return new ClassPathResource(INVOICE_JRXML_CLASSPATH_1).getInputStream();
        } catch (FileNotFoundException ignored) {
            // continue
        }

        // 2) Load theo kiểu đóng gói từ `MyReports/` (classpath root)
        try {
            return new ClassPathResource(INVOICE_JRXML_CLASSPATH_2).getInputStream();
        } catch (FileNotFoundException ignored) {
            // continue
        }

        // 3) Fallback dev: load từ filesystem trong repo (không cần copy file vào resources)
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate1 = cwd.resolve(Paths.get("MyReports", "Invoice.jrxml"));
        Path candidate2 = cwd.resolve(Paths.get("..", "MyReports", "Invoice.jrxml"));
        if (Files.exists(candidate1)) {
            return Files.newInputStream(candidate1);
        }
        if (Files.exists(candidate2)) {
            return Files.newInputStream(candidate2);
        }

        throw new IllegalStateException(
                "Không tìm thấy `Invoice.jrxml`. Hãy đảm bảo file nằm ở `MyReports/Invoice.jrxml` hoặc copy vào `src/main/resources/report/`."
        );
    }

    private URL resolveLogoUrl() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // 1) Logo có sẵn trong classpath (nếu bạn copy vào `src/main/resources/report/`)
        if (cl != null) {
            URL logo1 = cl.getResource("report/invoice_logo.png");
            if (logo1 != null) {
                return logo1;
            }
            URL logo2 = cl.getResource("report/" + INVOICE_LOGO_FILENAME);
            if (logo2 != null) {
                return logo2;
            }
        }

        // 2) Logo có sẵn trong thư mục repo (`MyReports/`)
        Path cwd = Paths.get("").toAbsolutePath();
        Path repoCandidate = cwd.resolve(Paths.get("MyReports", INVOICE_LOGO_FILENAME));
        if (Files.exists(repoCandidate)) {
            try {
                return repoCandidate.toUri().toURL();
            } catch (Exception ignored) {
                // continue fallback
            }
        }
        Path repoCandidate2 = cwd.resolve(Paths.get("..", "MyReports", INVOICE_LOGO_FILENAME));
        if (Files.exists(repoCandidate2)) {
            try {
                return repoCandidate2.toUri().toURL();
            } catch (Exception ignored) {
                // continue fallback
            }
        }

        // 3) Fallback: tạo placeholder 1x1 PNG ở thư mục temp và trả về URL để Jasper load
        Path logoPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(INVOICE_LOGO_FILENAME);
        if (!Files.exists(logoPath)) {
            // 1x1 transparent PNG
            String base64 =
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/2p8AAAAASUVORK5CYII=";
            try {
                byte[] bytes = Base64.getDecoder().decode(base64);
                Files.write(logoPath, bytes);
            } catch (Exception ignored) {
                // If create fails, return null.
            }
        }

        try {
            return logoPath.toUri().toURL();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> parametersWithConnection(Connection connection) {
        Map<String, Object> params = new HashMap<>();
        params.put(REPORT_CONNECTION, connection);
        params.put("LOGO_URL", resolveLogoUrl());
        return params;
    }

    public void exportPdf(OutputStream outputStream) throws JRException, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Map<String, Object> params = parametersWithConnection(connection);
            JasperPrint print = JasperFillManager.fillReport(compiledInvoice, params, connection);
            JasperExportManager.exportReportToPdfStream(print, outputStream);
        }
    }

    public void exportXlsx(OutputStream outputStream) throws JRException, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Map<String, Object> params = parametersWithConnection(connection);
            JasperPrint print = JasperFillManager.fillReport(compiledInvoice, params, connection);

            JRXlsxExporter exporter = new JRXlsxExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));

            SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
            configuration.setDetectCellType(true);
            configuration.setCollapseRowSpan(false);
            exporter.setConfiguration(configuration);

            exporter.exportReport();
        }
    }
}
