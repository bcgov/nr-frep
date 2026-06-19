package ca.bc.gov.nrs.frep.service.v1.report;

import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import ca.bc.gov.nrs.frep.exception.ReportGenerationException;
import ca.bc.gov.nrs.frep.exception.ReportNotFoundException;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Translates a {@link ReportRequest} into the parameter {@code Map} Jasper feeds
 * to the underlying SQL / stored proc. Mirrors the nr-fspts
 * {@code FspReportParameterProvider}.
 *
 * <p>Parameter names follow the lowercase {@code p_*} convention the nr-rept
 * JRXMLs declare (e.g. {@code p_org_unit_no}). Jasper silently ignores map
 * entries that don't match a declared {@code <parameter>}, so an unused slot is
 * harmless and a typo simply shows up as a missing value at fill time.</p>
 *
 * <p>Every report receives the base parameters ({@code p_user_id} from the JWT +
 * {@code SUBREPORT_DIR}); {@link #buildJasperParameters} then dispatches per report
 * to add its specific filters. When you add a {@code ReportDefinition} constant for
 * a Jasper report, add a matching arm to the switch.</p>
 */
@Component
public class ReportParameterProvider {

  private static final LocalDate DEFAULT_START_DATE = LocalDate.of(1900, 1, 1);

  private static final LocalDate DEFAULT_END_DATE = LocalDate.of(9999, 12, 31);

  private static final String SUBREPORT_DIR_PARAM = "SUBREPORT_DIR";

  private final LoggedUserHelper loggedUserHelper;

  /** Compiled embedded subreports, keyed by classpath. Compiled once, reused across fills. */
  private final ConcurrentHashMap<String, JasperReport> compiledSubreports = new ConcurrentHashMap<>();

  public ReportParameterProvider(LoggedUserHelper loggedUserHelper) {
    this.loggedUserHelper = loggedUserHelper;
  }

  /** Builds the Jasper parameter map for the given report: a per-report arm plus the base params. */
  public Map<String, Object> buildJasperParameters(
      ReportDefinition definition, ReportRequest request) {
    Map<String, Object> params = switch (definition) {
      case CHECKLIST_COMPLETION_STATUS -> paramsChecklistCompletion(request);
      case CHECKLIST_REJECTION_REASON -> paramsChecklistRejection(request);
      // CSV data extracts (BIODIVERSITY_*) route through CSVReportService, not Jasper,
      // so they never reach this builder; an empty map keeps the switch exhaustive.
      default -> new HashMap<>();
    };
    params.putAll(baseParameters());
    return params;
  }

  /**
   * FREPRPT012 — Checklist Completion Status. The proc filters by a year range,
   * licence and client number ({@code freprpt_resval_chklst_comp(cursor, p_start_year,
   * p_end_year, p_licence, p_client_number, p_user_id)}). The form's date range supplies
   * the years (defaulting to a wide span when absent, mirroring nr-fspts); {@code p_user_id}
   * is added by {@link #baseParameters()}.
   */
  private Map<String, Object> paramsChecklistCompletion(ReportRequest request) {
    Map<String, Object> params = new HashMap<>();
    params.put("p_start_year", yearOf(request.startDate(), DEFAULT_START_DATE));
    params.put("p_end_year", yearOf(request.endDate(), DEFAULT_END_DATE));
    params.put("p_licence", trimToEmpty(request.licenceNumber()));
    params.put("p_client_number", trimToEmpty(request.clientNumber()));
    return params;
  }

  /**
   * FREPRPT018 — Checklist Rejection Reason. The main proc
   * ({@code freprpt_chklst_rej_reason(cursor, p_org_unit_code, p_start_year, p_end_year, p_user_id)})
   * rolls up accepted/rejected counts by region + district; an embedded detail subreport
   * ({@code freprpt_chklst_rej_reas}) lists the rejection reasons per district. The org-unit and
   * year filters come from the form; the compiled subreport is injected as {@code REJECTION_SUBREPORT}
   * (the main JRXML's {@code subreportExpression} is {@code $P{REJECTION_SUBREPORT}}), and the
   * subreport reuses the fill's {@code REPORT_CONNECTION}.
   */
  private Map<String, Object> paramsChecklistRejection(ReportRequest request) {
    Map<String, Object> params = new HashMap<>();
    params.put("p_org_unit_code", trimToEmpty(request.orgUnitCode()));
    params.put("p_start_year", yearOf(request.startDate(), DEFAULT_START_DATE));
    params.put("p_end_year", yearOf(request.endDate(), DEFAULT_END_DATE));
    params.put(
        "REJECTION_SUBREPORT", compileSubreport("reports/CHECKLIST_REJECTION_REASON_subreport.jrxml"));
    return params;
  }

  /** Parameters every report receives: the current IDIR user and the subreport dir. */
  private Map<String, Object> baseParameters() {
    Map<String, Object> params = new HashMap<>();
    params.put("p_user_id", currentUserId());
    params.put(SUBREPORT_DIR_PARAM, resolveSubreportDir());
    return params;
  }

  // ── Helpers (mirrored from nr-fspts; reused by per-report builders) ─────────

  private String currentUserId() {
    String userId = loggedUserHelper.getLoggedUserId();
    return userId == null ? "" : userId;
  }

  /** Formats a date as {@code YYYY-MM-DD} (JRXMLs declare these as String), or the fallback. */
  static String formatDate(LocalDate value, LocalDate fallback) {
    LocalDate effective = value != null ? value : fallback;
    return effective.format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  /** The 4-digit year of a date as a String (procs that filter by year want this), or the fallback's. */
  static String yearOf(LocalDate value, LocalDate fallback) {
    LocalDate effective = value != null ? value : fallback;
    return String.valueOf(effective.getYear());
  }

  static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Compiles an embedded subreport JRXML from the classpath (cached). Reports whose JRXML carries a
   * {@code $P{...}} subreport expression of class {@code JasperReport} pass the result in their
   * parameter map. Mirrors {@code ReportService.compileTemplate}; surfaces the same 404/502.
   */
  private JasperReport compileSubreport(String classpath) {
    return compiledSubreports.computeIfAbsent(classpath, path -> {
      ClassPathResource resource = new ClassPathResource(path);
      if (!resource.exists()) {
        throw new ReportNotFoundException(
            path, new IllegalStateException("No subreport template at classpath:" + path));
      }
      try (InputStream is = resource.getInputStream()) {
        return JasperCompileManager.compileReport(is);
      } catch (JRException | IOException ex) {
        throw new ReportGenerationException("Failed to compile subreport " + path, ex);
      }
    });
  }

  /**
   * Resolves {@code classpath:reports/} to a filesystem/jar dir string for the
   * {@code SUBREPORT_DIR} parameter sub-reports use. Returns {@code ""} when it
   * can't be resolved (e.g. no reports dir yet) — a report with no sub-reports
   * never reads it.
   */
  private static String resolveSubreportDir() {
    try {
      ClassPathResource resource = new ClassPathResource("reports/");
      if (!resource.exists()) {
        return "";
      }
      return resource.getURL().toString();
    } catch (IOException ex) {
      return "";
    }
  }
}
