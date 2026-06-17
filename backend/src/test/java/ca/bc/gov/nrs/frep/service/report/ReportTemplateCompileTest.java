package ca.bc.gov.nrs.frep.service.report;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Compiles every registered report's JRXML template so a malformed template fails the build rather
 * than only surfacing at runtime (the production warm-up needs the oracle profile). Mirrors what
 * {@code ReportService.compileTemplate} does, minus the fill/DB.
 */
class ReportTemplateCompileTest {

  @Test
  void everyJasperTemplateReportCompiles() throws Exception {
    for (ReportDefinition definition : ReportDefinition.values()) {
      // CSV data-extract reports (procName != null) are rendered by CSVReportService from the proc
      // cursor — they have no JRXML. Only Jasper template reports carry a template to compile.
      if (definition.getProcName() != null) {
        continue;
      }
      String path = "reports/" + definition.name() + ".jrxml";
      ClassPathResource resource = new ClassPathResource(path);
      assertTrue(resource.exists(), "Missing JRXML template at classpath:" + path);
      try (InputStream is = resource.getInputStream()) {
        JasperReport compiled = JasperCompileManager.compileReport(is);
        assertNotNull(compiled, "Compile returned null for " + path);
      }
    }
  }

  /**
   * Embedded subreports aren't {@link ReportDefinition}s, so the loop above skips them — compile
   * them explicitly here so a malformed subreport fails the build too. Keep this list in sync with
   * the {@code $P{..._SUBREPORT}} templates compiled in {@code ReportParameterProvider}.
   */
  @Test
  void everyEmbeddedSubreportCompiles() throws Exception {
    String[] subreports = {"reports/CHECKLIST_REJECTION_REASON_subreport.jrxml"};
    for (String path : subreports) {
      ClassPathResource resource = new ClassPathResource(path);
      assertTrue(resource.exists(), "Missing subreport template at classpath:" + path);
      try (InputStream is = resource.getInputStream()) {
        assertNotNull(JasperCompileManager.compileReport(is), "Compile returned null for " + path);
      }
    }
  }
}
