package ca.bc.gov.nrs.frep.repository.frep;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.LinkedHashMap;
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
    String call = callSql(BIO_OPENING_PACKAGE, "GET", 35);
    return executeCall(call, cs -> {
      registerBioTombstoneOutParams(cs, 1);
      cs.setString(20, EMPTY_RESOURCE_VALUE_ID);
      cs.setString(21, checklistId);
      for (int i = 22; i <= 34; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.registerOutParameter(35, Types.VARCHAR);
    }, cs -> {
      throwIfError(BIO_OPENING_PACKAGE, "GET", cs.getString(35));
      ChecklistHeaderData header = headerFromBioTombstone(cs, 22);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Observed gross area", cs.getString(23));
      putIfPresent(fields, "Location description", cs.getString(24));
      putIfPresent(fields, "Net area", cs.getString(25));
      putIfPresent(fields, "Gross area", cs.getString(26));
      putIfPresent(fields, "Block reserve", cs.getString(27));
      putIfPresent(fields, "Sample reserve", cs.getString(28));
      putIfPresent(fields, "Innovative practice", cs.getString(29));
      putIfPresent(fields, "Innovative practice comment", cs.getString(30));
      putIfPresent(fields, "Invasive plant", cs.getString(31));
      putIfPresent(fields, "Invasive plant comment", cs.getString(32));
      putIfPresent(fields, "Rating", cs.getString(33));
      putIfPresent(fields, "Rationale", cs.getString(34));
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getBioStratum(String checklistId) {
    String call = callSql(BIO_STRATUM_PACKAGE, "get", 82);
    return executeCall(call, cs -> {
      registerBioTombstoneOutParams(cs, 1);
      cs.setString(19, "");
      cs.setString(20, checklistId);
      cs.setString(21, EMPTY_RESOURCE_VALUE_ID);
      for (int i = 22; i <= 80; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.registerOutParameter(81, Types.VARCHAR);
      cs.registerOutParameter(82, OracleTypes.CURSOR);
    }, cs -> {
      throwIfError(BIO_STRATUM_PACKAGE, "get", cs.getString(81));
      ChecklistHeaderData header = headerFromBioTombstone(cs, 23);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Stratum type", cs.getString(25));
      putIfPresent(fields, "Stratum number", cs.getString(26));
      putIfPresent(fields, "Stratum summary date", cs.getString(27));
      putIfPresent(fields, "Stratum assessor", cs.getString(28));
      putIfPresent(fields, "Stratum plot count", cs.getString(29));
      putIfPresent(fields, "Plots completed", cs.getString(30));
      putIfPresent(fields, "Stratum size", cs.getString(31));
      putIfPresent(fields, "Stratum consistent map ind", cs.getString(32));
      putIfPresent(fields, "Stratum estimated size", cs.getString(33));
      putIfPresent(fields, "Patch location", cs.getString(34));
      putIfPresent(fields, "Patch estimated oldest tree age", cs.getString(35));
      putIfPresent(fields, "Patch general comment", cs.getString(36));
      putIfPresent(fields, "Patch windthrow pct", cs.getString(37));
      putIfPresent(fields, "Constrained total", cs.getString(78));
      putIfPresent(fields, "NAR", cs.getString(79));
      putIfPresent(fields, "Actual number of plots", cs.getString(80));
      putCursorFields(fields, cs, 82, "Wind treatment ");
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getBioPlots(String checklistId) {
    String call = callSql(BIO_PLOT_PACKAGE, "get", 44);
    return executeCall(call, cs -> {
      registerBioTombstoneOutParams(cs, 1);
      cs.registerOutParameter(19, Types.VARCHAR);
      cs.setString(20, "");
      cs.setString(21, "");
      cs.setString(22, EMPTY_RESOURCE_VALUE_ID);
      cs.registerOutParameter(23, Types.ARRAY, STAND_TABLE_ARRAY);
      cs.registerOutParameter(24, Types.ARRAY, CWD_TABLE_ARRAY);
      for (int i = 25; i <= 43; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.registerOutParameter(44, Types.VARCHAR);
    }, cs -> {
      throwIfError(BIO_PLOT_PACKAGE, "get", cs.getString(44));
      ChecklistHeaderData header = headerFromBioTombstone(cs, 27);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Strata type", cs.getString(19));
      putIfPresent(fields, "Plot number", cs.getString(28));
      putIfPresent(fields, "Assessor name", cs.getString(29));
      putIfPresent(fields, "UTM signal", cs.getString(30));
      putIfPresent(fields, "UTM zone", cs.getString(31));
      putIfPresent(fields, "UTM easting", cs.getString(32));
      putIfPresent(fields, "UTM northing", cs.getString(33));
      putIfPresent(fields, "Tree indicator", cs.getString(34));
      putIfPresent(fields, "Basal area factor", cs.getString(35));
      putIfPresent(fields, "Fixed area radius", cs.getString(36));
      putIfPresent(fields, "Full count area", cs.getString(37));
      putIfPresent(fields, "CWD transect indicator", cs.getString(38));
      putIfPresent(fields, "First leg transect", cs.getString(39));
      putIfPresent(fields, "Second leg transect", cs.getString(40));
      putIfPresent(fields, "Stratum plot count", cs.getString(42));
      putIfPresent(fields, "Plots completed", cs.getString(43));
      putArrayFields(fields, cs.getArray(23), "Stand table ");
      putArrayFields(fields, cs.getArray(24), "CWD table ");
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getRipStreamOpening(String checklistId) {
    String call = callSql(RIP_STREAM_PACKAGE, "GET", 69);
    return executeCall(call, cs -> {
      registerRipTombstoneOutParams(cs, 1);
      cs.setString(22, EMPTY_RESOURCE_VALUE_ID);
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
      putIfPresent(fields, "Range use plan", cs.getString(25));
      putIfPresent(fields, "Pasture id", cs.getString(26));
      putIfPresent(fields, "Stream name", cs.getString(27));
      putIfPresent(fields, "Stream location ind", cs.getString(28));
      putIfPresent(fields, "Planned riparian stream RMA class", cs.getString(29));
      putIfPresent(fields, "Actual riparian stream RMA class", cs.getString(30));
      putArrayFields(fields, cs.getArray(31), "Stream edge ");
      putIfPresent(fields, "Channel width", cs.getString(32));
      putIfPresent(fields, "Channel gradient pct", cs.getString(33));
      putIfPresent(fields, "Channel depth", cs.getString(34));
      putIfPresent(fields, "Invasive plant", cs.getString(66));
      putIfPresent(fields, "Invasive plant comment", cs.getString(67));
      return ChecklistSectionData.of(header, fields);
    });
  }

  public ChecklistSectionData getRipFieldData(String checklistId) {
    String call = callSql(RIP_FIELD_PACKAGE, "GET", 26);
    return executeCall(call, cs -> {
      cs.setString(1, EMPTY_RESOURCE_VALUE_ID);
      cs.registerOutParameter(2, Types.VARCHAR);
      cs.registerOutParameter(3, Types.VARCHAR);
      cs.setString(4, checklistId);
      cs.registerOutParameter(5, Types.VARCHAR);
      registerRipTombstoneOutParams(cs, 6);
      cs.registerOutParameter(24, Types.VARCHAR);
      cs.registerOutParameter(25, Types.VARCHAR);
      cs.registerOutParameter(26, Types.ARRAY, RIP_POINT_IND_ARRAY);
      cs.registerOutParameter(27, Types.ARRAY, RIP_CONTINUOUS_IND_ARRAY);
    }, cs -> {
      throwIfError(RIP_FIELD_PACKAGE, "GET", cs.getString(24));
      ChecklistHeaderData header = headerFromRipTombstone(cs, 5);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Field data stream dry", cs.getString(25));
      putArrayFields(fields, cs.getArray(26), "Point indicator ");
      putArrayFields(fields, cs.getArray(27), "Continuous indicator ");
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
      putArrayFields(fields, cs.getArray(25), "Other indicator ");
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
      putArrayFields(fields, cs.getArray(26), "Question ");
      putArrayFields(fields, cs.getArray(27), "No answer ");
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
    String call = callSql(RIP_COMMENTS_PACKAGE, "GET", 34);
    return executeCall(call, cs -> {
      registerRipTombstoneOutParams(cs, 1);
      cs.registerOutParameter(19, Types.VARCHAR);
      cs.registerOutParameter(20, Types.VARCHAR);
      cs.registerOutParameter(21, Types.VARCHAR);
      cs.registerOutParameter(22, Types.VARCHAR);
      cs.registerOutParameter(23, Types.VARCHAR);
      cs.setString(24, EMPTY_RESOURCE_VALUE_ID);
      cs.setString(25, checklistId);
      for (int i = 26; i <= 33; i++) {
        cs.registerOutParameter(i, Types.VARCHAR);
      }
      cs.registerOutParameter(34, Types.VARCHAR);
    }, cs -> {
      throwIfError(RIP_COMMENTS_PACKAGE, "GET", cs.getString(34));
      ChecklistHeaderData header = headerFromRipTombstone(cs, 26);
      Map<String, String> fields = ChecklistSectionData.linkedFields();
      putIfPresent(fields, "Conclusion comment", cs.getString(27));
      putIfPresent(fields, "Specific impact comment", cs.getString(28));
      putIfPresent(fields, "Assessment problems comment", cs.getString(29));
      putIfPresent(fields, "Map legibility comment", cs.getString(30));
      putIfPresent(fields, "Leave strip assessment comment", cs.getString(31));
      putIfPresent(fields, "Checklist recommendation comment", cs.getString(32));
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
      putArrayFields(fields, cs.getArray(2), "Disturbance ");
      putArrayFields(fields, cs.getArray(3), "Access road ");
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
    String call = "{call " + WTR_SAMPLE_SITE_PROC + "(?)}";
    return executeCall(call, cs -> {
      cs.setObject(1, createWaterSampleSiteStruct(cs, checklistId));
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
      putArrayFields(fields, cs.getArray(2), "Observed condition ");
      putArrayFields(fields, cs.getArray(3), "Solution ");
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
      putArrayFields(fields, cs.getArray(2), "Range ");
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

  private static void registerBioTombstoneOutParams(CallableStatement cs, int startIndex) throws SQLException {
    for (int i = startIndex; i < startIndex + 19; i++) {
      cs.registerOutParameter(i, Types.VARCHAR);
    }
  }

  private static void registerRipTombstoneOutParams(CallableStatement cs, int startIndex) throws SQLException {
    for (int i = startIndex; i < startIndex + 18; i++) {
      cs.registerOutParameter(i, Types.VARCHAR);
    }
  }

  private static ChecklistHeaderData headerFromBioTombstone(CallableStatement cs, int statusIndex)
      throws SQLException {
    return new ChecklistHeaderData(
        "",
        stringValue(cs.getString(4)),
        stringValue(cs.getString(1)),
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
        stringValue(cs.getString(1)),
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
        stringValue(attrs, 33)
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
    Object[] attrs = new Object[39];
    attrs[0] = checklistId;
    attrs[1] = EMPTY_RESOURCE_VALUE_ID;
    return connection.createStruct(WTR_CHECKLIST_TYPE, attrs);
  }

  private static Struct createWaterSampleSiteStruct(CallableStatement cs, String checklistId) throws SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    Object[] attrs = new Object[28];
    attrs[1] = checklistId;
    return connection.createStruct(WTR_SAMPLE_SITE_TYPE, attrs);
  }

  private static void putWaterChecklistFields(Map<String, String> fields, Struct struct) throws SQLException {
    if (struct == null) {
      return;
    }
    Object[] attrs = struct.getAttributes();
    putIfPresent(fields, "Site access code", stringValue(attrs, 3));
    putIfPresent(fields, "Main access road number", stringValue(attrs, 4));
    putIfPresent(fields, "Main watershed description", stringValue(attrs, 5));
    putIfPresent(fields, "Drinking water answer", stringValue(attrs, 6));
    putIfPresent(fields, "Water intake comment", stringValue(attrs, 7));
    putIfPresent(fields, "Intake to cutblock distance", stringValue(attrs, 8));
    putIfPresent(fields, "Water intake connectivity", stringValue(attrs, 9));
    putIfPresent(fields, "Special resource answer", stringValue(attrs, 11));
    putIfPresent(fields, "Reported disturbance ind", stringValue(attrs, 13));
    putIfPresent(fields, "Stream crossings ind", stringValue(attrs, 21));
    putIfPresent(fields, "Roads parallel to stream ind", stringValue(attrs, 22));
    putIfPresent(fields, "Unstable slopes ind", stringValue(attrs, 23));
    putIfPresent(fields, "Sensitive soils ind", stringValue(attrs, 24));
    putIfPresent(fields, "Adjacent harvesting ind", stringValue(attrs, 25));
    putIfPresent(fields, "Livestock concerns ind", stringValue(attrs, 26));
    putIfPresent(fields, "Other activity ind", stringValue(attrs, 27));
    putIfPresent(fields, "Other activity description", stringValue(attrs, 28));
    putIfPresent(fields, "Invasive plant", stringValue(attrs, 34));
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
            fields.put(rowPrefix + (row + 1) + " - Field " + (i + 1), value);
          }
        }
      }
    }
  }

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
