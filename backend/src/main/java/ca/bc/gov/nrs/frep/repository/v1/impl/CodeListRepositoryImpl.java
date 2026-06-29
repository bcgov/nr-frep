package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import ca.bc.gov.nrs.frep.struct.v1.frep.BecRow;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps Oracle package {@code FREP_CODE_LISTS} (legacy:
 * pkgdefinitions/PkgFrepCodeLists.java). Each method calls a procedure that
 * returns a single REF CURSOR. Cursor row column names vary per lookup, so
 * rows are surfaced as generic {@code Map} instances.
 */
@Repository
public class CodeListRepositoryImpl extends AbstractFrepRepository implements CodeListRepository {

  static final String PACKAGE_NAME = "FREP_CODE_LISTS";
  private static final String BGC_SEARCH_PACKAGE = "FREP_52_BGC_SEARCH";
  // Corporate forestry code lists for the opening-search dropdowns, owned by THE (needs EXECUTE
  // grant). Each proc returns a single OUT ref cursor of (code, description).
  private static final String SIL_CODE_PACKAGE = "SIL_CODE_LISTS_V002";

  public CodeListRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  @Override
  public List<Map<String, Object>> getBlockStatusCode() {
    return executeCall("{call " + SIL_CODE_PACKAGE + ".GET_BLOCK_STATUS(?)}",
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  @Override
  public List<Map<String, Object>> getOpenCategoryCode() {
    return executeCall("{call " + SIL_CODE_PACKAGE + ".GET_OPEN_CATEGORY(?)}",
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  @Override
  public List<Map<String, Object>> getOpeningStatusCode() {
    return executeCall("{call " + SIL_CODE_PACKAGE + ".GET_OPENING_STATUS(?)}",
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Forest-district org units for dropdowns and reference lookups.
   *
   * <p>Legacy procedure {@code get_district_org_unit_code} returns
   * {@code code} = {@code org_unit_no} and {@code description} =
   * {@code org_unit_code || ' - ' || org_unit_name}.
   */
  public List<Map<String, Object>> getDistrictOrgUnitCode() {
    String call = "{call " + PACKAGE_NAME + ".get_district_org_unit_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Master list (evaluation) years for dropdowns and reference lookups.
   *
   * <p>Legacy procedure {@code get_masterlist_year_code} returns
   * {@code code} = {@code effective_year} and {@code description} =
   * {@code effective_year || '/' || (effective_year + 1)}, ordered by
   * {@code code} descending.
   */
  public List<Map<String, Object>> getMasterListYearCode() {
    String call = "{call " + PACKAGE_NAME + ".get_masterlist_year_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * FREP protocol (resource value type) codes for dropdowns and filters.
   *
   * <p>Legacy procedure {@code get_resource_value} returns
   * {@code code} = {@code frep_resource_value_type_code} and
   * {@code description}, ordered by {@code description}.
   */
  public List<Map<String, Object>> getResourceValue() {
    String call = "{call " + PACKAGE_NAME + ".get_resource_value(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Site-resource rejection reason codes for the FREP110 rejection-reason dropdown.
   *
   * <p>Legacy procedure {@code get_site_resource_reason_code} returns
   * {@code frep_site_resource_reason_code} (code) and {@code description},
   * from table {@code frep_site_resource_reason_code}.
   */
  public List<Map<String, Object>> getSiteResourceReasonCode() {
    String call = "{call " + PACKAGE_NAME + ".get_site_resource_reason_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Riparian stream RMA class codes for the FREP230 stream-class dropdowns.
   *
   * <p>Legacy procedure {@code get_stream_class_code} returns
   * {@code code} = {@code riparian_stream_rma_class_code} and
   * {@code description} = {@code code || ' - ' || description}, from table
   * {@code riparian_stream_rma_class_code} (only non-expired rows).
   */
  public List<Map<String, Object>> getStreamClassCode() {
    String call = "{call " + PACKAGE_NAME + ".get_stream_class_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Site-access codes for the FREP301 Administration "Access type" dropdown.
   *
   * <p>Legacy procedure {@code get_site_access_code} returns {@code code} =
   * {@code frep_site_access_code} and {@code description}, from table
   * {@code frep_site_access_code}.
   */
  public List<Map<String, Object>> getSiteAccessCode() {
    String call = "{call " + PACKAGE_NAME + ".get_site_access_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Site-evaluation (rating) codes for the FREP210 Opening "Rating" dropdown.
   *
   * <p>Legacy procedure {@code get_evaluation_code} returns {@code code} =
   * {@code frep_site_evaluation_code} and {@code description}, from table
   * {@code frep_site_evaluation_code}, ordered E/W/M/P/U (rating 1-5).
   */
  public List<Map<String, Object>> getEvaluationCode() {
    String call = "{call " + PACKAGE_NAME + ".get_evaluation_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Biodiversity stratum-type codes for the FREP211 "Stratum type" dropdown.
   *
   * <p>Legacy procedure {@code get_stratum_type_code} returns {@code code} =
   * {@code biodiversity_strata_type_code} and {@code description} =
   * {@code code || ' - ' || description}, from table {@code biodiversity_strata_type_code}.
   */
  public List<Map<String, Object>> getStratumTypeCode() {
    String call = "{call " + PACKAGE_NAME + ".get_stratum_type_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Resource-value status codes for the biodiversity data-extract report filter
   * ({@code p_resource_val}). Direct SELECT (no proc), mirroring the legacy JCRS input control:
   * {@code frep_resource_value_stat_code} + {@code description}, excluding {@code REJ}.
   */
  public List<Map<String, Object>> getResourceValueStatusCode() {
    return jdbcTemplate.query(
        "SELECT frep_resource_value_stat_code AS code, description "
            + "FROM the.frep_resource_value_stat_code "
            + "WHERE frep_resource_value_stat_code <> 'REJ' ORDER BY description",
        (rs, n) -> {
          Map<String, Object> row = new LinkedHashMap<>(2);
          row.put("code", rs.getString("code"));
          row.put("description", rs.getString("description"));
          return row;
        });
  }

  /**
   * Checklist-status options for the CHR data-extract report filter (legacy FREPRPT022
   * {@code p_checklist_status_code} input control). Returns {@code code} =
   * {@code frep_checklist_status_code} + {@code description}.
   */
  public List<Map<String, Object>> getChecklistStatusCode() {
    return jdbcTemplate.query(
        "SELECT frep_checklist_status_code AS code, description "
            + "FROM the.frep_checklist_status_code ORDER BY description",
        (rs, n) -> {
          Map<String, Object> row = new LinkedHashMap<>(2);
          row.put("code", rs.getString("code"));
          row.put("description", rs.getString("description"));
          return row;
        });
  }

  /**
   * Tree-species codes for the FREP212 plot Stand / CWD "Spp." dropdowns.
   *
   * <p>Legacy procedure {@code get_frep_species_code} returns {@code code} =
   * {@code frep_tree_species_code} and {@code description}, from table
   * {@code frep_tree_species_code}.
   */
  public List<Map<String, Object>> getFrepSpeciesCode() {
    String call = "{call " + PACKAGE_NAME + ".get_frep_species_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Wildlife-tree decay-class codes for the FREP212 Stand table "WT Class" dropdown.
   *
   * <p>Legacy procedure {@code get_wildlife_tree_decay_code} returns {@code code} =
   * {@code wildlife_tree_decay_class_code} and {@code description}.
   */
  public List<Map<String, Object>> getWildlifeTreeDecayCode() {
    String call = "{call " + PACKAGE_NAME + ".get_wildlife_tree_decay_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * CWD decay-class codes for the FREP212 Coarse Woody Debris "Decay Class" dropdown.
   *
   * <p>Legacy procedure {@code get_cwd_decay_class_code} returns {@code code} =
   * {@code cwd_decay_class_code} and {@code description}.
   */
  public List<Map<String, Object>> getCwdDecayClassCode() {
    String call = "{call " + PACKAGE_NAME + ".get_cwd_decay_class_code(?)}";
    return executeCall(call,
        cs -> registerOutCursor(cs, 1),
        cs -> readCursor(cs, 1, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * Evaluator user-ids for the FREP212 "Evaluated By" dropdown — the checklist's saved team
   * members for the given protocol.
   *
   * <p>Legacy procedure {@code get_evaluator_code(p_checklist_id, p_resource_type_code, cursor)}
   * returns {@code code} = {@code evaluator_userid} and {@code description} = the userid with the
   * {@code IDIR\} prefix stripped, from {@code <protocol>_evaluator_name}.
   */
  public List<Map<String, Object>> getEvaluatorCode(String checklistId, String resourceType) {
    String call = "{call " + PACKAGE_NAME + ".get_evaluator_code(?,?,?)}";
    return executeCall(call,
        cs -> {
          cs.setString(1, checklistId);
          cs.setString(2, resourceType);
          registerOutCursor(cs, 3);
        },
        cs -> readCursor(cs, 3, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * FREP checklist answer codes (Yes/No/etc.) for indicator dropdowns such as the
   * FREP230 invasive-plant answer.
   *
   * <p>Legacy procedure {@code get_checklist_answer_code(p_exclude_answer_code, cursor)}
   * returns {@code code} = {@code frep_checklist_answer_code} and {@code description},
   * excluding the supplied code (pass {@code ""} to return every answer).
   */
  public List<Map<String, Object>> getChecklistAnswerCode(String excludeAnswerCode) {
    String call = "{call " + PACKAGE_NAME + ".get_checklist_answer_code(?,?)}";
    // The proc filters WHERE frep_checklist_answer_code != p_exclude. An empty string binds as NULL
    // in Oracle, making the comparison UNKNOWN for every row (returns nothing). When no exclusion is
    // wanted, pass a sentinel that matches no real answer code so the full list comes back.
    String exclude = (excludeAnswerCode == null || excludeAnswerCode.isBlank())
        ? "~~~"
        : excludeAnswerCode;
    return executeCall(call,
        cs -> {
          cs.setString(1, exclude);
          registerOutCursor(cs, 2);
        },
        cs -> readCursor(cs, 2, CodeListRepositoryImpl::rowToMap));
  }

  /**
   * BEC catalogue search for the FREP211 BEC picker via {@code FREP_52_BGC_SEARCH.mainline}
   * ({@code p_action='GET'}). 10 params: action + the 7 BEC criteria + error_message are all IN OUT
   * VARCHAR (the proc uppercases / strips LIKE predicates on the criteria in place); the result set
   * is the OUT ref cursor at param 10. Each criterion is optional (blank → matches everything).
   * Mirrors legacy {@code Sil52BgcSearchBeanJDBCAdaptor}.
   */
  public List<BecRow> searchBec(String zone, String subzone, String variant, String phase,
      String siteSeries, String siteSeriesPhase, String seral) {
    String call = "{call " + BGC_SEARCH_PACKAGE + ".mainline(?,?,?,?,?,?,?,?,?,?)}";
    return executeCall(call,
        cs -> {
          setInOutString(cs, 1, "GET");
          setInOutString(cs, 2, zone);
          setInOutString(cs, 3, subzone);
          setInOutString(cs, 4, variant);
          setInOutString(cs, 5, phase);
          setInOutString(cs, 6, siteSeries);
          setInOutString(cs, 7, siteSeriesPhase);
          setInOutString(cs, 8, seral);
          setInOutString(cs, 9, null);
          registerOutCursor(cs, 10);
        },
        cs -> {
          throwIfError(BGC_SEARCH_PACKAGE, "mainline", cs.getString(9));
          return readCursor(cs, 10, CodeListRepositoryImpl::becRow);
        });
  }

  private static BecRow becRow(ResultSet rs) throws java.sql.SQLException {
    return new BecRow(
        rs.getString("BGC_ZONE_CODE"),
        rs.getString("BGC_SUBZONE_CODE"),
        rs.getString("BGC_VARIANT"),
        rs.getString("BGC_PHASE"),
        rs.getString("BEC_SITE_SERIES_CD"),
        rs.getString("BEC_SITE_SERIES_PHASE_CD"),
        rs.getString("BEC_SERAL"),
        rs.getString("SITE_SERIES_DESC"));
  }

  private static Map<String, Object> rowToMap(ResultSet rs) throws java.sql.SQLException {
    ResultSetMetaData md = rs.getMetaData();
    Map<String, Object> row = new LinkedHashMap<>(md.getColumnCount());
    for (int i = 1; i <= md.getColumnCount(); i++) {
      row.put(md.getColumnLabel(i), rs.getObject(i));
    }
    return row;
  }
}
