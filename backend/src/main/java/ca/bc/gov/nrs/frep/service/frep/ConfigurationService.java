package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.repository.frep.CodeListRepository;
import ca.bc.gov.nrs.frep.dto.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.dto.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Configuration lookups backed by the legacy Oracle schema via
 * {@link CodeListRepository}.
 */
@Service
@Profile("oracle")
public class ConfigurationService {

  private static final String[] CODE_KEYS = {"CODE", "code"};
  private static final String[] DESC_KEYS = {
      "DESCRIPTION", "description",
      "EXT_DESCRIPTION", "ext_description"
  };

  private final CodeListRepository codeListRepository;

  public ConfigurationService(CodeListRepository codeListRepository) {
    this.codeListRepository = codeListRepository;
  }


  public List<MasterListYearResponse> getMasterListYears() {
    List<Map<String, Object>> rows = codeListRepository.getMasterListYearCode();
    List<MasterListYearResponse> years = new ArrayList<>(rows.size());
    for (int i = 0; i < rows.size(); i++) {
      MasterListYearResponse year = toMasterListYearResponse(rows.get(i), i == 0);
      if (year.effectiveYear() != null) {
        years.add(year);
      }
    }
    return years;
  }


  public List<OrgUnitResponse> getOrgUnits() {
    return codeListRepository.getDistrictOrgUnitCode().stream()
        .map(ConfigurationService::toOrgUnitResponse)
        .filter(o -> o.orgUnitNo() != null)
        .toList();
  }


  public List<ProtocolResponse> getProtocols() {
    return codeListRepository.getResourceValue().stream()
        .map(ConfigurationService::toProtocolResponse)
        .filter(p -> p.code() != null)
        .toList();
  }

  /**
   * Maps cursor columns returned by {@code get_resource_value}:
   * {@code code} = {@code frep_resource_value_type_code}, {@code description}
   * = protocol display name.
   */
  static ProtocolResponse toProtocolResponse(Map<String, Object> row) {
    String code = firstNonBlank(row, CODE_KEYS);
    String name = firstNonBlank(row, DESC_KEYS);

    if (code == null || name == null) {
      List<Object> ordered = List.copyOf(row.values());
      if (code == null && !ordered.isEmpty()) {
        code = blankToNull(ordered.get(0));
      }
      if (name == null && ordered.size() >= 2) {
        name = blankToNull(ordered.get(1));
      }
    }

    return new ProtocolResponse(code, name != null ? name : "");
  }

  /**
   * Maps cursor columns returned by {@code get_masterlist_year_code}:
   * {@code code} = {@code effective_year}, {@code description} =
   * {@code effective_year || '/' || (effective_year + 1)}.
   *
   * <p>Legacy screens default to the first row (highest year); {@code current}
   * mirrors that behaviour.
   */
  static MasterListYearResponse toMasterListYearResponse(Map<String, Object> row, boolean current) {
    String code = firstNonBlank(row, CODE_KEYS);
    String label = firstNonBlank(row, DESC_KEYS);

    if (code == null || label == null) {
      List<Object> ordered = List.copyOf(row.values());
      if (code == null && !ordered.isEmpty()) {
        code = blankToNull(ordered.get(0));
      }
      if (label == null && ordered.size() >= 2) {
        label = blankToNull(ordered.get(1));
      }
    }

    if (label == null && code != null) {
      label = formatMasterListYearLabel(code);
    }

    return new MasterListYearResponse(code, label != null ? label : "", current);
  }

  static String formatMasterListYearLabel(String effectiveYear) {
    try {
      int year = Integer.parseInt(effectiveYear.trim());
      return year + "/" + (year + 1);
    } catch (NumberFormatException e) {
      return effectiveYear;
    }
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
