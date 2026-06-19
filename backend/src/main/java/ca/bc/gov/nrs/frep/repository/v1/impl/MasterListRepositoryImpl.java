package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.MasterListRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps legacy package {@code FREP_700_GEN_MASTER} (FREP700 Generate Master List).
 */
@Repository
@Profile("oracle")
public class MasterListRepositoryImpl extends AbstractFrepRepository implements MasterListRepository {

  static final String PACKAGE_NAME = "FREP_700_GEN_MASTER";
  static final String RESULTS_ARRAY_TYPE = "THE.FREP_GENERATION_RESULTS_VARRAY";

  public MasterListRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Loads eligibility criteria and per-district generation stats for a year.
   *
   * <p>Legacy equivalent: {@code Frep700MasterListDataManager.get}.
   */
  public MasterListCriteriaData getCriteria(String effectiveYear) {
    String call = "{call " + PACKAGE_NAME + ".get (?,?,?,?,?,?,?,?,?)}";
    return executeCall(call, cs -> {
      cs.setString(1, effectiveYear);
      cs.registerOutParameter(2, Types.VARCHAR);
      cs.registerOutParameter(3, Types.VARCHAR);
      cs.registerOutParameter(4, Types.NUMERIC);
      cs.registerOutParameter(5, Types.NUMERIC);
      cs.registerOutParameter(6, Types.VARCHAR);
      cs.registerOutParameter(7, Types.VARCHAR);
      cs.setString(8, null);
      cs.registerOutParameter(8, Types.VARCHAR);
      cs.registerOutParameter(9, Types.ARRAY, RESULTS_ARRAY_TYPE);
    }, cs -> {
      throwIfError(PACKAGE_NAME, "get", cs.getString(8));
      return new MasterListCriteriaData(
          blankToEmpty(cs.getString(2)),
          blankToEmpty(cs.getString(3)),
          toDouble(cs.getObject(4)),
          toInteger(cs.getObject(5)),
          blankToEmpty(cs.getString(6)),
          blankToEmpty(cs.getString(7)),
          readGenerationResultsArray(cs.getArray(9))
      );
    });
  }

  /**
   * Generates the provincial master list for all districts.
   *
   * <p>Legacy equivalent: {@code Frep700MasterListDataManager.generate}.
   */
  public void generate(
      String effectiveYear,
      String maxHarvestCompleteDate,
      String minHarvestCompleteDate,
      String minOpeningGrossAreaHa,
      String maxSitesPerDistrict,
      String generationComments,
      String entryUserId
  ) {
    String call = "{call " + PACKAGE_NAME + ".generate (?,?,?,?,?,?,?,?)}";
    executeCall(call, cs -> {
      cs.setString(1, effectiveYear);
      cs.setString(2, maxHarvestCompleteDate);
      cs.setString(3, minHarvestCompleteDate);
      cs.setString(4, minOpeningGrossAreaHa);
      cs.setString(5, maxSitesPerDistrict);
      cs.setString(6, generationComments);
      cs.registerOutParameter(7, Types.VARCHAR);
      cs.setString(8, entryUserId);
    }, cs -> {
      throwIfError(PACKAGE_NAME, "generate", cs.getString(7));
      return null;
    });
  }

  /**
   * Regenerate the master list for a single district. Legacy equivalent:
   * {@code Frep700MasterListDataManager.regenerate} ({@code regenerate(year, org_unit_no,
   * error OUT, user_id)}).
   */
  public void regenerateDistrict(String effectiveYear, String orgUnitNo, String userId) {
    String call = "{call " + PACKAGE_NAME + ".regenerate (?,?,?,?)}";
    executeCall(call, cs -> {
      cs.setString(1, effectiveYear);
      cs.setString(2, orgUnitNo);
      cs.registerOutParameter(3, Types.VARCHAR);
      cs.setString(4, userId);
    }, cs -> {
      throwIfError(PACKAGE_NAME, "regenerate", cs.getString(3));
      return null;
    });
  }

  /**
   * Save the generation comments for a year without regenerating. Legacy equivalent:
   * {@code Frep700MasterListDataManager.save_comments} ({@code save_comments(year, comments,
   * user_id, error OUT)}).
   */
  public void saveComments(String effectiveYear, String generationComments, String userId) {
    String call = "{call " + PACKAGE_NAME + ".save_comments (?,?,?,?)}";
    executeCall(call, cs -> {
      cs.setString(1, effectiveYear);
      cs.setString(2, generationComments);
      cs.setString(3, userId);
      cs.registerOutParameter(4, Types.VARCHAR);
    }, cs -> {
      throwIfError(PACKAGE_NAME, "save_comments", cs.getString(4));
      return null;
    });
  }

  /**
   * Delete the generated master list for a year. Legacy equivalent:
   * {@code Frep700MasterListDataManager.delete_list} ({@code delete_list(year, error OUT)}).
   */
  public void deleteList(String effectiveYear) {
    String call = "{call " + PACKAGE_NAME + ".delete_list (?,?)}";
    executeCall(call, cs -> {
      cs.setString(1, effectiveYear);
      cs.registerOutParameter(2, Types.VARCHAR);
    }, cs -> {
      throwIfError(PACKAGE_NAME, "delete_list", cs.getString(2));
      return null;
    });
  }

  private static List<MasterListGenerationRow> readGenerationResultsArray(Array array)
      throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object[] elements = (Object[]) array.getArray();
    List<MasterListGenerationRow> rows = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (element instanceof Struct struct) {
        rows.add(fromGenerationStruct(struct));
      }
    }
    return rows;
  }

  /**
   * Attribute order matches {@code FREP_GENERATION_RESULTS_OBJECT} in legacy DDL.
   */
  static MasterListGenerationRow fromGenerationStruct(Struct struct) throws SQLException {
    Object[] attrs = struct.getAttributes();
    return new MasterListGenerationRow(
        stringAttr(attrs, 0),
        stringAttr(attrs, 1),
        intAttr(attrs, 4),
        intAttr(attrs, 5),
        stringAttr(attrs, 6)
    );
  }

  private static String stringAttr(Object[] attrs, int index) {
    if (attrs == null || index >= attrs.length || attrs[index] == null) {
      return "";
    }
    return attrs[index].toString().trim();
  }

  private static int intAttr(Object[] attrs, int index) {
    if (attrs == null || index >= attrs.length || attrs[index] == null) {
      return 0;
    }
    if (attrs[index] instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(attrs[index].toString().trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static Double toDouble(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(value.toString().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Integer toInteger(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(value.toString().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String blankToEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
