package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.SiteDetailRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceSaveRequest;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import oracle.jdbc.OracleConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps legacy package {@code FREP_110_SITE_DETAILS} (FREP110 Site Details).
 */
@Repository
public class SiteDetailRepositoryImpl extends AbstractFrepRepository implements SiteDetailRepository {

  static final String PACKAGE_NAME = "FREP_110_SITE_DETAILS";
  static final String RESOURCE_ARRAY_TYPE = "THE.FREP_RESOURCE_VARRAY";
  static final String RESOURCE_OBJECT_TYPE = "THE.FREP_RESOURCE_OBJECT";

  public SiteDetailRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Loads site header and resource values for a selected site.
   *
   * <p>Legacy equivalent: {@code FrepResourceDataManager.getSiteDetails}.
   */
  public SiteDetailData findSiteDetail(String frepSelectedSiteId) {
    return getSiteDetail(frepSelectedSiteId, null, null);
  }

  @Override
  public SiteDetailData findSiteDetailByOpening(String openingId, String masterList) {
    // Load by opening with a null selected-site id: GET param 8 is p_master_list, param 9 is
    // p_opening_id (IN OUT). The proc resolves an existing site for opening+year, or returns the
    // opening header + a blank row per protocol type when none exists yet (new targeted opening).
    return getSiteDetail(null, masterList, openingId);
  }

  private SiteDetailData getSiteDetail(String frepSelectedSiteId, String masterList, String openingId) {
    String call = "{call " + PACKAGE_NAME + ".GET (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}";
    return executeCall(call, cs -> {
      setInOutString(cs, 1, frepSelectedSiteId);
      setEmptyResourceArray(cs, 2);
      setInOutString(cs, 3, null);
      cs.registerOutParameter(4, Types.VARCHAR);
      cs.registerOutParameter(5, Types.VARCHAR);
      cs.registerOutParameter(6, Types.VARCHAR);
      cs.registerOutParameter(7, Types.VARCHAR);
      setInOutString(cs, 8, masterList); // p_master_list (year) — drives the opening+year resolve
      setInOutString(cs, 9, openingId); // p_opening_id — load by opening when the site id is null
      cs.registerOutParameter(10, Types.VARCHAR);
      cs.registerOutParameter(11, Types.VARCHAR);
      cs.registerOutParameter(12, Types.VARCHAR);
      cs.registerOutParameter(13, Types.VARCHAR);
      cs.registerOutParameter(14, Types.VARCHAR);
      cs.registerOutParameter(15, Types.VARCHAR);
      cs.registerOutParameter(16, Types.VARCHAR);
      cs.registerOutParameter(17, Types.VARCHAR);
      cs.registerOutParameter(18, Types.VARCHAR);
      cs.registerOutParameter(19, Types.VARCHAR);
      cs.registerOutParameter(20, Types.VARCHAR);
      cs.registerOutParameter(21, Types.VARCHAR);
      cs.registerOutParameter(22, Types.VARCHAR);
    }, cs -> {
      throwIfError(PACKAGE_NAME, "GET", cs.getString(3));
      return new SiteDetailData(
          stringValue(cs.getString(1)),
          stringValue(cs.getString(8)),
          stringValue(cs.getString(18)),
          stringValue(cs.getString(19)),
          stringValue(cs.getString(5)),
          stringValue(cs.getString(6)),
          stringValue(cs.getString(20)),
          stringValue(cs.getString(9)),
          stringValue(cs.getString(10)),
          stringValue(cs.getString(11)),
          stringValue(cs.getString(12)),
          stringValue(cs.getString(13)),
          stringValue(cs.getString(14)),
          stringValue(cs.getString(4)),
          stringValue(cs.getString(15)),
          readResourceArray(cs.getArray(2))
      );
    });
  }

  /**
   * Persist resource-value evaluations via {@code FREP_110_SITE_DETAILS.SAVE} (7 params; the
   * resource VARRAY drives accept/reject/target — ACC/TAR spawns a checklist, REJ removes it). The
   * proc reads resource_id, resource_type, stat_code, rejection_rationale,
   * frep_site_resource_reason_code, resource_comment and revision_count from each struct. Returns
   * the (possibly echoed) site id; throws {@code StoredProcedureException} on a proc error.
   */
  public String saveResources(
      String frepSelectedSiteId,
      String openingId,
      String orgUnitNo,
      String effectiveYear,
      List<SiteResourceSaveRequest> resources,
      String userId
  ) {
    return executeCall(
        "{call " + PACKAGE_NAME + ".SAVE (?,?,?,?,?,?,?)}",
        cs -> {
          setInOutString(cs, 1, frepSelectedSiteId);
          cs.setString(2, openingId);
          cs.setString(3, orgUnitNo);
          cs.setString(4, effectiveYear);
          cs.setObject(5, buildStructArray(cs, RESOURCE_ARRAY_TYPE, RESOURCE_OBJECT_TYPE, resources,
              r -> new Object[] {
                  // resource_id + revision_count are NUMBER attributes — a blank string can't
                  // convert (Oracle 17059), and a new resource must send a null id so the proc
                  // takes its insert branch. Null all blanks, mirroring the legacy array bean.
                  blankToNull(r.resourceValueId()), null, blankToNull(r.resourceType()), null,
                  blankToNull(r.statusCode()), null, blankToNull(r.rationale()),
                  blankToNull(r.rejectionReasonCode()), blankToNull(r.otherComments()),
                  blankToNull(r.revisionCount()), null, blankToNull(userId)
              }));
          setInOutString(cs, 6, userId);
          setInOutString(cs, 7, null);
        },
        cs -> {
          throwIfError(PACKAGE_NAME, "SAVE", cs.getString(7));
          return stringValue(cs.getString(1));
        }
    );
  }

  /**
   * Resolves a checklist id from a resource value, mirroring legacy
   * {@code frep_get_checklist}.
   */
  public String resolveChecklistId(String resourceValueId, String resourceType) {
    if (resourceValueId == null || resourceValueId.isBlank()
        || resourceType == null || resourceType.isBlank()) {
      return "";
    }
    List<String> rows = jdbcTemplate.query(
        "SELECT frep_get_checklist(?, ?) FROM dual",
        (rs, rowNum) -> rs.getString(1),
        resourceValueId.trim(),
        resourceType.trim()
    );
    return rows.isEmpty() || rows.get(0) == null ? "" : rows.get(0).trim();
  }

  /**
   * Codes whose {@code EXPIRY_DATE} has passed. Read directly rather than through a proc — no
   * code-list procedure exposes the dates, and this table is already queried natively elsewhere
   * (see {@code AcceptedSitesRepositoryImpl} / {@code SearchRepositoryImpl}), so the SELECT grant is
   * in place. THE-qualified for the same reason those are: the app connects as an invoker without
   * the package's definer rights.
   *
   * <p>A null {@code EXPIRY_DATE} is treated as never expiring.
   */
  public Set<String> retiredResourceTypes() {
    Set<String> retired = new HashSet<>();
    jdbcTemplate.query(
        "SELECT frep_resource_value_type_code FROM THE.frep_resource_value_type_code "
            + "WHERE expiry_date IS NOT NULL AND expiry_date <= SYSDATE",
        rs -> {
          String code = rs.getString(1);
          if (code != null) {
            retired.add(code.trim().toUpperCase(Locale.ROOT));
          }
        });
    return retired;
  }

  private static void setEmptyResourceArray(CallableStatement cs, int index) throws SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    cs.setArray(index, connection.createOracleArray(RESOURCE_ARRAY_TYPE, new Object[0]));
    cs.registerOutParameter(index, Types.ARRAY, RESOURCE_ARRAY_TYPE);
  }

  private static List<SiteResourceRow> readResourceArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object[] elements = (Object[]) array.getArray();
    List<SiteResourceRow> rows = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (element instanceof Struct struct) {
        rows.add(fromResourceStruct(struct));
      }
    }
    return rows;
  }

  static SiteResourceRow fromResourceStruct(Struct struct) throws SQLException {
    Object[] attrs = struct.getAttributes();
    return new SiteResourceRow(
        stringAttr(attrs, 0),
        stringAttr(attrs, 1),
        stringAttr(attrs, 2),
        stringAttr(attrs, 3),
        stringAttr(attrs, 4),
        stringAttr(attrs, 5),
        stringAttr(attrs, 6),
        stringAttr(attrs, 7),
        stringAttr(attrs, 8),
        stringAttr(attrs, 9)
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

  private static String stringValue(String value) {
    return value == null ? "" : value.trim();
  }

  /** Null for blank values so they don't get pushed into NUMBER/typed struct attributes. */
  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
