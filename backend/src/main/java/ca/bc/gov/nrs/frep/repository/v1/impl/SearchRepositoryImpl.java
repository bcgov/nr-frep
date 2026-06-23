package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.SearchRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import oracle.jdbc.OracleConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps legacy FREP400 checklist search and FREP410 client search procedures.
 */
@Repository
public class SearchRepositoryImpl extends AbstractFrepRepository implements SearchRepository {

  static final String CHECKLIST_SEARCH_PROC = "FREP_400_CHECKLIST_SEARCH";
  static final String CLIENT_SEARCH_PROC = "FREP_410_CLIENT_SEARCH";
  static final String CHECKLIST_SEARCH_TYPE = "THE.FREP_CHKLST_SEARCH_VW_OBJECT";
  static final String CHECKLIST_SEARCH_ARRAY = "THE.FREP_CHKLST_SEARCH_VW_VARRAY";
  static final String CLIENT_SEARCH_TYPE = "THE.FREP_CLIENT_SEARCH_VW_OBJECT";
  static final String CLIENT_SEARCH_ARRAY = "THE.FREP_CLIENT_SEARCH_VW_VARRAY";

  private static final Logger log = LoggerFactory.getLogger(SearchRepositoryImpl.class);

  /** Cursor batch size for the streaming export (Oracle defaults to 10 — too chatty for large pulls). */
  private static final int STREAM_FETCH_SIZE = 1000;
  /** Hard safety cap on a single CSV export, so an unfiltered search can't stream an unbounded file. */
  static final int MAX_EXPORT_ROWS = 100_000;

  private final NamedParameterJdbcTemplate namedJdbc;
  /** Dedicated template for the streaming export: server-side cursor (fetch size) + the max-rows cap. */
  private final NamedParameterJdbcTemplate streamingJdbc;

  public SearchRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
    this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    JdbcTemplate streamingTemplate = new JdbcTemplate(
        Objects.requireNonNull(jdbcTemplate.getDataSource(), "oracleJdbcTemplate has no DataSource"));
    streamingTemplate.setFetchSize(STREAM_FETCH_SIZE);
    streamingTemplate.setMaxRows(MAX_EXPORT_ROWS);
    this.streamingJdbc = new NamedParameterJdbcTemplate(streamingTemplate);
  }

  /** Maps a checklist-search result-set row. Shared by the paged and streaming reads. */
  private static final RowMapper<ChecklistSearchRow> CHECKLIST_ROW_MAPPER = (rs, n) -> new ChecklistSearchRow(
      clean(rs.getString("checklist_id")),
      clean(rs.getString("protocol_code")),
      clean(rs.getString("protocol_name")),
      clean(rs.getString("effective_year")),
      clean(rs.getString("org_unit_code")),
      clean(rs.getString("licence_id")),
      clean(rs.getString("cutting_permit_id")),
      clean(rs.getString("cut_block_id")),
      clean(rs.getString("opening_id")),
      clean(rs.getString("client_number")),
      clean(rs.getString("evaluation_date")),
      clean(rs.getString("evaluator_userid")),
      clean(rs.getString("checklist_status_code")));

  /**
   * Inner checklist-search query, ported verbatim from {@code FREP_CHECKLIST_SEARCH.search} (the
   * {@code checklist_tbls} UNION across water/riparian/biodiversity/CHR, the {@code for_cli_audit}
   * join, the legacy {@code (+)} outer joins, region rollup, {@code LPAD} client, IDIR-prefix strip)
   * but as a native query bound by name — so it can be paginated with {@code OFFSET/FETCH} and counted
   * without the legacy VARRAY cap. {@code DISTINCT} applies to the whole row, matching the proc.
   *
   * <p>The legacy proc runs with definer's rights as {@code THE}, so its unqualified names resolve in
   * THE's namespace. This query runs as the connecting app user, so {@code THE}-owned tables are
   * explicitly schema-qualified. {@code for_cli_audit} is left UNQUALIFIED: it is a PUBLIC SYNONYM (not a
   * THE table), so {@code THE.for_cli_audit} would raise ORA-00942 — the bare name hits the synonym.
   *
   * <p>NOTE: the app user must hold SELECT on the {@code FOR_CLI_AUDIT} synonym's target table. Without
   * that grant this query raises ORA-00942 on {@code for_cli_audit} (the legacy proc only reached it via
   * THE's definer rights). If the grant cannot be added, the {@code for_cli_audit} join and its two
   * {@code org_unit_no = add_org_unit / = update_org_unit} predicates can be removed — they contribute no
   * output columns and act only as an org-unit filter — but that may change which checklists return.
   */
  private static final String CHECKLIST_SEARCH_INNER = """
      SELECT DISTINCT
             checklist_tbls.checklist_id                          AS checklist_id
           , frv.frep_resource_value_type_code                    AS protocol_code
           , frvtc.description                                    AS protocol_name
           , fss.effective_year                                   AS effective_year
           , ou.org_unit_code                                     AS org_unit_code
           , fss.forest_file_id                                   AS licence_id
           , fss.cutting_permit_id                                AS cutting_permit_id
           , fss.cut_block_id                                     AS cut_block_id
           , fss.opening_id                                       AS opening_id
           , fss.client_number                                    AS client_number
           , TO_CHAR(checklist_tbls.evaluation_date, 'YYYY-MM-DD') AS evaluation_date
           , REPLACE(checklist_tbls.evaluator_userid, 'IDIR\\')    AS evaluator_userid
           , checklist_tbls.frep_checklist_status_code            AS checklist_status_code
        FROM THE.frep_evaluation_year fey
           , THE.frep_selected_site fss
           , THE.org_unit ou
           --, for_cli_audit fca
           , THE.frep_resource_value frv
           , ( SELECT DISTINCT wc.water_checklist_id checklist_id, wc.frep_checklist_status_code,
                      wc.evaluation_date evaluation_date, wen.evaluator_userid, wc.frep_resource_value_id
                 FROM THE.water_checklist wc, THE.water_evaluator_name wen
                WHERE wc.water_checklist_id = wen.water_checklist_id(+)
                  AND wen.evaluator_team_lead_ind(+) = 'Y'
               UNION
               SELECT DISTINCT rc.riparian_checklist_id, rc.frep_checklist_status_code,
                      rc.evaluation_date, ren.evaluator_userid, rc.frep_resource_value_id
                 FROM THE.riparian_checklist rc, THE.riparian_evaluator_name ren
                WHERE rc.riparian_checklist_id = ren.riparian_checklist_id(+)
                  AND ren.evaluator_team_lead_ind(+) = 'Y'
               UNION
               SELECT DISTINCT bc.biodiversity_checklist_id, bc.frep_checklist_status_code,
                      bc.evaluation_date, ben.evaluator_userid, bc.frep_resource_value_id
                 FROM THE.biodiversity_checklist bc, THE.biodiversity_evaluator_name ben
                WHERE bc.biodiversity_checklist_id = ben.biodiversity_checklist_id(+)
                  AND ben.evaluator_team_lead_ind(+) = 'Y'
               UNION
               SELECT DISTINCT chr.chr_checklist_id, chr.frep_checklist_status_code,
                      chr.evaluation_date, chr.assessed_by, chr.frep_resource_value_id
                 FROM THE.chr_checklist chr
             ) checklist_tbls
           , THE.frep_resource_value_type_code frvtc
       WHERE fey.effective_year = fss.effective_year
         AND ou.org_unit_no = fss.org_unit_no
         --AND ou.org_unit_no = fca.add_org_unit
         --AND ou.org_unit_no = fca.update_org_unit
         AND fss.frep_selected_site_id = frv.frep_selected_site_id
         AND frv.frep_resource_value_id = checklist_tbls.frep_resource_value_id
         AND frvtc.frep_resource_value_type_code = frv.frep_resource_value_type_code
         AND (fss.effective_year = :effectiveYear OR :effectiveYear IS NULL)
         AND (ou.org_unit_no = :orgUnit OR ou.rollup_region_no = :orgUnit OR :orgUnit IS NULL)
         AND (fss.opening_id = :openingId OR :openingId IS NULL)
         AND (checklist_tbls.frep_checklist_status_code = :statusCode OR :statusCode IS NULL)
         AND (checklist_tbls.checklist_id = :checklistId OR :checklistId IS NULL)
         AND (UPPER(fss.forest_file_id) = UPPER(:licenceId) OR :licenceId IS NULL)
         AND (UPPER(fss.cut_block_id) = UPPER(:cutBlockId) OR :cutBlockId IS NULL)
         AND (UPPER(fss.cutting_permit_id) = UPPER(:cuttingPermitId) OR :cuttingPermitId IS NULL)
         AND (fss.client_number = LPAD(:clientNumber, 8, '0') OR :clientNumber IS NULL)
         AND (checklist_tbls.evaluation_date >= TO_DATE(:evalFrom, 'YYYY-MM-DD') OR :evalFrom IS NULL)
         AND (checklist_tbls.evaluation_date <= TO_DATE(:evalTo, 'YYYY-MM-DD') OR :evalTo IS NULL)
         AND (frv.frep_resource_value_type_code = :protocolType OR :protocolType IS NULL)
      """;

  @Override
  public long countChecklists(ChecklistSearchCriteria criteria) {
    Long count = namedJdbc.queryForObject(
        "SELECT COUNT(*) FROM (" + CHECKLIST_SEARCH_INNER + ")",
        checklistParams(criteria),
        Long.class);
    return count == null ? 0L : count;
  }

  @Override
  public List<ChecklistSearchRow> searchChecklistsPage(
      ChecklistSearchCriteria criteria, int offset, int pageSize, String orderByColumn, boolean descending) {
    String sql = "SELECT * FROM (" + CHECKLIST_SEARCH_INNER + ") "
        + orderByClause(orderByColumn, descending)
        + "OFFSET :offset ROWS FETCH NEXT :pageSize ROWS ONLY";
    MapSqlParameterSource params = checklistParams(criteria)
        .addValue("offset", offset)
        .addValue("pageSize", pageSize);
    return namedJdbc.query(sql, params, CHECKLIST_ROW_MAPPER);
  }

  @Override
  public long streamChecklists(
      ChecklistSearchCriteria criteria, String orderByColumn, boolean descending,
      Consumer<ChecklistSearchRow> consumer) {
    String sql = "SELECT * FROM (" + CHECKLIST_SEARCH_INNER + ") " + orderByClause(orderByColumn, descending);
    long[] streamed = {0L};
    streamingJdbc.query(sql, checklistParams(criteria), rs -> {
      consumer.accept(CHECKLIST_ROW_MAPPER.mapRow(rs, (int) streamed[0]));
      streamed[0]++;
    });
    if (streamed[0] >= MAX_EXPORT_ROWS) {
      log.warn("Checklist-search CSV export reached the {}-row safety cap; the file may be truncated.",
          MAX_EXPORT_ROWS);
    }
    return streamed[0];
  }

  /** Deterministic ORDER BY: the (validated) sort column, then a stable tiebreaker. */
  private static String orderByClause(String orderByColumn, boolean descending) {
    return "ORDER BY " + orderByColumn + (descending ? " DESC" : " ASC") + ", opening_id, checklist_id ";
  }

  /** Binds the OR-NULL filter values once each (named params are reused across their occurrences). */
  private static MapSqlParameterSource checklistParams(ChecklistSearchCriteria c) {
    return new MapSqlParameterSource()
        .addValue("effectiveYear", c.effectiveYear())
        .addValue("orgUnit", c.orgUnitNo())
        .addValue("openingId", c.openingId())
        .addValue("statusCode", c.checklistStatusCode())
        .addValue("checklistId", c.checklistId())
        .addValue("licenceId", c.licenceId())
        .addValue("cutBlockId", c.cutBlockId())
        .addValue("cuttingPermitId", c.cuttingPermitId())
        .addValue("clientNumber", c.clientNumber())
        .addValue("evalFrom", c.evaluationDateFrom())
        .addValue("evalTo", c.evaluationDateTo())
        .addValue("protocolType", c.protocolTypeCode());
  }

  /** Trims and strips a trailing {@code .0} that Oracle JDBC can append to NUMBER columns. */
  private static String clean(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.endsWith(".0")) {
      try {
        Double.parseDouble(trimmed);
        return trimmed.substring(0, trimmed.length() - 2);
      } catch (NumberFormatException ignored) {
        // keep original
      }
    }
    return trimmed;
  }

  /**
   * Runs a checklist search.
   *
   * <p>Legacy equivalent: {@code Frep400DataManager.getSearchChecklists}.
   */
  public List<ChecklistSearchRow> searchChecklists(ChecklistSearchCriteria criteria) {
    String call = "{call " + CHECKLIST_SEARCH_PROC + "(?,?)}";
    return executeCall(call, cs -> {
      cs.setObject(1, createChecklistSearchStruct(cs, criteria));
      cs.registerOutParameter(2, Types.ARRAY, CHECKLIST_SEARCH_ARRAY);
    }, cs -> readChecklistSearchArray(cs.getArray(2)));
  }

  /**
   * Runs a client search.
   *
   * <p>Legacy equivalent: {@code Frep410DataManager.getSearchClients}.
   */
  public List<ClientSearchRow> searchClients(ClientSearchCriteria criteria) {
    String call = "{call " + CLIENT_SEARCH_PROC + "(?,?)}";
    return executeCall(call, cs -> {
      cs.setObject(1, createClientSearchStruct(
          cs,
          criteria.clientNumber(),
          criteria.clientAcronym(),
          criteria.clientName(),
          criteria.legalFirstName(),
          criteria.legalMiddleName()));
      cs.registerOutParameter(2, Types.ARRAY, CLIENT_SEARCH_ARRAY);
    }, cs -> readClientSearchArray(cs.getArray(2)));
  }

  private static Struct createChecklistSearchStruct(
      CallableStatement cs,
      ChecklistSearchCriteria criteria
  ) throws SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    Object[] attrs = new Object[18];
    attrs[0] = structValue(criteria.checklistId());
    attrs[3] = structValue(criteria.protocolTypeCode());
    attrs[5] = structValue(criteria.effectiveYear());
    attrs[6] = structValue(criteria.openingId());
    attrs[7] = structValue(criteria.orgUnitNo());
    attrs[9] = structValue(criteria.checklistStatusCode());
    attrs[10] = structValue(criteria.licenceId());
    attrs[11] = structValue(criteria.cutBlockId());
    attrs[12] = structValue(criteria.cuttingPermitId());
    attrs[13] = structValue(criteria.clientNumber());
    attrs[14] = dateStructValue(criteria.evaluationDateFrom());
    attrs[15] = dateStructValue(criteria.evaluationDateTo());
    return connection.createStruct(CHECKLIST_SEARCH_TYPE, attrs);
  }

  private static Struct createClientSearchStruct(
      CallableStatement cs,
      String clientNumber,
      String clientAcronym,
      String clientName,
      String legalFirstName,
      String legalMiddleName
  ) throws SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    Object[] attrs = new Object[10];
    attrs[0] = structValue(clientNumber);
    attrs[1] = structValue(clientAcronym);
    attrs[3] = structValue(clientName);
    attrs[4] = structValue(legalFirstName);
    attrs[5] = structValue(legalMiddleName);
    return connection.createStruct(CLIENT_SEARCH_TYPE, attrs);
  }

  private static List<ChecklistSearchRow> readChecklistSearchArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object[] elements = (Object[]) array.getArray();
    List<ChecklistSearchRow> rows = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (element instanceof Struct struct) {
        rows.add(fromChecklistSearchStruct(struct));
      }
    }
    return rows;
  }

  private static List<ClientSearchRow> readClientSearchArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object[] elements = (Object[]) array.getArray();
    List<ClientSearchRow> rows = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (element instanceof Struct struct) {
        rows.add(fromClientSearchStruct(struct));
      }
    }
    return rows;
  }

  static ChecklistSearchRow fromChecklistSearchStruct(Struct struct) throws SQLException {
    Object[] attrs = struct.getAttributes();
    return new ChecklistSearchRow(
        stringAttr(attrs, 0),
        stringAttr(attrs, 3),
        stringAttr(attrs, 4),
        stringAttr(attrs, 5),
        stringAttr(attrs, 8),
        stringAttr(attrs, 10),
        stringAttr(attrs, 12),
        stringAttr(attrs, 11),
        stringAttr(attrs, 6),
        stringAttr(attrs, 13),
        formatEvaluationDate(stringAttr(attrs, 16)),
        stringAttr(attrs, 17),
        stringAttr(attrs, 9)
    );
  }

  static ClientSearchRow fromClientSearchStruct(Struct struct) throws SQLException {
    Object[] attrs = struct.getAttributes();
    return new ClientSearchRow(
        stringAttr(attrs, 0),
        stringAttr(attrs, 1),
        stringAttr(attrs, 2),
        stringAttr(attrs, 3),
        stringAttr(attrs, 6),
        stringAttr(attrs, 7),
        stringAttr(attrs, 8),
        stringAttr(attrs, 9)
    );
  }

  static String formatEvaluationDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    return raw.length() >= 11 ? raw.substring(0, 11).trim() : raw.trim();
  }

  private static Object structValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  /**
   * Marshals a {@code yyyy-MM-dd} criteria string into the DATE attribute format the
   * legacy struct uses ({@code Frep400DataManager} appends {@code " 00:00:00.0"} so
   * Oracle parses the value as a timestamp at midnight).
   */
  private static Object dateStructValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim() + " 00:00:00.0";
  }

  private static String stringAttr(Object[] attrs, int index) {
    if (attrs == null || index >= attrs.length || attrs[index] == null) {
      return "";
    }
    String value = attrs[index].toString().trim();
    if (value.endsWith(".0")) {
      try {
        Double.parseDouble(value);
        value = value.substring(0, value.length() - 2);
      } catch (NumberFormatException ignored) {
        // keep original string
      }
    }
    return value;
  }
}
