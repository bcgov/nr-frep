package ca.bc.gov.nrs.frep.service.v1.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.exception.InvalidParameterException;
import ca.bc.gov.nrs.frep.exception.ReportNotFoundException;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Endpoint routing rules for the Jasper report path. The fill itself needs a live Oracle
 * connection, so these cover only the checks that run before it.
 */
class ReportServiceTest {

  /** A filter-less request; these tests never reach the fill, so every component may be null. */
  private static ReportRequest emptyRequest() {
    return new ReportRequest(
        null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static ReportService newService() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.getDataSource()).thenReturn(mock(DataSource.class));
    return new ReportService(jdbcTemplate, new ReportParameterProvider(null));
  }

  @Test
  void rejectsCsvDataExtractsSoTheyCannotBypassTheDistrictGate() {
    // chr-data-extract is a valid report id, but it belongs to POST /reports/csv/{reportName},
    // where ReportAuthorizer checks the caller holds the requested district. Accepting it here
    // would route around that check — previously only a missing JRXML stopped it.
    assertThatThrownBy(() ->
        newService().generateReport("chr-data-extract", emptyRequest()))
        .isInstanceOf(InvalidParameterException.class)
        .hasMessageContaining("/api/v1/reports/csv/chr-data-extract");
  }

  @Test
  void unknownReportIdStillYieldsAReportNotFound() {
    // The CSV guard must not swallow the 404 path for ids that aren't registered at all.
    assertThatThrownBy(() -> newService().generateReport("nope", emptyRequest()))
        .isInstanceOf(ReportNotFoundException.class);
  }

  @Test
  void everyCsvExtractIsRejectedAndEveryJasperReportIsNot() {
    // Drives the rule off the registry rather than a hardcoded list, so adding a definition can't
    // quietly land on the wrong side of the guard. Jasper reports get past it and fail later at
    // fill time (which needs a database), so the assertion is only that it isn't *this* rejection.
    ReportService service = newService();
    for (ReportDefinition definition : ReportDefinition.values()) {
      Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
          () -> service.generateReport(definition.getId(), emptyRequest()));
      if (definition.getProcName() != null) {
        assertThat(thrown)
            .as("CSV extract %s must be rejected by the Jasper endpoint", definition.getId())
            .isInstanceOf(InvalidParameterException.class);
      } else {
        assertThat(thrown)
            .as("Jasper report %s must get past the CSV guard", definition.getId())
            .isNotInstanceOf(InvalidParameterException.class);
      }
    }
  }
}
