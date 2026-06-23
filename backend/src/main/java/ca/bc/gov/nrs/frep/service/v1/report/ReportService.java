package ca.bc.gov.nrs.frep.service.v1.report;

import ca.bc.gov.nrs.frep.struct.v1.report.ReportFormat;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import ca.bc.gov.nrs.frep.exception.ReportGenerationException;
import ca.bc.gov.nrs.frep.exception.ReportNotFoundException;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Compile-on-first-request JasperReports engine. Mirrors the nr-fspts
 * {@code FspReportService} (which mirrors nr-rept's {@code ReptReportService}):
 * compile JRXML → cache → fill against a live JDBC {@link Connection} (the
 * template carries the SQL) → export PDF or CSV.
 *
 * <p>JRXML templates live under {@code classpath:reports/<NAME>.jrxml} where
 * {@code NAME} matches {@code ReportDefinition.name()}. The registry is empty for
 * now, so {@link #warmCompileCache()} is a no-op and every request 404s until a
 * report is registered + its template added.</p>
 *
 * <p>Filling reads through the Oracle datasource (the same one repositories use via
 * {@code oracleJdbcTemplate}).</p>
 */
@Service
public class ReportService {

  private static final Logger LOG = LoggerFactory.getLogger(ReportService.class);

  private final DataSource dataSource;
  private final ReportParameterProvider parameterProvider;
  private final ConcurrentHashMap<String, JasperReport> compiledCache = new ConcurrentHashMap<>();

  public ReportService(
      @Qualifier("oracleJdbcTemplate") JdbcTemplate oracleJdbcTemplate,
      ReportParameterProvider parameterProvider) {
    this.dataSource = Objects.requireNonNull(oracleJdbcTemplate.getDataSource(),
        "oracleJdbcTemplate has no DataSource");
    this.parameterProvider = parameterProvider;
  }

  /**
   * Pre-compile every registered JRXML at startup so a broken template surfaces
   * in deploy logs rather than on the first user hit. Failures are logged, never
   * fatal — one bad report shouldn't take the API offline; the on-demand path
   * hits the same failure later and returns a 404/502. No-op while the registry
   * is empty.
   */
  @PostConstruct
  void warmCompileCache() {
    for (ReportDefinition definition : ReportDefinition.values()) {
      try {
        compiledCache.put(definition.getId(), compileTemplate(definition));

        LOG.info("Pre-compiled report template [{}]", definition.getId());
      } catch (ReportNotFoundException ex) {
        LOG.warn("Report [{}] has no JRXML on the classpath; will 404 at request time",
            definition.getId());
      } catch (ReportGenerationException ex) {
        LOG.warn("Report [{}] failed to compile at startup; will 502 at request time: {}",
            definition.getId(), ex.getMessage());
      }
    }
  }

  public ReportResult generateReport(String reportId, ReportRequest request) {
    ReportDefinition definition = ReportDefinition.fromId(reportId);
    ReportFormat format = ReportFormat.fromNullable(request.format());

    Map<String, Object> params = parameterProvider.buildJasperParameters(definition, request);
    JasperReport jasperReport =
        compiledCache.computeIfAbsent(definition.getId(), id -> compileTemplate(definition));

    try (Connection connection = dataSource.getConnection()) {
      JasperPrint print = JasperFillManager.fillReport(jasperReport, params, connection);
      byte[] body = switch (format) {
        case PDF -> JasperExportManager.exportReportToPdf(print);
        case CSV -> exportToCsv(print);
      };
      if (body == null || body.length == 0) {
        throw new ReportGenerationException(
            "Empty " + format.name() + " produced for report " + reportId);
      }
      return new ReportResult(body, definition.resolveFilename(format), format.getMediaType());
    } catch (JRException ex) {
      LOG.error("Jasper fill/export failed for [{}]", reportId, ex);
      throw new ReportGenerationException("Failed to render report " + reportId, ex);
    } catch (SQLException ex) {
      LOG.error("Database connection failed for report [{}]", reportId, ex);
      throw new ReportGenerationException("Database connection failed for report " + reportId, ex);
    }
  }

  /**
   * Exports the filled report to CSV via {@link JRCsvExporter} (a flat tabular
   * dump of the main dataset; band layout / images / sub-reports are dropped,
   * which is the expected CSV behaviour).
   */
  private static byte[] exportToCsv(JasperPrint print) throws JRException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    JRCsvExporter exporter = new JRCsvExporter();
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleWriterExporterOutput(out, StandardCharsets.UTF_8.name()));
    exporter.exportReport();
    return out.toByteArray();
  }

  private JasperReport compileTemplate(ReportDefinition definition) {
    String path = "reports/" + definition.name() + ".jrxml";
    ClassPathResource resource = new ClassPathResource(path);
    if (!resource.exists()) {
      throw new ReportNotFoundException(definition.getId(),
          new IllegalStateException("No JRXML template at classpath:" + path));
    }
    try (InputStream is = resource.getInputStream()) {
      return JasperCompileManager.compileReport(is);
    } catch (JRException | IOException ex) {
      LOG.error("Failed to compile JRXML for [{}] (path={})", definition.getId(), path, ex);
      throw new ReportGenerationException("Failed to compile JRXML for " + definition.getId(), ex);
    }
  }
}
