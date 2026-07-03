package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.ChecklistRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps legacy protocol-checklist GET procedures for BIO/SLB, RIP, and WTR.
 */
@Repository
public class ChecklistRepositoryImpl extends AbstractFrepRepository implements ChecklistRepository {

  static final String BIO_OPENING_PACKAGE = "frep_210_bio_opening";
  static final String BIO_STRATUM_PACKAGE = "FREP_211_BioStratum";
  static final String BIO_PLOT_PACKAGE = "FREP_212_BioPlot";
  static final String TOMBSTONE_PROC = "FREP_TOMBSTONE_GET";

  static final String STAND_TABLE_ARRAY = "THE.FREP_STAND_TABLE_VARRAY";
  static final String CWD_TABLE_ARRAY = "THE.FREP_CWD_TABLE_VARRAY";

  private static final String EMPTY_RESOURCE_VALUE_ID = "";

  public ChecklistRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
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

  /** The checklist's first stratum id (a checklist may have 0..n strata); "" when none. */
  private String resolveFirstBioStratumId(String checklistId) {
    List<String> ids = jdbcTemplate.query(
        "SELECT stratum_id FROM the.biodiversity_stratum WHERE biodiversity_checklist_id = ? "
            + "ORDER BY stratum_number, stratum_id",
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

    // FREP_211.get's status SELECT INTO (deployed line 139) is unguarded: with a blank
    // resource_value_id, to_number('') is NULL, the WHERE matches no biodiversity_checklist row, and
    // the proc dies with ORA-01403 -> ORA-06512 -> HTTP 500. A blank id means there is no checklist
    // row (a bogus/not-yet-persisted id), so there's nothing to load -- return an empty section
    // instead of letting the proc crash.
    if (resourceValueId.isBlank()) {
      return ChecklistSectionData.emptySection();
    }

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
