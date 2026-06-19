package ca.bc.gov.nrs.frep.repository.v1.impl;

import ca.bc.gov.nrs.frep.repository.v1.AcceptedSitesRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.AcceptedSiteRow;
import ca.bc.gov.nrs.frep.repository.v1.bean.MapExtent;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import oracle.jdbc.OracleConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps legacy package {@code FREP_200_ACCEPTED_SITES} (FREP200 Accepted Sites).
 */
@Repository
@Profile("oracle")
public class AcceptedSitesRepositoryImpl extends AbstractFrepRepository
    implements AcceptedSitesRepository {

  static final String PACKAGE_NAME = "FREP_200_ACCEPTED_SITES";
  static final String ARRAY_TYPE_NAME = "THE.FREP_ACC_SITES_VARRAY";

  public AcceptedSitesRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  @Override
  public List<AcceptedSiteRow> findAcceptedSites(String orgUnitNo, String effectiveYear) {
    String call = "{call " + PACKAGE_NAME + ".get (?,?,?,?,?,?,?,?,?,?,?,?,?,?)}";
    return executeCall(call, cs -> {
      cs.setString(1, orgUnitNo);
      cs.setString(2, effectiveYear);
      cs.registerOutParameter(3, Types.VARCHAR);
      cs.registerOutParameter(4, Types.NUMERIC);
      cs.registerOutParameter(5, Types.NUMERIC);
      cs.registerOutParameter(6, Types.NUMERIC);
      cs.registerOutParameter(7, Types.NUMERIC);
      cs.registerOutParameter(8, Types.NUMERIC);
      cs.registerOutParameter(9, Types.NUMERIC);
      cs.setString(10, "");
      cs.registerOutParameter(11, Types.VARCHAR);
      setEmptyAcceptedSitesArray(cs, 12);
      cs.registerOutParameter(12, Types.ARRAY, ARRAY_TYPE_NAME);
      cs.registerOutParameter(13, Types.NUMERIC);
      cs.registerOutParameter(14, Types.NUMERIC);
    }, cs -> {
      throwIfError(PACKAGE_NAME, "get", cs.getString(11));
      return readAcceptedSitesArray(cs.getArray(12));
    });
  }

  @Override
  public List<AcceptedSiteRow> findCulturalHeritageSites(String orgUnitNo, String effectiveYear) {
    return jdbcTemplate.query(
        CHR_ACCEPTED_SITES_SQL,
        (rs, rowNum) -> new AcceptedSiteRow(
            cleanString(rs.getString("checklist_id")),
            cleanString(rs.getString("checklist_type")),
            cleanString(rs.getString("sample_number")),
            cleanString(rs.getString("resource_value_stat_code")),
            cleanString(rs.getString("checklist_status_code")),
            cleanString(rs.getString("opening_number")),
            cleanString(rs.getString("opening_id")),
            cleanString(rs.getString("licence_id")),
            cleanString(rs.getString("cutting_permit_id")),
            cleanString(rs.getString("cut_block_id")),
            cleanString(rs.getString("harvest_complete_date"))),
        effectiveYear,
        orgUnitNo);
  }

  private static final String CHR_ACCEPTED_SITES_SQL = """
      SELECT cc.chr_checklist_id                       AS checklist_id
           , frvtc.description                         AS checklist_type
           , NULL                                      AS sample_number
           , frv.frep_resource_value_stat_code         AS resource_value_stat_code
           , cc.frep_checklist_status_code             AS checklist_status_code
           , THE.frep_formatted_mapsheet(fss.mapsheet_grid, fss.mapsheet_letter, fss.mapsheet_square,
                                         fss.mapsheet_quad, fss.mapsheet_sub_quad, fss.opening_number)
                                                       AS opening_number
           , fss.opening_id                            AS opening_id
           , fss.forest_file_id                        AS licence_id
           , fss.cutting_permit_id                     AS cutting_permit_id
           , fss.cut_block_id                          AS cut_block_id
           , TO_CHAR(fss.disturbance_end_date, 'YYYY-MM-DD')
                                                       AS harvest_complete_date
        FROM THE.frep_selected_site fss
           , THE.frep_resource_value frv
           , THE.chr_checklist cc
           , THE.frep_resource_value_type_code frvtc
       WHERE fss.frep_selected_site_id = frv.frep_selected_site_id
         AND frv.frep_resource_value_id = cc.frep_resource_value_id
         AND frv.frep_resource_value_type_code = frvtc.frep_resource_value_type_code
         AND fss.effective_year = to_number(?)
         AND fss.org_unit_no = to_number(?)
         AND frv.frep_resource_value_stat_code <> 'REJ'
         AND frv.frep_resource_value_type_code = 'CHR'
       ORDER BY fss.opening_id
      """;

  private static String cleanString(String value) {
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

  private static void setEmptyAcceptedSitesArray(CallableStatement cs, int index) throws SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    cs.setArray(index, connection.createOracleArray(ARRAY_TYPE_NAME, new Object[0]));
  }

  private static List<AcceptedSiteRow> readAcceptedSitesArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object[] elements = (Object[]) array.getArray();
    List<AcceptedSiteRow> rows = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (element instanceof Struct struct) {
        rows.add(fromStruct(struct));
      }
    }
    return rows;
  }

  /**
   * Attribute order matches {@code FREP_ACC_SITES_OBJECT} in legacy DDL.
   */
  static AcceptedSiteRow fromStruct(Struct struct) throws SQLException {
    Object[] attrs = struct.getAttributes();
    return new AcceptedSiteRow(
        stringAttr(attrs, 2),
        stringAttr(attrs, 4),
        stringAttr(attrs, 3),
        stringAttr(attrs, 5),
        stringAttr(attrs, 6),
        stringAttr(attrs, 8),
        stringAttr(attrs, 7),
        stringAttr(attrs, 9),
        stringAttr(attrs, 10),
        stringAttr(attrs, 11),
        stringAttr(attrs, 12)
    );
  }

  /**
   * Bounding box for an opening via the standalone legacy procedure {@code frep_map_bounding_values}
   * (1 IN {@code p_opening_id} NUMBER, 4 OUT VARCHAR2 corners). Returns an all-null {@link MapExtent}
   * when the opening has no {@code OPENING_MAP_IMAGE} row (the proc swallows NO_DATA_FOUND).
   */
  @Override
  public MapExtent getOpeningExtent(String openingId) {
    String call = "{call frep_map_bounding_values(?,?,?,?,?)}";
    return executeCall(call, cs -> {
      cs.setBigDecimal(1, new BigDecimal(openingId));
      cs.registerOutParameter(2, Types.VARCHAR);
      cs.registerOutParameter(3, Types.VARCHAR);
      cs.registerOutParameter(4, Types.VARCHAR);
      cs.registerOutParameter(5, Types.VARCHAR);
    }, cs -> new MapExtent(cs.getString(2), cs.getString(3), cs.getString(4), cs.getString(5)));
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
