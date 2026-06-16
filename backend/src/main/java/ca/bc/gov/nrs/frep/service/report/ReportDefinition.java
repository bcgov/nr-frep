package ca.bc.gov.nrs.frep.service.report;

import ca.bc.gov.nrs.frep.dto.report.ReportFormat;
import ca.bc.gov.nrs.frep.exception.ReportNotFoundException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Catalogue of report templates the backend ships with. Mirrors the nr-fspts
 * {@code FspReportDefinition} pattern:
 *
 * <ul>
 *   <li>{@code name()} (e.g. {@code CHECKLIST_SUMMARY}) is the JRXML filename on
 *       the classpath: {@code reports/<NAME>.jrxml} (see
 *       {@code ReportService.compileTemplate}).</li>
 *   <li>{@code id} is the front-end-facing route token:
 *       {@code POST /api/v1/reports/{id}}.</li>
 *   <li>{@code defaultFilename} is the download filename base (the format
 *       extension is appended).</li>
 * </ul>
 *
 * <p>To wire a new Jasper (PDF) report:
 *
 * <ol>
 *   <li>Add a constant below with {@code procName == null}, e.g.
 *       {@code CHECKLIST_COMPLETION_STATUS("checklist-completion-status",
 *       "Checklist_Completion_Status", null)}.</li>
 *   <li>Drop the matching template at
 *       {@code src/main/resources/reports/CHECKLIST_COMPLETION_STATUS.jrxml}.</li>
 *   <li>Add a parameter builder arm in {@code ReportParameterProvider}.</li>
 *   <li>Mirror the id in the frontend {@code reportDefinitions.ts}.</li>
 * </ol>
 *
 * <p>An unregistered id makes {@link #fromId(String)} throw
 * {@link ReportNotFoundException} so the request returns HTTP 404.</p>
 */
public enum ReportDefinition {
  // ── Reports → Biodiversity → Data Extract (legacy JCRS FREPRPT001-005) ──────
  // CSV extracts filled from an Oracle ref-cursor proc; see ReportDataProvider.
  BIODIVERSITY_EXTRACT_BLOCK(
      "biodiversity-extract-block", "Biodiversity_Extract_Block", "freprpt_bio_opening"),
  BIODIVERSITY_EXTRACT_STRATUM(
      "biodiversity-extract-stratum", "Biodiversity_Extract_Stratum", "freprpt_bio_stratum_sum"),
  BIODIVERSITY_EXTRACT_PLOT(
      "biodiversity-extract-plot", "Biodiversity_Extract_Plot", "freprpt_bio_plot"),
  BIODIVERSITY_EXTRACT_STAND(
      "biodiversity-extract-stand", "Biodiversity_Extract_Stand", "freprpt_bio_plot_stand"),
  BIODIVERSITY_EXTRACT_CWD(
      "biodiversity-extract-cwd", "Biodiversity_Extract_CWD", "freprpt_bio_plot_cwd"),

  // ── Reports → Cultural Heritage → Data Extract (legacy JCRS FREPRPT022, admin-only) ──
  // CSV extract; proc takes a different arg list than the biodiversity extracts (org unit,
  // master list ×2, checklist status, resource value, user id) — see ReportExtractRepository.
  CHR_DATA_EXTRACT(
      "chr-data-extract", "CHR_Data_Extract", "freprpt_chr_extract"),

  // ── Reports → Checklist Completion Status (legacy JCRS FREPRPT012) ───────────
  // Jasper PDF report whose JRXML carries its own proc call (freprpt_resval_chklst_comp);
  // filled against a JDBC connection. procName is null so it routes through ReportService.
  CHECKLIST_COMPLETION_STATUS(
      "checklist-completion-status", "Checklist_Completion_Status", null),

  // ── Reports → Checklist Rejection Reason (legacy JCRS FREPRPT018) ────────────
  // Jasper PDF; the main JRXML calls freprpt_chklst_rej_reason and embeds a detail
  // subreport (freprpt_chklst_rej_reas) compiled + injected via REJECTION_SUBREPORT
  // in ReportParameterProvider. procName is null so it routes through ReportService.
  CHECKLIST_REJECTION_REASON(
      "checklist-rejection-reason", "Checklist_Rejection_Reason", null),
  ;

  private final String id;
  private final String defaultFilename;
  /**
   * Oracle ref-cursor proc that supplies the report rows
   * ({@code {call <proc>(cursor, p_org_unit_code, p_opening, p_start_year, p_resource_val)}}), or
   * {@code null} for a report whose JRXML carries its own SQL (filled against a JDBC connection).
   */
  private final String procName;

  ReportDefinition(String id, String defaultFilename, String procName) {
    this.id = id;
    this.defaultFilename = defaultFilename;
    this.procName = procName;
  }

  public String getId() {
    return id;
  }

  public String getProcName() {
    return procName;
  }

  /** Download filename for the given format, e.g. {@code Checklist_Summary.pdf}. */
  public String resolveFilename(ReportFormat format) {
    return defaultFilename + "." + format.getExtension();
  }

  /** Case-insensitive lookup by route token. 404s on an unknown / unregistered id. */
  public static ReportDefinition fromId(String reportId) {
    return Optional.ofNullable(reportId)
        .flatMap(id -> Arrays.stream(values())
            .filter(def -> def.id.equalsIgnoreCase(id))
            .findFirst())
        .orElseThrow(() -> new ReportNotFoundException(reportId));
  }
}
