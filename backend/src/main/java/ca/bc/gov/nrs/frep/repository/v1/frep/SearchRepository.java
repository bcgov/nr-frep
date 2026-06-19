package ca.bc.gov.nrs.frep.repository.v1.frep;

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
 * Wraps legacy FREP400 checklist search and FREP410 client search procedures.
 */
@Repository
@Profile("oracle")
public class SearchRepository extends AbstractFrepRepository {

  static final String CHECKLIST_SEARCH_PROC = "FREP_400_CHECKLIST_SEARCH";
  static final String CLIENT_SEARCH_PROC = "FREP_410_CLIENT_SEARCH";
  static final String CHECKLIST_SEARCH_TYPE = "THE.FREP_CHKLST_SEARCH_VW_OBJECT";
  static final String CHECKLIST_SEARCH_ARRAY = "THE.FREP_CHKLST_SEARCH_VW_VARRAY";
  static final String CLIENT_SEARCH_TYPE = "THE.FREP_CLIENT_SEARCH_VW_OBJECT";
  static final String CLIENT_SEARCH_ARRAY = "THE.FREP_CLIENT_SEARCH_VW_VARRAY";

  public SearchRepository(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
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
