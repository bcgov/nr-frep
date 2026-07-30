package ca.bc.gov.nrs.frep.security;

import ca.bc.gov.nrs.frep.service.v1.report.ReportDefinition;
import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import org.springframework.stereotype.Component;

/**
 * Protocol + district authorization for the CSV data-extract report endpoint (registered as
 * {@code @reportAuth}, referenced from {@code @PreAuthorize}). The CHR Data Extract needs CHR
 * access — and for a district-scoped (non-admin) user the requested org unit must be one of their
 * districts ("all" / blank / {@code *} rejected); the biodiversity extracts need FREP edit access.
 */
@Component("reportAuth")
public class ReportAuthorizer {

  private final LoggedUserHelper loggedUserHelper;

  public ReportAuthorizer(LoggedUserHelper loggedUserHelper) {
    this.loggedUserHelper = loggedUserHelper;
  }

  /** True if the caller may generate the CSV data-extract {@code reportName} with the given filters. */
  public boolean canGenerate(String reportName, ReportRequest request) {
    ReportDefinition definition = ReportDefinition.fromId(reportName);
    if (definition == ReportDefinition.CHR_DATA_EXTRACT) {
      if (!loggedUserHelper.canAnyChr()) {
        return false;
      }
      if (loggedUserHelper.isSysAdmin()) {
        return true;
      }
      String code = request == null ? null : request.orgUnitCode();
      return code != null && !code.isBlank() && !"*".equals(code) && loggedUserHelper.canChr(code);
    }
    return loggedUserHelper.canEdit();
  }
}
