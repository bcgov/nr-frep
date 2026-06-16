package ca.bc.gov.nrs.frep.service.report;

import java.util.List;

/**
 * Tabular result of a report extract proc: the ordered column names (header row) and the rows
 * (each a list of stringified cell values, aligned to {@code columns}). Columns come from the
 * cursor metadata so the header is present even when there are zero rows.
 */
public record ReportExtract(List<String> columns, List<List<String>> rows) {}
