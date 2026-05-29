package ca.bc.gov.nrs.frep.repository.frep;

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
public class AcceptedSitesRepository extends AbstractFrepRepository {

  static final String PACKAGE_NAME = "FREP_200_ACCEPTED_SITES";
  static final String ARRAY_TYPE_NAME = "THE.FREP_ACC_SITES_VARRAY";

  public AcceptedSitesRepository(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Loads accepted/targeted sites for a district and master-list year.
   *
   * <p>Legacy equivalent: {@code FrepAcceptedSitesManager.getAcceptedSites}.
   */
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
