package ca.bc.gov.nrs.frep.repository.frep;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps Oracle package {@code FREP_CODE_LISTS} (legacy:
 * pkgdefinitions/PkgFrepCodeLists.java). Each method calls a procedure that
 * returns a single REF CURSOR. Cursor row column names vary per lookup, so
 * rows are surfaced as generic {@code Map} instances.
 */
@Repository
@Profile("oracle")
public class CodeListRepository extends AbstractFrepRepository {

  static final String PACKAGE_NAME = "FREP_CODE_LISTS";

  public CodeListRepository(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
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
        cs -> readCursor(cs, 1, CodeListRepository::rowToMap));
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
        cs -> readCursor(cs, 1, CodeListRepository::rowToMap));
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
        cs -> readCursor(cs, 1, CodeListRepository::rowToMap));
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
