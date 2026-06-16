package ca.bc.gov.nrs.frep.dto.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

/**
 * Generic report-request payload. The Jasper layer only cares about the format
 * plus the parameter slots a given report's SQL / stored proc reads; each
 * report maps the subset it needs in {@code ReportParameterProvider}.
 *
 * <p>Mirrors the nr-fspts {@code FspReportRequestDto} shape with FREP-oriented
 * filter fields. All fields are nullable — a report that doesn't use a field
 * just leaves it null. Add fields here as new report definitions need them.</p>
 */
public record ReportRequest(
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
    /** Org unit / district number filter. */
    String orgUnitNo,
    /** Org unit CODE filter (e.g. {@code DCK}); {@code *} = all. Biodiversity extracts use this. */
    String orgUnitCode,
    /** Master-list (evaluation) year filter; {@code *} = all. (Proc param {@code p_start_year}.) */
    String masterListYear,
    /** Resource-value status code filter (e.g. {@code ACT}); {@code *} = all. */
    String resourceValueStatus,
    /** Checklist status code filter (CHR extract proc param {@code p_checklist_status_code}); {@code *} = all. */
    String checklistStatus,
    /** Client (agreement-holder) number. */
    String clientNumber,
    /** Licence number filter (Checklist Completion Status proc param {@code p_licence}). */
    String licenceNumber,
    /** Opening id for single-opening reports / the biodiversity-extract {@code p_opening} filter. */
    String openingId,
    /** Optional sort-column hint passed straight to the proc. */
    String sortColumn,
    /** Output format. Defaults to PDF when null. */
    ReportFormat format
) {}
