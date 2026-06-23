package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.RandomListRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import oracle.jdbc.OracleConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps legacy package {@code FREP_100_DIST_RAND_LIST} (FREP100 District Random List).
 */
@Repository
public class RandomListRepositoryImpl extends AbstractFrepRepository implements RandomListRepository {

  static final String PACKAGE_NAME = "FREP_100_DIST_RAND_LIST";
  static final String ARRAY_TYPE_NAME = "THE.FREP_RANDOM_LIST_VARRAY";

  public RandomListRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Loads randomly generated sites for a master-list year and optional district.
   *
   * <p>Legacy equivalent: {@code Frep100DataManager.getSelectedSites}.
   */
  public RandomListResult findRandomList(String effectiveYear, String orgUnitNo) {
    String call = "{call " + PACKAGE_NAME + ".GET (?,?,?,?,?,?,?,?,?,?)}";
    return executeCall(call, cs -> {
      cs.setString(1, effectiveYear);
      cs.setString(2, blankToNull(orgUnitNo));
      cs.setString(3, "");
      cs.registerOutParameter(4, Types.VARCHAR);
      cs.registerOutParameter(5, Types.VARCHAR);
      cs.registerOutParameter(6, Types.VARCHAR);
      cs.registerOutParameter(7, Types.VARCHAR);
      cs.registerOutParameter(8, Types.VARCHAR);
      setInOutString(cs, 9, null);
      setEmptyRandomListArray(cs, 10);
      cs.registerOutParameter(10, Types.ARRAY, ARRAY_TYPE_NAME);
    }, cs -> {
      throwIfError(PACKAGE_NAME, "GET", cs.getString(9));
      RandomListSummary summary = new RandomListSummary(
          nullToEmpty(cs.getString(8)),
          nullToEmpty(cs.getString(4)),
          nullToEmpty(cs.getString(5)),
          nullToEmpty(cs.getString(6)),
          nullToEmpty(cs.getString(7))
      );
      return new RandomListResult(summary, readRandomListArray(cs.getArray(10)));
    });
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static void setEmptyRandomListArray(CallableStatement cs, int index) throws SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    cs.setArray(index, connection.createOracleArray(ARRAY_TYPE_NAME, new Object[0]));
  }

  private static List<RandomListRow> readRandomListArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object[] elements = (Object[]) array.getArray();
    List<RandomListRow> rows = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (element instanceof Struct struct) {
        rows.add(fromStruct(struct));
      }
    }
    return rows;
  }

  static RandomListRow fromStruct(Struct struct) throws SQLException {
    Object[] attrs = struct.getAttributes();
    return new RandomListRow(
        stringAttr(attrs, 0),
        stringAttr(attrs, 1),
        stringAttr(attrs, 2),
        stringAttr(attrs, 3),
        stringAttr(attrs, 4),
        stringAttr(attrs, 6),
        stringAttr(attrs, 8),
        stringAttr(attrs, 9),
        stringAttr(attrs, 10),
        stringAttr(attrs, 11),
        stringAttr(attrs, 12),
        stringAttr(attrs, 13),
        stringAttr(attrs, 14),
        stringAttr(attrs, 15),
        readExistingChecklistLabels(attrs, 23)
    );
  }

  /**
   * Reads each existing checklist's display label from the {@code checklist_common_varray}
   * ({@code FREP_CHECKLIST_COMMON_OBJECT.display_checkList}, attr 5) — one entry per checklist,
   * so multi-instance protocols (e.g. multiple riparian checklists) are distinguishable rather
   * than collapsing to a repeated type code.
   */
  private static List<String> readExistingChecklistLabels(Object[] attrs, int index) throws SQLException {
    if (attrs == null || index >= attrs.length || attrs[index] == null) {
      return List.of();
    }
    if (!(attrs[index] instanceof Array checklistArray)) {
      return List.of();
    }
    Object[] elements = (Object[]) checklistArray.getArray();
    List<String> labels = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (element instanceof Struct checklistStruct) {
        String label = stringAttr(checklistStruct.getAttributes(), 5);
        if (!label.isBlank()) {
          labels.add(label);
        }
      }
    }
    return labels;
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

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
