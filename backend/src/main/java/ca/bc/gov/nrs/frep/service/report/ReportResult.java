package ca.bc.gov.nrs.frep.service.report;

import org.springframework.http.MediaType;

/**
 * A rendered report: the binary body, the suggested download filename, and the
 * HTTP content type. Mirrors the nr-fspts {@code FspReportResult}.
 */
public record ReportResult(byte[] content, String filename, MediaType mediaType) {}
