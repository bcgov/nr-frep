package ca.bc.gov.nrs.frep.service.v1.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guards the per-report proc-call binding in {@link ReportExtractRepository#bindArgs}. The
 * biodiversity extracts (001-005) and the CHR extract (FREPRPT022) call procs with different
 * argument lists, so the binding can't be hard-coded — these assert each report's positional args.
 */
class ReportExtractRepositoryTest {

  private final LoggedUserHelper loggedUserHelper = mock(LoggedUserHelper.class);
  private final ReportExtractRepository repository =
      new ReportExtractRepository(mock(JdbcTemplate.class), loggedUserHelper);

  /** Builds a ReportRequest with only the report-filter fields set (everything else null). */
  private static ReportRequest request(
      String orgUnitCode,
      String masterListYear,
      String resourceValueStatus,
      String checklistStatus,
      String openingId) {
    return new ReportRequest(
        null, null, null, orgUnitCode, masterListYear, resourceValueStatus, checklistStatus,
        null, null, openingId, null, null);
  }

  @Test
  void biodiversityExtractBindsFourArgsWithAllSentinels() {
    // Empty filters collapse to the legacy "*" sentinel (opening is blank, not "*").
    List<String> args =
        repository.bindArgs(ReportDefinition.BIODIVERSITY_EXTRACT_BLOCK, request(null, null, null, null, null));

    assertThat(args).containsExactly("*", "", "*", "*");
  }

  @Test
  void biodiversityExtractPassesThroughProvidedFilters() {
    List<String> args =
        repository.bindArgs(
            ReportDefinition.BIODIVERSITY_EXTRACT_PLOT, request("DCK", "2023", "ACT", null, "1709463"));

    assertThat(args).containsExactly("DCK", "1709463", "2023", "ACT");
  }

  @Test
  void chrExtractBindsSixArgsRepeatingMasterListAndAppendingUser() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\me");

    List<String> args =
        repository.bindArgs(ReportDefinition.CHR_DATA_EXTRACT, request("DCK", "2023", "ACT", "SUB", null));

    // org unit, master list (twice = legacy from/to), checklist status, resource value, user id.
    assertThat(args).containsExactly("DCK", "2023", "2023", "SUB", "ACT", "IDIR\\me");
  }

  @Test
  void chrExtractDefaultsBlankUserToEmptyString() {
    when(loggedUserHelper.getLoggedUserId()).thenReturn(null);

    List<String> args =
        repository.bindArgs(ReportDefinition.CHR_DATA_EXTRACT, request(null, null, null, null, null));

    assertThat(args).containsExactly("*", "*", "*", "*", "*", "");
  }
}
