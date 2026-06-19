package ca.bc.gov.nrs.frep.service.v1.report;

import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import ca.bc.gov.nrs.frep.repository.v1.frep.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Runs an Oracle ref-cursor extract proc for the biodiversity data-extract reports and returns the
 * rows + column order. The legacy JCRS reports ran
 * {@code {call freprpt_bio_*(ORACLE_REF_CURSOR, p_org_unit_code, p_opening, p_start_year,
 * p_resource_val)}} via JasperServer's plsql executor; we call the proc directly (the same
 * {@code registerOutCursor} pattern repositories use) and hand the result to {@link CSVReportService}.
 *
 * <p>Column labels are lower-cased to match the legacy CSV headers; cell values are stringified.
 * The {@code freprpt_bio_*} procs live in the JCRS reporting schema — the app's Oracle user needs
 * execute (grant/synonym) on them.</p>
 */
@Repository
@Profile("oracle")
public class ReportExtractRepository extends AbstractFrepRepository {

  /** Legacy "all" sentinel for the mandatory dropdown filters. */
  private static final String ALL = "*";

  private final LoggedUserHelper loggedUserHelper;

  public ReportExtractRepository(
      @Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate, LoggedUserHelper loggedUserHelper) {
    super(jdbcTemplate);
    this.loggedUserHelper = loggedUserHelper;
  }

  /** Calls the report's extract proc with the request filters and returns columns + rows. */
  public ReportExtract runExtract(ReportDefinition definition, ReportRequest request) {
    String proc = definition.getProcName();
    if (proc == null) {
      throw new IllegalArgumentException(
          "Report " + definition.getId() + " has no extract proc");
    }
    // Positional string args after the OUT ref-cursor (placeholder 1). Each report's proc takes a
    // different list, so the binding is built per definition rather than hard-coded.
    List<String> args = bindArgs(definition, request);
    String call = "{call " + proc + "(?" + ",?".repeat(args.size()) + ")}";
    return executeCall(
        call,
        cs -> {
          registerOutCursor(cs, 1);
          for (int i = 0; i < args.size(); i++) {
            cs.setString(i + 2, args.get(i));
          }
        },
        cs -> readExtract(cs, 1));
  }

  /**
   * Positional proc arguments (after the OUT cursor) for the given report.
   *
   * <ul>
   *   <li>Biodiversity 001-005 ({@code freprpt_bio_*}): {@code (p_org_unit_code, p_opening,
   *       p_start_year, p_resource_val)}.</li>
   *   <li>CHR FREPRPT022 ({@code freprpt_chr_extract}): {@code (p_org_unit_code, p_master_list,
   *       p_master_list, p_checklist_status_code, p_resource_val, p_user_id)} — the master-list year
   *       is passed twice (legacy from/to) and the proc takes the logged user.</li>
   * </ul>
   */
  List<String> bindArgs(ReportDefinition definition, ReportRequest request) {
    return switch (definition) {
      case CHR_DATA_EXTRACT -> {
        String masterList = orElseAll(request.masterListYear());
        yield List.of(
            orElseAll(request.orgUnitCode()),
            masterList,
            masterList,
            orElseAll(request.checklistStatus()),
            orElseAll(request.resourceValueStatus()),
            currentUserId());
      }
      default -> List.of(
          orElseAll(request.orgUnitCode()),
          StringUtils.hasText(request.openingId()) ? request.openingId().trim() : "",
          orElseAll(request.masterListYear()),
          orElseAll(request.resourceValueStatus()));
    };
  }

  private String currentUserId() {
    String userId = loggedUserHelper.getLoggedUserId();
    return userId == null ? "" : userId;
  }

  private static ReportExtract readExtract(CallableStatement cs, int index) throws SQLException {
    Object obj = cs.getObject(index);
    if (!(obj instanceof ResultSet rs)) {
      return new ReportExtract(List.of(), List.of());
    }
    try (ResultSet auto = rs) {
      ResultSetMetaData md = auto.getMetaData();
      int columnCount = md.getColumnCount();
      List<String> columns = new ArrayList<>(columnCount);
      for (int i = 1; i <= columnCount; i++) {
        columns.add(md.getColumnLabel(i).toLowerCase(Locale.ROOT));
      }
      List<List<String>> rows = new ArrayList<>();
      while (auto.next()) {
        List<String> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
          Object value = auto.getObject(i);
          row.add(value == null ? null : String.valueOf(value));
        }
        rows.add(row);
      }
      return new ReportExtract(columns, rows);
    }
  }

  private static String orElseAll(String value) {
    return StringUtils.hasText(value) ? value.trim() : ALL;
  }
}
