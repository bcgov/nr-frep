package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.repository.frep.FrepCodeListRepository;
import ca.bc.gov.nrs.frep.dto.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.dto.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolResponse;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Configuration lookups backed by the legacy Oracle schema via
 * {@link FrepCodeListRepository}.
 *
 * <p>Master-list years and protocols remain stubbed until their PL/SQL packages
 * are wired (Phase 1).
 */
@Service
@Profile("oracle")
public class JdbcConfigurationService implements ConfigurationService {

  private static final String[] CODE_KEYS = {"CODE", "code"};
  private static final String[] DESC_KEYS = {
      "DESCRIPTION", "description",
      "EXT_DESCRIPTION", "ext_description"
  };

  private static final List<MasterListYearResponse> MASTER_LIST_YEARS = List.of(
      new MasterListYearResponse("2024", "2024/2025", true),
      new MasterListYearResponse("2023", "2023/2024", false),
      new MasterListYearResponse("2022", "2022/2023", false)
  );

  private static final List<ProtocolResponse> PROTOCOLS = List.of(
      new ProtocolResponse("BIO", "Biodiversity"),
      new ProtocolResponse("RIP", "Riparian"),
      new ProtocolResponse("WAT", "Water Quality"),
      new ProtocolResponse("CHR", "Culture Heritage")
  );

  private final FrepCodeListRepository frepCodeListRepository;

  public JdbcConfigurationService(FrepCodeListRepository frepCodeListRepository) {
    this.frepCodeListRepository = frepCodeListRepository;
  }

  @Override
  public List<MasterListYearResponse> getMasterListYears() {
    return MASTER_LIST_YEARS;
  }

  @Override
  public List<OrgUnitResponse> getOrgUnits() {
    return frepCodeListRepository.getDistrictOrgUnitCode().stream()
        .map(JdbcConfigurationService::toOrgUnitResponse)
        .filter(o -> o.orgUnitNo() != null)
        .toList();
  }

  @Override
  public List<ProtocolResponse> getProtocols() {
    return PROTOCOLS;
  }

  static OrgUnitResponse toOrgUnitResponse(Map<String, Object> row) {
    String code = firstNonBlank(row, CODE_KEYS);
    String description = firstNonBlank(row, DESC_KEYS);

    if (code == null || description == null) {
      List<Object> ordered = List.copyOf(row.values());
      if (code == null && !ordered.isEmpty()) {
        code = blankToNull(ordered.get(0));
      }
      if (description == null && ordered.size() >= 2) {
        description = blankToNull(ordered.get(1));
      }
    }

    return mapFromProcedureRow(code, description != null ? description : "");
  }

  /**
   * Maps cursor columns returned by {@code get_district_org_unit_code}:
   * {@code code} = {@code org_unit_no}, {@code description} =
   * {@code org_unit_code || ' - ' || org_unit_name}.
   */
  static OrgUnitResponse mapFromProcedureRow(String code, String description) {
    String orgUnitCode = "";
    String orgUnitName = description != null ? description : "";
    if (description != null) {
      int separator = description.indexOf(" - ");
      if (separator >= 0) {
        orgUnitCode = description.substring(0, separator);
        orgUnitName = description.substring(separator + 3);
      }
    }
    return new OrgUnitResponse(code, orgUnitCode, orgUnitName);
  }

  private static String firstNonBlank(Map<String, Object> row, String... keys) {
    for (String key : keys) {
      String s = blankToNull(row.get(key));
      if (s != null) {
        return s;
      }
    }
    return null;
  }

  private static String blankToNull(Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    return s.isEmpty() ? null : s;
  }
}
