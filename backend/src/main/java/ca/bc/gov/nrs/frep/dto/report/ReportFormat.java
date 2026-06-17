package ca.bc.gov.nrs.frep.dto.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.http.MediaType;

/**
 * Output format for a generated report. Drives both the JasperReports exporter
 * branch (see {@code ReportService}) and the HTTP {@code Content-Type}.
 *
 * <p>Mirrors the nr-fspts {@code FspReportFormat}. JSON-deserializes
 * case-insensitively from either the enum name or the extension
 * ({@code "pdf"} / {@code "csv"}); a null/blank value defaults to PDF.</p>
 */
public enum ReportFormat {
  PDF("pdf", MediaType.APPLICATION_PDF),
  CSV("csv", new MediaType("text", "csv"));

  private final String extension;
  private final MediaType mediaType;

  ReportFormat(String extension, MediaType mediaType) {
    this.extension = extension;
    this.mediaType = mediaType;
  }

  public String getExtension() {
    return extension;
  }

  public MediaType getMediaType() {
    return mediaType;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ReportFormat fromJsonValue(String value) {
    if (value == null || value.isBlank()) {
      return PDF;
    }
    for (ReportFormat format : values()) {
      if (format.name().equalsIgnoreCase(value) || format.extension.equalsIgnoreCase(value)) {
        return format;
      }
    }
    throw new IllegalArgumentException("Unknown report format: " + value);
  }

  public static ReportFormat fromNullable(ReportFormat format) {
    return format == null ? PDF : format;
  }
}
