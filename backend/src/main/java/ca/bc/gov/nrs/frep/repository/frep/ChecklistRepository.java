package ca.bc.gov.nrs.frep.repository.frep;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps legacy protocol-checklist GET procedures for BIO/SLB, RIP, and WTR.
 */
@Repository
@Profile("oracle")
public class ChecklistRepository extends AbstractFrepRepository {

  static final String BIO_OPENING_PACKAGE = "frep_210_bio_opening";
  static final String BIO_STRATUM_PACKAGE = "FREP_211_BioStratum";
  static final String BIO_PLOT_PACKAGE = "FREP_212_BioPlot";
  static final String RIP_STREAM_PACKAGE = "FREP_230_STRM_OPEN";
  static final String RIP_FIELD_PACKAGE = "FREP_231_FIELD_DATA";
  static final String RIP_OTHER_PACKAGE = "FREP_232_OTHER_INDS";
  static final String RIP_QUESTIONS_PACKAGE = "FREP_233_QUESTIONS";
  static final String RIP_IMPACTS_PACKAGE = "FREP_234_SPECIFIC_IMPACTS";
  static final String RIP_COMMENTS_PACKAGE = "FREP_235_FINAL_CMTS";
  static final String WTR_CHECKLIST_PROC = "FREP_250_WATER_CHKLST_GET";
  static final String WTR_SAMPLE_SITE_PROC = "FREP_251_SAMPLE_SITE_GET";
  static final String WTR_ASSESSMENT_PROC = "FREP_252_ASSESSMENT_GET";
  static final String WTR_RANGE_PROC = "FREP_253_RANGE_GET";
  static final String WTR_SUMMARY_PROC = "FREP_254_SUMMARY_GET";
  static final String TOMBSTONE_PROC = "FREP_TOMBSTONE_GET";

  static final String WTR_CHECKLIST_TYPE = "THE.FREP_WTR_CHKLST_OBJECT";
  static final String WTR_SAMPLE_SITE_TYPE = "THE.FREP_WTR_SAMPLE_SITE_OBJECT";
  static final String WTR_DISTURBANCE_ARRAY = "THE.FREP_WTR_DISTURBANCE_VARRAY";
  static final String WTR_ACCESS_ROAD_ARRAY = "THE.FREP_WTR_ACCESS_ROAD_VARRAY";
  static final String WTR_ASSESSMENT_ARRAY = "THE.FREP_WTR_ASSESSMENT_VW_VARRAY";
  static final String WTR_SUMMARY_ARRAY = "THE.FREP_WTR_SUMMARY_VW_VARRAY";
  static final String STAND_TABLE_ARRAY = "THE.FREP_STAND_TABLE_VARRAY";
  static final String CWD_TABLE_ARRAY = "THE.FREP_CWD_TABLE_VARRAY";
  static final String RIP_STREAM_EDGE_ARRAY = "THE.FREP_STRM_EDGE_MEASMNT_VARRAY";
  static final String RIP_POINT_IND_ARRAY = "THE.FREP_POINT_INDICATOR_VARRAY";
  static final String RIP_CONTINUOUS_IND_ARRAY = "THE.FREP_CONTINUOUS_IND_VARRAY";
  static final String RIP_OTHER_IND_ARRAY = "THE.FREP_OTHER_INDICATOR_VARRAY";
  static final String RIP_QUESTIONS_ARRAY = "THE.FREP_QUESTIONS_VARRAY";
  static final String RIP_NO_ANSWERS_ARRAY = "THE.FREP_NO_ANSWERS_VARRAY";

  private static final String EMPTY_RESOURCE_VALUE_ID = "";

  public ChecklistRepository(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  public ChecklistSectionData getBioOpening(String checklistId) {
    // Layout per legacy BiodiversityDataManager (deployed signature): 35 params, leading OUT block
    // 1-18, resource_value_id IN @19, checklist_id IN @20, then checklist fields 21-34, error @35.
    // The tombstone block (org unit, opening, licence, client, etc.) is joined through
    // frep_resource_value_id, so resolve the real id rather than passing it empty.
    String resourceValueId = resolveBioResourceValueId(checklistId);
    String call = callSql(BIO_OPENING_PACKAGE, "GET", 35);
    return executeCall(call, cs -> {
      for (int i = 1; i <= 18; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.setString(19, resourceValueId);
      cs.setString(20, checklistId);
      for (int i = 21; i <= 35; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
    }, cs -> {
      throwIfError(BIO_OPENING_PACKAGE, "GET", cs.getString(35));
      ChecklistHeaderData header = headerFromBioTombstone(cs, 21);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      // Header context fields the legacy Administration tombstone shows (proc params 1-14).
      putIfPresent(fields, "Org unit", cs.getString(2));
      putIfPresent(fields, "Opening ID", cs.getString(5));
      putIfPresent(fields, "Licence", cs.getString(7));
      putIfPresent(fields, "Cutting permit", cs.getString(9));
      putIfPresent(fields, "Cut block", cs.getString(10));
      putIfPresent(fields, "Client", cs.getString(13));
      putIfPresent(fields, "Client name", cs.getString(14));
      putIfPresent(fields, "Gross area", cs.getString(22));
      putIfPresent(fields, "Location description", cs.getString(23));
      putIfPresent(fields, "Net area", cs.getString(24));
      putIfPresent(fields, "Observed gross area", cs.getString(25));
      putIfPresent(fields, "Block reserve", cs.getString(26));
      putIfPresent(fields, "Sample reserve", cs.getString(27));
      putIfPresent(fields, "Innovative practice", cs.getString(28));
      putIfPresent(fields, "Innovative practice comment", cs.getString(29));
      putIfPresent(fields, "Invasive plant", cs.getString(30));
      putIfPresent(fields, "Invasive plant comment", cs.getString(31));
      putIfPresent(fields, "Rating", cs.getString(32));
      putIfPresent(fields, "Rationale", cs.getString(33));
      return ChecklistSectionData.of(
          header.mergedWith(getTombstone(checklistId, "SLB", resourceValueId)), fields);
    });
  }

  /** The checklist's resource value id (required by FREP_211's status SELECT). */
  private String resolveBioResourceValueId(String checklistId) {
    List<String> ids = jdbcTemplate.query(
        "SELECT frep_resource_value_id FROM the.biodiversity_checklist "
            + "WHERE biodiversity_checklist_id = ?",
        (rs, n) -> rs.getString(1),
        checklistId);
    return ids.isEmpty() ? "" : trimId(ids.get(0));
  }

  /**
   * The riparian checklist's resource value id. FREP_230's tombstone block (org unit, opening,
   * licence, client, etc.) is joined through {@code frep_selected_site} via this id, so passing it
   * empty leaves those header fields blank.
   */
  private String resolveRipResourceValueId(String checklistId) {
    List<String> ids = jdbcTemplate.query(
        "SELECT frep_resource_value_id FROM the.riparian_checklist "
            + "WHERE riparian_checklist_id = ?",
        (rs, n) -> rs.getString(1),
        checklistId);
    return ids.isEmpty() ? "" : trimId(ids.get(0));
  }

  /** The checklist's first stratum id (a checklist may have 0..n strata); "" when none. */
  private String resolveFirstBioStratumId(String checklistId) {
    List<String> ids = jdbcTemplate.query(
        "SELECT stratum_id FROM the.biodiversity_stratum WHERE biodiversity_checklist_id = ? "
            + "ORDER BY stratum_number, stratum_id",
        (rs, n) -> rs.getString(1),
        checklistId);
    return ids.isEmpty() ? "" : trimId(ids.get(0));
  }

  /** The checklist's first water sample site id (a checklist may have 0..n sites); "" when none. */
  private String resolveFirstWaterSampleSiteId(String checklistId) {
    List<String> ids = jdbcTemplate.query(
        "SELECT water_sample_site_id FROM the.water_sample_site WHERE water_checklist_id = ? "
            + "ORDER BY sample_site_number, water_sample_site_id",
        (rs, n) -> rs.getString(1),
        checklistId);
    return ids.isEmpty() ? "" : trimId(ids.get(0));
  }

  private static String trimId(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.endsWith(".0") ? trimmed.substring(0, trimmed.length() - 2) : trimmed;
  }

  /** Null for a blank string so empty values are not passed to NUMBER struct attrs (avoids ORA-17059). */
  private static Object blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  public ChecklistSectionData getBioStratum(String checklistId) {
    // FREP_211_BioStratum.get is a single-stratum GET keyed on resource_value_id (its status
    // SELECT INTO) + stratum_id; passing empty ids raises ORA-01403. Resolve the checklist's
    // resource value id and its first stratum id (a checklist may have 0..n strata) so the section
    // populates instead of erroring.
    String resourceValueId = resolveBioResourceValueId(checklistId);
    String stratumId = resolveFirstBioStratumId(checklistId);

    // Layout per legacy Frep211DataManager (deployed signature): 82 params, leading OUT block 1-15,
    // stratum_id IN @16, checklist_id IN @17, resource_value_id IN @18, fields 19-80, error @81,
    // wind-treatment REF CURSOR @82.
    String call = callSql(BIO_STRATUM_PACKAGE, "get", 82);
    return executeCall(call, cs -> {
      for (int i = 1; i <= 15; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.setString(16, stratumId);
      cs.setString(17, checklistId);
      cs.setString(18, resourceValueId);
      for (int i = 19; i <= 81; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.registerOutParameter(82, OracleTypes.CURSOR);
    }, cs -> {
      throwIfError(BIO_STRATUM_PACKAGE, "get", cs.getString(81));
      ChecklistHeaderData header = headerFromBioTombstone(cs, 19);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Stratum type", cs.getString(21));
      putIfPresent(fields, "Stratum number", cs.getString(22));
      putIfPresent(fields, "Stratum summary date", cs.getString(23));
      putIfPresent(fields, "Stratum assessor", cs.getString(24));
      putIfPresent(fields, "Stratum plot count", cs.getString(25));
      putIfPresent(fields, "Plots completed", cs.getString(26));
      putIfPresent(fields, "Stratum size", cs.getString(27));
      putIfPresent(fields, "Stratum consistent map ind", cs.getString(28));
      putIfPresent(fields, "Stratum estimated size", cs.getString(29));
      putIfPresent(fields, "Patch location", cs.getString(30));
      putIfPresent(fields, "Patch estimated oldest tree age", cs.getString(31));
      putIfPresent(fields, "Patch general comment", cs.getString(32));
      putIfPresent(fields, "Patch windthrow pct", cs.getString(33));
      putIfPresent(fields, "Constraint indicator", cs.getString(34));
      putIfPresent(fields, "Wetland %", cs.getString(35));
      putIfPresent(fields, "Harvest area code", cs.getString(36));
      putIfPresent(fields, "Riparian management zone %", cs.getString(37));
      putIfPresent(fields, "Riparian reserve zone %", cs.getString(38));
      putIfPresent(fields, "Rock outcrop %", cs.getString(39));
      putIfPresent(fields, "Non-commercial brush %", cs.getString(40));
      putIfPresent(fields, "Non-merch timber %", cs.getString(41));
      putIfPresent(fields, "Sensitive soil %", cs.getString(42));
      putIfPresent(fields, "Ungulate wintering %", cs.getString(43));
      putIfPresent(fields, "Wildlife habitat area %", cs.getString(44));
      putIfPresent(fields, "OGMA %", cs.getString(45));
      putIfPresent(fields, "Visuals %", cs.getString(46));
      putIfPresent(fields, "Cultural heritage feature %", cs.getString(47));
      putIfPresent(fields, "Recreation feature %", cs.getString(48));
      putIfPresent(fields, "Other constraint", cs.getString(49));
      putIfPresent(fields, "Other constraint %", cs.getString(50));
      putIfPresent(fields, "Eco indicator", cs.getString(51));
      putIfPresent(fields, "Bear den count", cs.getString(52));
      putIfPresent(fields, "Hibernaculum count", cs.getString(53));
      putIfPresent(fields, "Veteran tree count", cs.getString(54));
      putIfPresent(fields, "Mineral lick count", cs.getString(55));
      putIfPresent(fields, "Large stick nest count", cs.getString(56));
      putIfPresent(fields, "Cavity nest count", cs.getString(57));
      putIfPresent(fields, "Large hollow tree count", cs.getString(58));
      putIfPresent(fields, "Large witches' broom count", cs.getString(59));
      putIfPresent(fields, "Karst feature", cs.getString(60));
      putIfPresent(fields, "Largest tree", cs.getString(61));
      putIfPresent(fields, "Heavy CWD concentration", cs.getString(62));
      putIfPresent(fields, "Active wildlife trails", cs.getString(63));
      putIfPresent(fields, "Active WLT/CWD feeding", cs.getString(64));
      putIfPresent(fields, "Uncommon tree species", cs.getString(65));
      putIfPresent(fields, "Other eco anchor count", cs.getString(66));
      putIfPresent(fields, "Other eco anchor description", cs.getString(67));
      putIfPresent(fields, "BGC zone", cs.getString(68));
      putIfPresent(fields, "BGC subzone", cs.getString(69));
      putIfPresent(fields, "BGC variant", cs.getString(70));
      putIfPresent(fields, "BGC phase", cs.getString(71));
      putIfPresent(fields, "Site series", cs.getString(72));
      putIfPresent(fields, "Site series phase", cs.getString(73));
      putIfPresent(fields, "Seral", cs.getString(74));
      putIfPresent(fields, "Windthrow distribution code", cs.getString(75));
      putIfPresent(fields, "Other windthrow treatment", cs.getString(76));
      putIfPresent(fields, "Constrained total", cs.getString(77));
      putIfPresent(fields, "NAR", cs.getString(78));
      putIfPresent(fields, "Actual number of plots", cs.getString(79));
      putCursorFields(fields, cs, 82, "Wind treatment ");
      return ChecklistSectionData.of(header.mergedWith(getTombstone(checklistId, "SLB", "")), fields);
    });
  }

  public ChecklistSectionData getBioPlots(String checklistId) {
    // Layout per legacy Frep212DataManager (deployed signature): 44 params, leading OUT 1-15 +
    // strata_type @16, then plot_id IN @17, stratum_id IN @18, resource_value_id IN @19, the stand
    // + CWD VARRAYs @20/@21, scalar plot fields 22-43, error @44. (The load view has no specific
    // plot/stratum context, so the id inputs are blank.)
    String call = callSql(BIO_PLOT_PACKAGE, "get", 44);
    return executeCall(call, cs -> {
      for (int i = 1; i <= 16; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.setString(17, "");
      cs.setString(18, "");
      cs.setString(19, EMPTY_RESOURCE_VALUE_ID);
      cs.registerOutParameter(20, Types.ARRAY, STAND_TABLE_ARRAY);
      cs.registerOutParameter(21, Types.ARRAY, CWD_TABLE_ARRAY);
      for (int i = 22; i <= 44; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
    }, cs -> {
      throwIfError(BIO_PLOT_PACKAGE, "get", cs.getString(44));
      ChecklistHeaderData header = headerFromBioTombstone(cs, 24);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Strata type", cs.getString(16));
      putIfPresent(fields, "Plot number", cs.getString(25));
      putIfPresent(fields, "Assessor name", cs.getString(26));
      putIfPresent(fields, "UTM signal", cs.getString(27));
      putIfPresent(fields, "UTM zone", cs.getString(28));
      putIfPresent(fields, "UTM easting", cs.getString(29));
      putIfPresent(fields, "UTM northing", cs.getString(30));
      putIfPresent(fields, "Tree indicator", cs.getString(31));
      putIfPresent(fields, "Basal area factor", cs.getString(32));
      putIfPresent(fields, "Fixed area radius", cs.getString(33));
      putIfPresent(fields, "Full count area", cs.getString(34));
      putIfPresent(fields, "CWD transect indicator", cs.getString(35));
      putIfPresent(fields, "First leg transect", cs.getString(36));
      putIfPresent(fields, "Second leg transect", cs.getString(37));
      putIfPresent(fields, "Stratum plot count", cs.getString(39));
      putIfPresent(fields, "Plots completed", cs.getString(40));
      putArrayFields(fields, cs.getArray(20), "Stand table ", STAND_COLS);
      putArrayFields(fields, cs.getArray(21), "CWD table ", CWD_COLS);
      return ChecklistSectionData.of(header.mergedWith(getTombstone(checklistId, "SLB", "")), fields);
    });
  }

  public ChecklistSectionData getRipStreamOpening(String checklistId) {
    // Layout per legacy RiparianChecklistDataManager.getRiparianChecklistStream (deployed
    // signature): 69 params, leading OUT block 1-21, resource_value_id IN @22, checklist_id IN @23,
    // status @24, stream fields, stream-edge VARRAY @31, ... invasive @66/67, revision @68, error
    // @69. (The fixed-18 tombstone helper left params 19-21 unbound.)
    // Resolve the real resource_value_id so the proc's tombstone join (org unit, opening, licence,
    // client, etc.) populates instead of returning blanks.
    String resourceValueId = resolveRipResourceValueId(checklistId);
    String call = callSql(RIP_STREAM_PACKAGE, "GET", 69);
    return executeCall(call, cs -> {
      for (int i = 1; i <= 21; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.setString(22, resourceValueId);
      cs.setString(23, checklistId);
      for (int i = 24; i <= 68; i++) {
        if (i == 31) {
          cs.registerOutParameter(i, Types.ARRAY, RIP_STREAM_EDGE_ARRAY);
        } else {
          cs.registerOutParameter(i, Types.VARCHAR);
        }
      }
      cs.registerOutParameter(69, Types.VARCHAR);
    }, cs -> {
      throwIfError(RIP_STREAM_PACKAGE, "GET", cs.getString(69));
      ChecklistHeaderData header = headerFromRipTombstone(cs, 24);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      // Header context fields the legacy FREP230 screen shows (tombstone block, proc params 1-20):
      // org unit, tenure (licence/CP/block), opening id, client, sample #, FSP, harvest date.
      putIfPresent(fields, "Org unit", cs.getString(2));
      putIfPresent(fields, "Opening ID", cs.getString(5));
      putIfPresent(fields, "Licence", cs.getString(7));
      putIfPresent(fields, "Cutting permit", cs.getString(9));
      putIfPresent(fields, "Cut block", cs.getString(10));
      putIfPresent(fields, "Client", cs.getString(13));
      putIfPresent(fields, "Client name", cs.getString(14));
      putIfPresent(fields, "Sample #", cs.getString(17));
      putIfPresent(fields, "FSP", cs.getString(19));
      putIfPresent(fields, "Harvest complete date", cs.getString(20));
      putIfPresent(fields, "Range use plan", cs.getString(25));
      putIfPresent(fields, "Pasture id", cs.getString(26));
      putIfPresent(fields, "Stream name", cs.getString(27));
      putIfPresent(fields, "Stream location ind", cs.getString(28));
      putIfPresent(fields, "Planned riparian stream RMA class", cs.getString(29));
      putIfPresent(fields, "Actual riparian stream RMA class", cs.getString(30));
      putArrayFields(fields, cs.getArray(31), "Stream edge ", STRM_EDGE_COLS);
      putIfPresent(fields, "Channel width (m)", cs.getString(32));
      putIfPresent(fields, "Channel gradient (%)", cs.getString(33));
      putIfPresent(fields, "Channel depth (m)", cs.getString(34));
      putIfPresent(fields, "Reach location to (m)", cs.getString(35));
      putIfPresent(fields, "Reach location from (m)", cs.getString(36));
      putIfPresent(fields, "Reach location u/s-d/s ind", cs.getString(37));
      putIfPresent(fields, "Reach location from desc", cs.getString(38));
      putIfPresent(fields, "UTM signal", cs.getString(39));
      putIfPresent(fields, "UTM at reference", cs.getString(40));
      putIfPresent(fields, "UTM zone", cs.getString(41));
      putIfPresent(fields, "UTM easting", cs.getString(42));
      putIfPresent(fields, "UTM northing", cs.getString(43));
      putIfPresent(fields, "Channel morphology", cs.getString(44));
      putIfPresent(fields, "RMA dominants on plans %", cs.getString(45));
      putIfPresent(fields, "RMA dominants on plans ind", cs.getString(46));
      putIfPresent(fields, "RMA dominants in field %", cs.getString(47));
      putIfPresent(fields, "RMA understory on plans %", cs.getString(48));
      putIfPresent(fields, "RMA understory on plans ind", cs.getString(49));
      putIfPresent(fields, "RMA understory in field %", cs.getString(50));
      putIfPresent(fields, "RRZ dominants on plans %", cs.getString(51));
      putIfPresent(fields, "RRZ dominants on plans ind", cs.getString(52));
      putIfPresent(fields, "RRZ dominants in field %", cs.getString(53));
      putIfPresent(fields, "RRZ dominants in field", cs.getString(54));
      putIfPresent(fields, "RRZ understory on plans %", cs.getString(55));
      putIfPresent(fields, "RRZ understory on plans ind", cs.getString(56));
      putIfPresent(fields, "RRZ understory in field %", cs.getString(57));
      putIfPresent(fields, "RRZ understory in field", cs.getString(58));
      putIfPresent(fields, "RMZ dominants on plans %", cs.getString(59));
      putIfPresent(fields, "RMZ dominants on plans ind", cs.getString(60));
      putIfPresent(fields, "RMZ dominants in field %", cs.getString(61));
      putIfPresent(fields, "RMZ understory on plans %", cs.getString(62));
      putIfPresent(fields, "RMZ understory on plans ind", cs.getString(63));
      putIfPresent(fields, "RMZ understory in field %", cs.getString(64));
      putIfPresent(fields, "Planned riparian N/A ind", cs.getString(65));
      putIfPresent(fields, "Invasive plant", cs.getString(66));
      putIfPresent(fields, "Invasive plant comment", cs.getString(67));
      return ChecklistSectionData.of(
          header.mergedWith(getTombstone(checklistId, "RIP", resourceValueId)), fields);
    });
  }

  public ChecklistSectionData getRipFieldData(String checklistId) {
    // Layout per legacy RiparianChecklistDataManager (the deployed signature): 26 params, with the
    // header block ending at sample_number (22), error_message (23), field_data_stream_dry (24),
    // then the point + continuous indicator VARRAYs (25, 26).
    String call = callSql(RIP_FIELD_PACKAGE, "GET", 26);
    return executeCall(call, cs -> {
      cs.setString(1, EMPTY_RESOURCE_VALUE_ID);
      cs.registerOutParameter(2, Types.VARCHAR);
      cs.registerOutParameter(3, Types.VARCHAR);
      cs.setString(4, checklistId);
      for (int i = 5; i <= 24; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.registerOutParameter(25, Types.ARRAY, RIP_POINT_IND_ARRAY);
      cs.registerOutParameter(26, Types.ARRAY, RIP_CONTINUOUS_IND_ARRAY);
    }, cs -> {
      throwIfError(RIP_FIELD_PACKAGE, "GET", cs.getString(23));
      ChecklistHeaderData header = headerFromRipTombstone(cs, 5);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Field data stream dry", cs.getString(24));
      putArrayFields(fields, cs.getArray(25), "Point indicator ", POINT_IND_COLS);
      putArrayFields(fields, cs.getArray(26), "Continuous indicator ", CONTINUOUS_IND_COLS);
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getRipOtherIndicators(String checklistId) {
    String call = callSql(RIP_OTHER_PACKAGE, "GET", 25);
    return executeCall(call, cs -> {
      cs.setString(1, EMPTY_RESOURCE_VALUE_ID);
      cs.registerOutParameter(2, Types.VARCHAR);
      cs.setString(3, "RIP");
      cs.setString(4, checklistId);
      cs.registerOutParameter(5, Types.VARCHAR);
      cs.registerOutParameter(6, Types.VARCHAR);
      registerRipTombstoneOutParams(cs, 7);
      cs.registerOutParameter(24, Types.VARCHAR);
      cs.registerOutParameter(25, Types.ARRAY, RIP_OTHER_IND_ARRAY);
    }, cs -> {
      throwIfError(RIP_OTHER_PACKAGE, "GET", cs.getString(24));
      ChecklistHeaderData header = headerFromRipTombstone(cs, 5);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Channel morphology", cs.getString(6));
      putArrayFields(fields, cs.getArray(25), "Other indicator ", OTHER_IND_COLS);
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getRipQuestions(String checklistId) {
    String call = callSql(RIP_QUESTIONS_PACKAGE, "GET", 27);
    return executeCall(call, cs -> {
      cs.setString(1, EMPTY_RESOURCE_VALUE_ID);
      cs.registerOutParameter(2, Types.VARCHAR);
      cs.setString(3, "RIP");
      cs.setString(4, checklistId);
      cs.registerOutParameter(5, Types.VARCHAR);
      cs.registerOutParameter(6, Types.VARCHAR);
      cs.registerOutParameter(7, Types.VARCHAR);
      registerRipTombstoneOutParams(cs, 8);
      cs.registerOutParameter(25, Types.VARCHAR);
      cs.registerOutParameter(26, Types.ARRAY, RIP_QUESTIONS_ARRAY);
      cs.registerOutParameter(27, Types.ARRAY, RIP_NO_ANSWERS_ARRAY);
    }, cs -> {
      throwIfError(RIP_QUESTIONS_PACKAGE, "GET", cs.getString(25));
      ChecklistHeaderData header = headerFromRipTombstone(cs, 5);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Channel morphology", cs.getString(6));
      putIfPresent(fields, "Actual riparian stream RMA class", cs.getString(7));
      putArrayFields(fields, cs.getArray(26), "Question ", QUESTIONS_COLS);
      putArrayFields(fields, cs.getArray(27), "No answer ", NO_ANSWERS_COLS);
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getRipSpecificImpacts(String checklistId) {
    String call = callSql(RIP_IMPACTS_PACKAGE, "GET", 25);
    return executeCall(call, cs -> {
      for (int i = 1; i <= 19; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.setString(20, EMPTY_RESOURCE_VALUE_ID);
      cs.setString(21, checklistId);
      cs.registerOutParameter(22, Types.VARCHAR);
      cs.registerOutParameter(23, Types.VARCHAR);
      cs.registerOutParameter(24, OracleTypes.CURSOR);
      cs.registerOutParameter(25, OracleTypes.CURSOR);
    }, cs -> {
      throwIfError(RIP_IMPACTS_PACKAGE, "GET", cs.getString(23));
      ChecklistHeaderData header = headerFromRipTombstone(cs, 22);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putCursorFields(fields, cs, 24, "Specific impact ");
      putCursorFields(fields, cs, 25, "Other specific impact ");
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getRipFinalComments(String checklistId) {
    // Layout per legacy RiparianChecklistDataManager.getFinalCmts (deployed signature): 30 params,
    // leading OUT block 1-19, resource_value_id IN @20, checklist_id IN @21, status @22, the six
    // comment fields 23-28, revision @29, error @30.
    String call = callSql(RIP_COMMENTS_PACKAGE, "GET", 30);
    return executeCall(call, cs -> {
      for (int i = 1; i <= 19; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.setString(20, EMPTY_RESOURCE_VALUE_ID);
      cs.setString(21, checklistId);
      for (int i = 22; i <= 30; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
    }, cs -> {
      throwIfError(RIP_COMMENTS_PACKAGE, "GET", cs.getString(30));
      ChecklistHeaderData header = headerFromRipTombstone(cs, 22);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Conclusion comment", cs.getString(23));
      putIfPresent(fields, "Specific impact comment", cs.getString(24));
      putIfPresent(fields, "Assessment problems comment", cs.getString(25));
      putIfPresent(fields, "Map legibility comment", cs.getString(26));
      putIfPresent(fields, "Leave strip assessment comment", cs.getString(27));
      putIfPresent(fields, "Checklist recommendation comment", cs.getString(28));
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getWaterSampleArea(String checklistId) {
    String call = "{call " + WTR_CHECKLIST_PROC + "(?,?,?)}";
    return executeCall(call, cs -> {
      cs.setObject(1, createWaterChecklistStruct(cs, checklistId));
      cs.registerOutParameter(1, Types.STRUCT, WTR_CHECKLIST_TYPE);
      cs.registerOutParameter(2, Types.ARRAY, WTR_DISTURBANCE_ARRAY);
      cs.registerOutParameter(3, Types.ARRAY, WTR_ACCESS_ROAD_ARRAY);
    }, cs -> {
      Struct checklist = (Struct) cs.getObject(1);
      ChecklistHeaderData header = headerFromWaterChecklistStruct(checklist);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putWaterChecklistFields(fields, checklist);
      putArrayFields(fields, cs.getArray(2), "Disturbance ", WTR_DISTURBANCE_COLS);
      putArrayFields(fields, cs.getArray(3), "Access road ", WTR_ACCESS_ROAD_COLS);
      ChecklistSectionData section = ChecklistSectionData.of(header, fields);
      return section.header().effectiveYear().isBlank()
          ? section
          : ChecklistSectionData.of(
              section.header().mergedWith(getTombstone(checklistId, "WTR", header.frepSelectedSiteId())),
              fields
          );
    });
  }

  public ChecklistSectionData getWaterSampleSite(String checklistId) {
    // FREP_251 -> FREP_WATER_SAMPLE_SITE.GET keys its main SELECT INTO on BOTH water_checklist_id
    // AND water_sample_site_id; passing an empty sample-site id raises ORA-01403. Resolve the
    // checklist's first sample site (a checklist may have 0..n) so the section populates.
    String sampleSiteId = resolveFirstWaterSampleSiteId(checklistId);
    String call = "{call " + WTR_SAMPLE_SITE_PROC + "(?)}";
    return executeCall(call, cs -> {
      cs.setObject(1, createWaterSampleSiteStruct(cs, checklistId, sampleSiteId));
      cs.registerOutParameter(1, Types.STRUCT, WTR_SAMPLE_SITE_TYPE);
    }, cs -> {
      Struct sampleSite = (Struct) cs.getObject(1);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putWaterSampleSiteFields(fields, sampleSite);
      ChecklistHeaderData header = headerFromWaterSampleSiteStruct(sampleSite);
      if (header.effectiveYear().isBlank()) {
        header = header.mergedWith(getTombstone(checklistId, "WTR", header.frepSelectedSiteId()));
      }
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getWaterAssessment(String waterSampleSiteId) {
    if (waterSampleSiteId == null || waterSampleSiteId.isBlank()) {
      return ChecklistSectionData.fieldsOnly(Map.of());
    }
    String call = "{call " + WTR_ASSESSMENT_PROC + "(?,?,?)}";
    return executeCall(call, cs -> {
      cs.setString(1, waterSampleSiteId);
      cs.registerOutParameter(2, Types.ARRAY, WTR_ASSESSMENT_ARRAY);
      cs.registerOutParameter(3, Types.ARRAY, WTR_ASSESSMENT_ARRAY);
    }, cs -> {
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putArrayFields(fields, cs.getArray(2), "Observed condition ", WTR_ASSESSMENT_COLS);
      putArrayFields(fields, cs.getArray(3), "Solution ", WTR_ASSESSMENT_COLS);
      return ChecklistSectionData.fieldsOnly(fields);
    });
  }

  public ChecklistSectionData getWaterRange(String waterSampleSiteId) {
    if (waterSampleSiteId == null || waterSampleSiteId.isBlank()) {
      return ChecklistSectionData.fieldsOnly(Map.of());
    }
    String call = "{call " + WTR_RANGE_PROC + "(?,?)}";
    return executeCall(call, cs -> {
      cs.setString(1, waterSampleSiteId);
      cs.registerOutParameter(2, Types.ARRAY, WTR_ASSESSMENT_ARRAY);
    }, cs -> {
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putArrayFields(fields, cs.getArray(2), "Range ", WTR_ASSESSMENT_COLS);
      return ChecklistSectionData.fieldsOnly(fields);
    });
  }

  public ChecklistSectionData getWaterSummary(String checklistId) {
    String call = "{call " + WTR_SUMMARY_PROC + "(?,?)}";
    return executeCall(call, cs -> {
      cs.setObject(1, createWaterChecklistStruct(cs, checklistId));
      cs.registerOutParameter(1, Types.STRUCT, WTR_CHECKLIST_TYPE);
      cs.registerOutParameter(2, Types.ARRAY, WTR_SUMMARY_ARRAY);
    }, cs -> {
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putArrayFields(fields, cs.getArray(2), "Summary ");
      return ChecklistSectionData.fieldsOnly(fields);
    });
  }

  ChecklistHeaderData getTombstone(String checklistId, String resourceValueType, String resourceValueId) {
    String call = "{call " + TOMBSTONE_PROC + "(" + placeholders(23) + ")}";
    return executeCall(call, cs -> {
      cs.setString(1, "");
      cs.registerOutParameter(1, Types.VARCHAR);
      cs.setString(2, emptyIfNull(resourceValueId));
      cs.registerOutParameter(2, Types.VARCHAR);
      cs.setString(3, checklistId);
      cs.registerOutParameter(3, Types.VARCHAR);
      cs.setString(4, resourceValueType);
      cs.registerOutParameter(4, Types.VARCHAR);
      for (int i = 5; i <= 22; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.registerOutParameter(23, Types.VARCHAR);
    }, cs -> {
      throwIfError(TOMBSTONE_PROC, "GET", cs.getString(23));
      return new ChecklistHeaderData(
          stringValue(cs.getString(2)),
          stringValue(cs.getString(10)),
          stringValue(cs.getString(6)),
          "",
          stringValue(cs.getString(22)),
          stringValue(cs.getString(19))
      );
    });
  }

  private static void registerRipTombstoneOutParams(CallableStatement cs, int startIndex) throws SQLException {
    for (int i = startIndex; i < startIndex + 18; i++) {
      cs.registerOutParameter(i, Types.VARCHAR);
    }
  }

  // The shared tombstone block in these GET procs carries opening @4, evaluator @11, evaluation
  // date @12; the harvest/effective year and resource (site) id are NOT reliably in this block, so
  // they are left blank here and filled authoritatively by the FREP_TOMBSTONE_GET merge
  // (getTombstone) applied per protocol.
  private static ChecklistHeaderData headerFromBioTombstone(CallableStatement cs, int statusIndex)
      throws SQLException {
    return new ChecklistHeaderData(
        "",
        stringValue(cs.getString(4)),
        "",
        stringValue(cs.getString(statusIndex)),
        stringValue(cs.getString(11)),
        stringValue(cs.getString(12))
    );
  }

  private static ChecklistHeaderData headerFromRipTombstone(CallableStatement cs, int statusIndex)
      throws SQLException {
    return new ChecklistHeaderData(
        "",
        stringValue(cs.getString(4)),
        "",
        stringValue(cs.getString(statusIndex)),
        stringValue(cs.getString(11)),
        stringValue(cs.getString(12))
    );
  }

  private static ChecklistHeaderData headerFromWaterChecklistStruct(Struct struct) throws SQLException {
    if (struct == null) {
      return ChecklistHeaderData.empty();
    }
    Object[] attrs = struct.getAttributes();
    return new ChecklistHeaderData(
        stringValue(attrs, 1),
        "",
        "",
        stringValue(attrs, 2),
        "",
        "" // FREP_WTR_CHKLST_OBJECT has no evaluation_date; the tombstone merge supplies it
    );
  }

  private static ChecklistHeaderData headerFromWaterSampleSiteStruct(Struct struct) throws SQLException {
    if (struct == null) {
      return ChecklistHeaderData.empty();
    }
    Object[] attrs = struct.getAttributes();
    return new ChecklistHeaderData(
        "",
        "",
        "",
        stringValue(attrs, 2),
        "",
        ""
    );
  }

  private static Struct createWaterChecklistStruct(CallableStatement cs, String checklistId) throws SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    // FREP_WTR_CHKLST_OBJECT has 39 attributes (incl. EVALUATION_DATE @34); createStruct requires
    // the array length to match the type exactly or Oracle raises ORA-00600 [kope2_readstr232].
    Object[] attrs = new Object[39];
    // FREP_WATER_CHECKLIST.GET keys only on water_checklist_id (attr 1); frep_resource_value_id
    // (attr 2) is a NUMBER, so it must be left null — an empty string raises ORA-17059.
    attrs[0] = checklistId;
    return connection.createStruct(WTR_CHECKLIST_TYPE, attrs);
  }

  private static Struct createWaterSampleSiteStruct(
      CallableStatement cs, String checklistId, String sampleSiteId) throws SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    Object[] attrs = new Object[28];
    // FREP_WTR_SAMPLE_SITE_OBJECT: attr 1 = water_sample_site_id, attr 2 = water_checklist_id.
    attrs[0] = blankToNull(sampleSiteId);
    attrs[1] = checklistId;
    return connection.createStruct(WTR_SAMPLE_SITE_TYPE, attrs);
  }

  private static void putWaterChecklistFields(Map<String, String> fields, Struct struct) throws SQLException {
    if (struct == null) {
      return;
    }
    // FREP_WTR_CHKLST_OBJECT is 39 attrs: evaluation_date @33, invasive plant answer/comment @34/35
    // (revision_count @36, userids @37/38) — 0-based per the .tps.
    Object[] attrs = struct.getAttributes();
    putIfPresent(fields, "Site access code", stringValue(attrs, 3));
    putIfPresent(fields, "Main access road number", stringValue(attrs, 4));
    putIfPresent(fields, "Main watershed description", stringValue(attrs, 5));
    putIfPresent(fields, "Drinking water answer", stringValue(attrs, 6));
    putIfPresent(fields, "Water intake comment", stringValue(attrs, 7));
    putIfPresent(fields, "Intake to cutblock distance", stringValue(attrs, 8));
    putIfPresent(fields, "Water intake connectivity", stringValue(attrs, 9));
    putIfPresent(fields, "Intake to cutblock comment", stringValue(attrs, 10));
    putIfPresent(fields, "Special resource answer", stringValue(attrs, 11));
    putIfPresent(fields, "Special resource comment", stringValue(attrs, 12));
    putIfPresent(fields, "Reported disturbance ind", stringValue(attrs, 13));
    putIfPresent(fields, "Fertilizer use on road ind", stringValue(attrs, 14));
    putIfPresent(fields, "Fertilizer use within block ind", stringValue(attrs, 15));
    putIfPresent(fields, "Sensitive soil answer", stringValue(attrs, 16));
    putIfPresent(fields, "Herbicide use on road ind", stringValue(attrs, 17));
    putIfPresent(fields, "Herbicide use within block ind", stringValue(attrs, 18));
    putIfPresent(fields, "Pesticide use on road ind", stringValue(attrs, 19));
    putIfPresent(fields, "Pesticide use within block ind", stringValue(attrs, 20));
    putIfPresent(fields, "Stream crossings ind", stringValue(attrs, 21));
    putIfPresent(fields, "Roads parallel to stream ind", stringValue(attrs, 22));
    putIfPresent(fields, "Unstable slopes ind", stringValue(attrs, 23));
    putIfPresent(fields, "Sensitive soils ind", stringValue(attrs, 24));
    putIfPresent(fields, "Adjacent harvesting ind", stringValue(attrs, 25));
    putIfPresent(fields, "Livestock concerns ind", stringValue(attrs, 26));
    putIfPresent(fields, "Other activity ind", stringValue(attrs, 27));
    putIfPresent(fields, "Other activity description", stringValue(attrs, 28));
    putIfPresent(fields, "Note description", stringValue(attrs, 29));
    putIfPresent(fields, "Block access time", stringValue(attrs, 30));
    putIfPresent(fields, "Hours on block", stringValue(attrs, 31));
    putIfPresent(fields, "People on block", stringValue(attrs, 32));
    putIfPresent(fields, "Evaluation date", stringValue(attrs, 33));
    putIfPresent(fields, "Invasive plant answer", stringValue(attrs, 34));
    putIfPresent(fields, "Invasive plant comment", stringValue(attrs, 35));
  }

  private static void putWaterSampleSiteFields(Map<String, String> fields, Struct struct) throws SQLException {
    if (struct == null) {
      return;
    }
    Object[] attrs = struct.getAttributes();
    putIfPresent(fields, "Water sample site id", stringValue(attrs, 0));
    putIfPresent(fields, "Site type code", stringValue(attrs, 3));
    putIfPresent(fields, "Stream width code", stringValue(attrs, 4));
    putIfPresent(fields, "Evaluator name id", stringValue(attrs, 5));
    putIfPresent(fields, "Domestic intake ind", stringValue(attrs, 6));
    putIfPresent(fields, "Sample site number", stringValue(attrs, 7));
    putIfPresent(fields, "UTM signal", stringValue(attrs, 8));
    putIfPresent(fields, "UTM zone", stringValue(attrs, 9));
    putIfPresent(fields, "UTM easting", stringValue(attrs, 10));
    putIfPresent(fields, "UTM northing", stringValue(attrs, 11));
    putIfPresent(fields, "Road type code", stringValue(attrs, 12));
    putIfPresent(fields, "Road use code", stringValue(attrs, 13));
    putIfPresent(fields, "Road reference", stringValue(attrs, 14));
    putIfPresent(fields, "Watershed reference", stringValue(attrs, 15));
    putIfPresent(fields, "Community watershed ind", stringValue(attrs, 16));
    putIfPresent(fields, "Range impact evaluation ind", stringValue(attrs, 17));
    putIfPresent(fields, "Water compromised ind", stringValue(attrs, 18));
    putIfPresent(fields, "Other observed condition ind", stringValue(attrs, 19));
    putIfPresent(fields, "Other observed condition desc", stringValue(attrs, 20));
    putIfPresent(fields, "Other solution ind", stringValue(attrs, 21));
    putIfPresent(fields, "Other solution description", stringValue(attrs, 22));
    putIfPresent(fields, "Assessment comment", stringValue(attrs, 23));
    putIfPresent(fields, "Range comment", stringValue(attrs, 24));
  }

  static void putIfPresent(Map<String, String> fields, String label, String value) {
    String normalized = stringValue(value);
    if (!normalized.isBlank()) {
      fields.put(label, normalized);
    }
  }

  static void putArrayFields(Map<String, String> fields, Array array, String rowPrefix) throws SQLException {
    putArrayFields(fields, array, rowPrefix, null);
  }

  /**
   * Flatten a VARRAY of OBJECTs into labelled per-row fields. When {@code cols} is supplied each
   * attribute is labelled by its column name (in {@code .tps} attribute order) instead of a generic
   * "Field N".
   */
  static void putArrayFields(Map<String, String> fields, Array array, String rowPrefix, String[] cols)
      throws SQLException {
    if (array == null) {
      return;
    }
    Object[] elements = (Object[]) array.getArray();
    for (int row = 0; row < elements.length; row++) {
      if (elements[row] instanceof Struct struct) {
        Object[] attrs = struct.getAttributes();
        for (int i = 0; i < attrs.length; i++) {
          String value = stringValue(attrs[i]);
          if (!value.isBlank()) {
            String label = (cols != null && i < cols.length) ? cols[i] : "Field " + (i + 1);
            fields.put(rowPrefix + (row + 1) + " - " + label, value);
          }
        }
      }
    }
  }

  // Column labels for the VARRAY element OBJECT types (in .tps attribute order), used to render
  // collection rows with named columns in the read/load view.
  static final String[] STAND_COLS = {"Stand id", "Plot id", "Species code", "Species desc",
      "Tree number", "DBH", "Height", "Comments", "Decay class code", "Decay class desc",
      "Revision count", "Entry userid", "Update userid"};
  static final String[] CWD_COLS = {"CWD id", "Plot id", "Species code", "Species desc",
      "Log number", "Log diameter", "Log length", "Decay class code", "Decay class desc",
      "Comments", "Revision count", "Entry userid", "Update userid"};
  static final String[] STRM_EDGE_COLS = {"Checklist id", "Measure type", "Measurement",
      "Description", "Revision count", "Entry userid", "Update userid"};
  static final String[] POINT_IND_COLS = {"Indicator id", "Question no", "Type", "Transect no",
      "Measure 1", "Measure 2", "Measure 3", "Measure 4", "Measure 5", "Measure 6", "Threshold",
      "Mean", "Revision count"};
  static final String[] CONTINUOUS_IND_COLS = {"Indicator id", "Question no", "Type", "Question",
      "Total", "Comments", "Threshold", "Revision count"};
  static final String[] OTHER_IND_COLS = {"Type id", "Section code", "Header question ind",
      "Question", "Indicator id", "Answer ind", "Revision count", "Entry userid", "Update userid"};
  static final String[] QUESTIONS_COLS = {"Checklist id", "Question id", "Question no", "Question",
      "Channel morphology code", "Applicable ind", "Morphology desc", "Question type",
      "Question desc", "Sub question", "Answer code", "Revision count", "Entry userid",
      "Update userid"};
  static final String[] NO_ANSWERS_COLS = {"Impact id", "Checklist id", "Question id", "Question no",
      "Impact type", "Impact desc", "Sort order", "Answer ind", "Revision count", "Entry userid",
      "Update userid"};
  static final String[] WTR_DISTURBANCE_COLS = {"Disturbance id", "Checklist id", "Disturbance code",
      "Age code", "Number", "Revision count", "Entry userid", "Update userid"};
  static final String[] WTR_ACCESS_ROAD_COLS = {"Access road id", "Checklist id", "Road type",
      "Road desc", "Status code", "Approx length", "Approx age", "Revision count", "Entry userid",
      "Update userid"};
  static final String[] WTR_ASSESSMENT_COLS = {"Sample site id", "Activity group code",
      "Activity group desc", "Activity group count", "Assessment type", "Assessment desc",
      "Assessment ind", "Revision count", "Entry userid", "Update userid"};

  static void putCursorFields(Map<String, String> fields, CallableStatement cs, int index, String rowPrefix)
      throws SQLException {
    Object obj = cs.getObject(index);
    if (!(obj instanceof ResultSet rs)) {
      return;
    }
    try (ResultSet auto = rs) {
      int row = 0;
      while (auto.next()) {
        row++;
        ResultSetMetaData meta = auto.getMetaData();
        for (int col = 1; col <= meta.getColumnCount(); col++) {
          String value = stringValue(auto.getObject(col));
          if (!value.isBlank()) {
            fields.put(rowPrefix + row + " - " + meta.getColumnLabel(col), value);
          }
        }
      }
    }
  }

  static String stringValue(Object value) {
    if (value == null) {
      return "";
    }
    String normalized = value.toString().trim();
    if (normalized.endsWith(".0")) {
      try {
        Double.parseDouble(normalized);
        normalized = normalized.substring(0, normalized.length() - 2);
      } catch (NumberFormatException ignored) {
        // keep original string
      }
    }
    return normalized;
  }

  private static String stringValue(Object[] attrs, int index) {
    if (attrs == null || index >= attrs.length) {
      return "";
    }
    return stringValue(attrs[index]);
  }
}
