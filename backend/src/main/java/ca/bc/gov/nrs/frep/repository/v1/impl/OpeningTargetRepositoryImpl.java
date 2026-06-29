package ca.bc.gov.nrs.frep.repository.v1.impl;

import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.OpeningTargetRepository;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchCriteria;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchResult;
import java.sql.Types;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Opening search + targeting validation for the FREP200 "Add Target Site" flow.
 *
 * <p><b>Search</b> ports {@code SIL_56_OPEN_TEN_SRCH_V002.get} as a bind-parameterised native query
 * over {@code THE.opening op} ⋈ {@code THE.cut_block_open_admin prime} (the prime-licence row), with
 * the proc's conditional joins (a second {@code cut_block_open_admin cboa} for tenure/client/date
 * filters, {@code cut_block} for block status, {@code for_client_link} for client, milestone tables
 * for regen/free-growing dates) and matching semantics (wildcards on licence/cut-block/licensee id,
 * exact elsewhere; a non-blank opening id ignores all other filters; P87 union). Unlike the proc it
 * uses bind params instead of string concatenation, {@code TO_DATE} instead of
 * {@code sil_date_conversion}, and {@code OFFSET/FETCH} pagination + {@code COUNT(*)} for totals.
 *
 * <p>Grants: the app user needs SELECT on {@code THE.opening}, {@code THE.cut_block_open_admin},
 * {@code THE.cut_block}, {@code THE.for_client_link}, {@code THE.stocking_milestone} and
 * {@code THE.stocking_standard_unit} (the last four only exercised by the client/date filters).
 *
 * <p><b>Validation</b> calls {@code FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE}, which only validates
 * (district ownership + no active-harvest cut blocks) and returns a {@code ;}-separated error-code
 * string.
 */
@Repository
public class OpeningTargetRepositoryImpl extends AbstractFrepRepository
    implements OpeningTargetRepository {

  // Display columns, mirroring the SIL56 SELECT (tenure from the prime cboa row, opening attributes
  // from opening, the mapsheet-formatted opening number, DISTINCT to collapse multi-row joins).
  private static final String SELECT_COLS = """
      SELECT DISTINCT prime.forest_file_id                AS forest_file_id
           , prime.cutting_permit_id                      AS cutting_permit_id
           , prime.timber_mark                            AS timber_mark
           , prime.cut_block_id                           AS cut_block_id
           , LPAD(op.mapsheet_grid, 3) || op.mapsheet_letter || ' ' || LPAD(op.mapsheet_square, 3, 0)
             || ' ' || op.mapsheet_quad || DECODE(op.mapsheet_quad, NULL, NULL, '.')
             || op.mapsheet_sub_quad || ' ' || op.opening_number
                                                          AS opening_number
           , TO_CHAR(prime.disturbance_gross_area)        AS gross_area
           , op.open_category_code                        AS open_category_code
           , op.opening_status_code                       AS opening_status_code
           , op.amendment_ind                             AS amendment_ind
           , TO_CHAR(op.opening_id)                       AS opening_id
           , op.licensee_opening_id                       AS licensee_opening_id
           , TO_CHAR(op.admin_district_no)                AS admin_district_no
      """;

  private static final RowMapper<OpeningSearchResult> ROW_MAPPER = (rs, rowNum) ->
      new OpeningSearchResult(
          clean(rs.getString("opening_id")),
          clean(rs.getString("opening_number")),
          clean(rs.getString("forest_file_id")),
          clean(rs.getString("cutting_permit_id")),
          clean(rs.getString("timber_mark")),
          clean(rs.getString("cut_block_id")),
          clean(rs.getString("gross_area")),
          clean(rs.getString("open_category_code")),
          clean(rs.getString("opening_status_code")),
          clean(rs.getString("amendment_ind")),
          clean(rs.getString("licensee_opening_id")),
          clean(rs.getString("admin_district_no")));

  private final NamedParameterJdbcTemplate namedJdbc;

  public OpeningTargetRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
    this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  @Override
  public List<OpeningSearchResult> searchOpenings(
      OpeningSearchCriteria criteria, int offset, int pageSize) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    BuiltSearch built = buildSearch(criteria, params);
    String sql = "SELECT * FROM (" + built.innerSql() + ") ORDER BY " + built.orderColumn()
        + " OFFSET :offset ROWS FETCH NEXT :pageSize ROWS ONLY";
    params.addValue("offset", offset).addValue("pageSize", pageSize);
    return namedJdbc.query(sql, params, ROW_MAPPER);
  }

  @Override
  public long countOpenings(OpeningSearchCriteria criteria) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    BuiltSearch built = buildSearch(criteria, params);
    Long count =
        namedJdbc.queryForObject("SELECT COUNT(*) FROM (" + built.innerSql() + ")", params, Long.class);
    return count == null ? 0L : count;
  }

  /** The built inner query (page + count wrap it) and the order-by column. */
  record BuiltSearch(String innerSql, String orderColumn) {}

  /**
   * Build the SIL56 inner query for {@code criteria}, binding values into {@code params}. Mirrors the
   * proc's conditional joins and filter clauses; the page/count callers wrap the result.
   */
  static BuiltSearch buildSearch(OpeningSearchCriteria c, MapSqlParameterSource params) {
    StringBuilder from = new StringBuilder(" FROM opening op , cut_block_open_admin prime ");
    StringBuilder where = new StringBuilder(
        " WHERE prime.opening_id = op.opening_id AND prime.opening_prime_licence_ind = 'Y' ");
    StringBuilder whereClient = new StringBuilder();
    boolean includeP87 = false;

    if (isSet(c.openingId())) {
      // A non-blank opening id ignores every other filter (legacy behaviour).
      where.append(" AND op.opening_id = to_number(:openingId) ");
      params.addValue("openingId", c.openingId().trim());
    } else {
      boolean needClient = isSet(c.clientNumber());
      boolean needBlock = isSet(c.blockStatusSt());
      boolean needCboa = needClient || needBlock || isSet(c.forestFileId())
          || isSet(c.cuttingPermitId()) || isSet(c.timberMark()) || isSet(c.cutBlockId())
          || "Disturbance".equals(c.dateType());
      boolean milestone = "Regen Delay".equals(c.dateType()) || "Free Growing".equals(c.dateType());

      if (needCboa) {
        from.append(" , cut_block_open_admin cboa ");
        where.append(" AND cboa.opening_id = op.opening_id ");
        if (needClient) {
          from.append(" , for_client_link fcla , for_client_link fclo ");
          where.append(" AND fclo.file_client_type(+) = 'O' ")
              .append(" AND fclo.forest_file_id(+) = cboa.forest_file_id ")
              .append(" AND fclo.cutting_permit_id(+) = cboa.cutting_permit_id ")
              .append(" AND fclo.cut_block_id(+) = cboa.cut_block_id ")
              .append(" AND fcla.file_client_type(+) = 'A' ")
              .append(" AND fcla.forest_file_id(+) = cboa.forest_file_id ");
        }
        if (needBlock) {
          from.append(" , cut_block cb ");
          where.append(" AND cb.forest_file_id = cboa.forest_file_id ")
              .append(" AND cb.cutting_permit_id = cboa.cutting_permit_id ")
              .append(" AND cb.cut_block_id = cboa.cut_block_id ");
        }
      }
      if (milestone) {
        from.append(" , stocking_milestone sm , stocking_standard_unit ssu ");
        where.append(" AND ssu.opening_id = op.opening_id ")
            .append(" AND ssu.stocking_standard_unit_id = sm.stocking_standard_unit_id ");
      }

      appendFilters(c, where, params, needBlock);
      appendClientWhere(c, whereClient, params, needClient);
      includeP87 = "Y".equalsIgnoreCase(c.includeAllP87Ind());
    }

    String main = SELECT_COLS + from + where + whereClient;
    // P87 union: the same query (without the client filter) restricted to P87 openings.
    String inner = includeP87
        ? main + " UNION " + SELECT_COLS + from + where + " AND op.open_category_code = 'P87' "
        : main;
    String orderColumn = "L".equalsIgnoreCase(c.sortBy()) ? "forest_file_id" : "opening_number";
    return new BuiltSearch(inner, orderColumn);
  }

  private static void appendFilters(
      OpeningSearchCriteria c, StringBuilder where, MapSqlParameterSource params, boolean needBlock) {
    if (isSet(c.orgUnit())) {
      where.append(" AND op.admin_district_no = to_number(:orgUnit) ");
      params.addValue("orgUnit", c.orgUnit().trim());
    }
    if (needBlock) {
      where.append(" AND cb.block_status_st = :blockStatus ");
      params.addValue("blockStatus", c.blockStatusSt().trim());
    }
    if (isSet(c.openCategoryCode())) {
      where.append(" AND op.open_category_code = :openCategory ");
      params.addValue("openCategory", c.openCategoryCode().trim());
    }
    if (isSet(c.openingStatusCode())) {
      where.append(" AND op.opening_status_code = :openingStatus ");
      params.addValue("openingStatus", c.openingStatusCode().trim());
    }

    // Opening number: part 1 splits into mapsheet grid (first 3) + letter (last char); part 3 into
    // quad (first char) + sub-quad (last char); parts 2 and 4 map directly.
    String grid = head(c.openingNumber1(), 3);
    String letter = lastChar(c.openingNumber1());
    if (isSet(grid)) {
      where.append(" AND op.mapsheet_grid = :msGrid ");
      params.addValue("msGrid", grid);
    }
    if (isSet(letter)) {
      where.append(" AND op.mapsheet_letter = :msLetter ");
      params.addValue("msLetter", letter);
    }
    if (isSet(c.openingNumber2())) {
      where.append(" AND op.mapsheet_square = :msSquare ");
      params.addValue("msSquare", c.openingNumber2().trim());
    }
    String quad = head(c.openingNumber3(), 1);
    String subQuad = lastChar(c.openingNumber3());
    if (isSet(quad)) {
      where.append(" AND op.mapsheet_quad = :msQuad ");
      params.addValue("msQuad", quad);
    }
    if (isSet(subQuad)) {
      where.append(" AND op.mapsheet_sub_quad = :msSubQuad ");
      params.addValue("msSubQuad", subQuad);
    }
    if (isSet(c.openingNumber4())) {
      where.append(" AND op.opening_number = :openingNum4 ");
      params.addValue("openingNum4", c.openingNumber4().trim());
    }

    if (isSet(c.forestFileId())) {
      where.append(" AND cboa.forest_file_id LIKE :forestFileId ");
      params.addValue("forestFileId", c.forestFileId().trim().toUpperCase() + "%");
    }
    if (isSet(c.cuttingPermitId())) {
      where.append(" AND cboa.cutting_permit_id = :cuttingPermitId ");
      params.addValue("cuttingPermitId", c.cuttingPermitId().trim());
    }
    if (isSet(c.timberMark())) {
      where.append(" AND cboa.timber_mark = :timberMark ");
      params.addValue("timberMark", c.timberMark().trim());
    }
    if (isSet(c.cutBlockId())) {
      where.append(" AND cboa.cut_block_id LIKE :cutBlockId ");
      params.addValue("cutBlockId", "%" + c.cutBlockId().trim().toUpperCase() + "%");
    }
    if (isSet(c.licenseeOpeningId())) {
      where.append(" AND UPPER(op.licensee_opening_id) LIKE :licenseeOpeningId ");
      params.addValue("licenseeOpeningId", "%" + c.licenseeOpeningId().trim().toUpperCase() + "%");
    }

    appendDateFilter(c, where, params);
  }

  private static void appendDateFilter(
      OpeningSearchCriteria c, StringBuilder where, MapSqlParameterSource params) {
    switch (StringUtils.trimAllWhitespace(emptyIfNull(c.dateType()))) {
      case "Disturbance" -> {
        where.append(" AND cboa.disturbance_start_date "
            + "BETWEEN TO_DATE(:distStart, 'YYYY-MM-DD') AND TO_DATE(:distEnd, 'YYYY-MM-DD') ");
        params.addValue("distStart", c.distStartDate());
        params.addValue("distEnd", c.distEndDate());
      }
      case "RegenDelay" -> {
        where.append(" AND sm.due_late_date IS NOT NULL AND sm.silv_milestone_type_code = 'RG' "
            + "AND sm.due_late_date BETWEEN TO_DATE(:rgFrom, 'YYYY-MM-DD') AND TO_DATE(:rgTo, 'YYYY-MM-DD') ");
        params.addValue("rgFrom", c.dueLateDateFrom());
        params.addValue("rgTo", c.dueLateDateTo());
      }
      case "FreeGrowing" -> {
        where.append(" AND sm.silv_milestone_type_code = 'FG' "
            + "AND ((sm.due_early_date BETWEEN TO_DATE(:fgEarly, 'YYYY-MM-DD') AND TO_DATE(:fgLate, 'YYYY-MM-DD')) "
            + "OR (sm.due_late_date BETWEEN TO_DATE(:fgEarly, 'YYYY-MM-DD') AND TO_DATE(:fgLate, 'YYYY-MM-DD'))) ");
        params.addValue("fgEarly", c.fgDueEarlyDate());
        params.addValue("fgLate", c.fgDueLateDate());
      }
      case "Update" -> {
        // [from 00:00, next-day 00:00) covers the whole to-date, matching the legacy 23:59:59 bound.
        where.append(" AND op.update_timestamp >= TO_DATE(:updFrom, 'YYYY-MM-DD') "
            + "AND op.update_timestamp < TO_DATE(:updTo, 'YYYY-MM-DD') + 1 ");
        params.addValue("updFrom", c.updateDateFrom());
        params.addValue("updTo", c.updateDateTo());
      }
      default -> {
        // No date filter.
      }
    }
  }

  private static void appendClientWhere(
      OpeningSearchCriteria c, StringBuilder whereClient, MapSqlParameterSource params,
      boolean needClient) {
    if (!needClient) {
      return;
    }
    // Match either the licensee (fcla) or obligation-holder (fclo) client; the NVL clause undoes the
    // outer joins so only openings tied to the client survive (legacy comment).
    whereClient.append(" AND fcla.client_number(+) = :clientNumber ")
        .append(" AND fclo.client_number(+) = :clientNumber ")
        .append(" AND NVL(fclo.client_number, fcla.client_number) = :clientNumber ");
    params.addValue("clientNumber", c.clientNumber().trim());
    if (isSet(c.clientLocnCode())) {
      whereClient.append(" AND fcla.client_locn_code(+) = :clientLocn ")
          .append(" AND fclo.client_locn_code(+) = :clientLocn ")
          .append(" AND NVL(fclo.client_locn_code, fcla.client_locn_code) = :clientLocn ");
      params.addValue("clientLocn", c.clientLocnCode().trim());
    }
  }

  @Override
  public String validateTargetedSite(String openingId, String orgUnitNo) {
    // Mirrors the legacy FrepAcceptedSitesManager.addTargetedSite binding exactly: IN openingId,
    // IN orgUnitNo, OUT error message. The proc only validates and returns ;-separated error codes.
    return executeCall(
        "{call FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE (?,?,?)}",
        cs -> {
          cs.setString(1, openingId);
          cs.setString(2, orgUnitNo);
          cs.registerOutParameter(3, Types.VARCHAR);
        },
        cs -> emptyIfNull(cs.getString(3)));
  }

  private static boolean isSet(String value) {
    return StringUtils.hasText(value);
  }

  /** First {@code n} characters of a trimmed value, or "" when blank. */
  private static String head(String value, int n) {
    if (!isSet(value)) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.length() <= n ? trimmed : trimmed.substring(0, n);
  }

  /** Last character of a trimmed value, or "" when blank. */
  private static String lastChar(String value) {
    if (!isSet(value)) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.substring(trimmed.length() - 1);
  }

  /** Trim and strip a trailing {@code .0} that Oracle NUMBER→getString can produce. */
  private static String clean(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.endsWith(".0") ? trimmed.substring(0, trimmed.length() - 2) : trimmed;
  }
}
