package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import ca.bc.gov.nrs.frep.struct.v1.frep.BecRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.CodeOptionResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.RejectionReasonResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Configuration lookups backed by the legacy Oracle schema via
 * {@link CodeListRepository}.
 */
@Service
public class ConfigurationService {

  private static final String[] CODE_KEYS = {"CODE", "code"};
  private static final String[] DESC_KEYS = {
      "DESCRIPTION", "description",
      "EXT_DESCRIPTION", "ext_description"
  };

  /**
   * CWD decay classes the legacy code list carries but FREP does not sample. Class 5 appears on
   * the field cards for reference only — a FREP transect records classes 1-4 — so it is dropped
   * here rather than in the UI, keeping one source of truth for every consumer of the list.
   *
   * <p>Filtered as an exclusion rather than a 1-4 allow-list on purpose: if the procedure ever
   * returns codes in an unexpected format, an exclusion degrades to "shows everything" instead of
   * an empty dropdown.
   */
  private static final Set<String> UNSAMPLED_CWD_DECAY_CODES = Set.of("5");

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

  /**
   * Master-list years for the FREP700 <em>Generate</em> screen: every existing year PLUS the next
   * not-yet-created year ({@code MAX(effective_year) + 1}), via {@code get_new_masterlist_code} —
   * so a sys-admin can select and generate the upcoming year (legacy
   * {@code frep.lookup.newMasterListCodes}; generating inserts the {@code frep_evaluation_year} row).
   *
   * <p>{@code current} marks the latest <em>existing</em> year — the synthetic next year is never
   * current — so the screen still defaults to the active year and offers the new one as a choice.
   */
  public List<MasterListYearResponse> getNewMasterListYears() {
    List<Map<String, Object>> existing = codeListRepository.getMasterListYearCode();
    String currentYear = existing.isEmpty()
        ? null
        : toMasterListYearResponse(existing.get(0), false).effectiveYear();

    List<Map<String, Object>> rows = codeListRepository.getNewMasterListYearCode();
    List<MasterListYearResponse> years = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      MasterListYearResponse year = toMasterListYearResponse(row, false);
      if (year.effectiveYear() == null) {
        continue;
      }
      years.add(year.effectiveYear().equals(currentYear)
          ? toMasterListYearResponse(row, true)
          : year);
    }
    return years;
  }


  @Cacheable("orgUnits")
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

  @Cacheable("rejectionReasons")
  public List<RejectionReasonResponse> getRejectionReasons() {
    return codeListRepository.getSiteResourceReasonCode().stream()
        .map(ConfigurationService::toRejectionReasonResponse)
        .filter(r -> r.code() != null)
        .toList();
  }

  /** Riparian stream RMA class options for the FREP230 stream-class dropdowns. */
  @Cacheable("streamClasses")
  public List<CodeOptionResponse> getStreamClasses() {
    return codeListRepository.getStreamClassCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Site-access options for the FREP301 Administration "Access type" dropdown. */
  @Cacheable("siteAccessCodes")
  public List<CodeOptionResponse> getSiteAccessCodes() {
    return codeListRepository.getSiteAccessCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Block-status options for the "Add Target Site" opening-search dropdown (SIL_CODE_LISTS). */
  @Cacheable("blockStatusCodes")
  public List<CodeOptionResponse> getBlockStatusCodes() {
    return codeListRepository.getBlockStatusCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Open-category options for the opening-search dropdown. */
  @Cacheable("openCategoryCodes")
  public List<CodeOptionResponse> getOpenCategoryCodes() {
    return codeListRepository.getOpenCategoryCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Opening-status options for the opening-search dropdown. */
  @Cacheable("openingStatusCodes")
  public List<CodeOptionResponse> getOpeningStatusCodes() {
    return codeListRepository.getOpeningStatusCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Site-evaluation (rating) options for the FREP210 Opening "Rating" dropdown. */
  @Cacheable("siteEvaluationCodes")
  public List<CodeOptionResponse> getSiteEvaluationCodes() {
    return codeListRepository.getEvaluationCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Biodiversity stratum-type options for the FREP211 "Stratum type" dropdown. */
  @Cacheable("strataTypes")
  public List<CodeOptionResponse> getStrataTypes() {
    return codeListRepository.getStratumTypeCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /**
   * CHR feature-class options for the feature and composite-feature "Feature class" dropdowns.
   *
   * <p>Cached like the rest: these are code tables, unchanged between releases, and every feature
   * editor opened would otherwise re-read them.
   */
  @Cacheable("chrFeatureClassCodes")
  public List<CodeOptionResponse> getChrFeatureClassCodes() {
    return toOptions(codeListRepository.getChrFeatureClassCode());
  }

  /** CHR information-source options for the feature and composite-feature dropdowns. */
  @Cacheable("chrFeatureInfoSourceCodes")
  public List<CodeOptionResponse> getChrFeatureInfoSourceCodes() {
    return toOptions(codeListRepository.getChrFeatureInfoSourceCode());
  }

  /** CHR reserve-type options for the location and management-strategy reserve dropdowns. */
  @Cacheable("chrReserveTypeCodes")
  public List<CodeOptionResponse> getChrReserveTypeCodes() {
    return toOptions(codeListRepository.getChrReserveTypeCode());
  }

  /**
   * CHR rating options for the feature and block-summary "Rating" dropdowns.
   *
   * <p>Distinct from {@link #getSiteEvaluationCodes()}, which serves SLR from a different table.
   */
  @Cacheable("chrSiteEvaluationCodes")
  public List<CodeOptionResponse> getChrSiteEvaluationCodes() {
    return toOptions(codeListRepository.getChrSiteEvaluationCode());
  }

  /** CHR participant-role options for the contacts "Role" dropdown. */
  @Cacheable("chrParticipantRoleCodes")
  public List<CodeOptionResponse> getChrParticipantRoleCodes() {
    return toOptions(codeListRepository.getChrParticipantRoleCode());
  }

  /** Shared mapping for the CHR code lists — cursor rows to options, dropping any without a code. */
  private static List<CodeOptionResponse> toOptions(List<Map<String, Object>> rows) {
    return rows.stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /**
   * Resource-value status options. Pass the code to exclude, or {@code null}/blank for every
   * status — the data-extract report filter hides {@code REJ}, Site Details offers all of them.
   */
  @Cacheable("resourceValueStatusCodes")
  public List<CodeOptionResponse> getResourceValueStatusCodes(String excludeStatusCode) {
    return codeListRepository.getResourceValueStatusCode(excludeStatusCode).stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Checklist-status options for the CHR data-extract report filter (FREPRPT022). */
  @Cacheable("checklistStatusCodes")
  public List<CodeOptionResponse> getChecklistStatusCodes() {
    return codeListRepository.getChecklistStatusCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Tree-species options for the FREP212 Stand / CWD "Spp." dropdowns. */
  @Cacheable("speciesCodes")
  public List<CodeOptionResponse> getSpeciesCodes() {
    return codeListRepository.getFrepSpeciesCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Wildlife-tree decay-class options for the FREP212 Stand "WT Class" dropdown. */
  @Cacheable("wildlifeTreeDecayCodes")
  public List<CodeOptionResponse> getWildlifeTreeDecayCodes() {
    return codeListRepository.getWildlifeTreeDecayCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /**
   * CWD decay-class options for the FREP212 Coarse Woody Debris "Decay Class" dropdown, limited
   * to the classes FREP samples — see {@link #UNSAMPLED_CWD_DECAY_CODES}.
   */
  @Cacheable("cwdDecayCodes")
  public List<CodeOptionResponse> getCwdDecayCodes() {
    return codeListRepository.getCwdDecayClassCode().stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .filter(o -> !UNSAMPLED_CWD_DECAY_CODES.contains(o.code()))
        .toList();
  }

  /** Evaluator options (the checklist's saved team) for the FREP212 "Evaluated By" dropdown. */
  public List<CodeOptionResponse> getEvaluators(String checklistId, String resourceType) {
    return codeListRepository.getEvaluatorCode(checklistId, resourceType).stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** BEC catalogue search for the FREP211 BEC picker (all criteria optional). */
  public List<BecRow> searchBec(String zone, String subzone, String variant, String phase,
      String siteSeries, String siteSeriesPhase, String seral) {
    return codeListRepository.searchBec(
        zone, subzone, variant, phase, siteSeries, siteSeriesPhase, seral);
  }

  /**
   * FREP checklist answer options (Yes/No/etc.) for indicator dropdowns. Pass the code to exclude,
   * or {@code null}/blank to return every answer.
   */
  public List<CodeOptionResponse> getChecklistAnswers(String excludeAnswerCode) {
    return codeListRepository.getChecklistAnswerCode(excludeAnswerCode).stream()
        .map(ConfigurationService::toCodeOption)
        .filter(o -> o.code() != null)
        .toList();
  }

  /** Maps a generic code-list cursor row ({@code code}, {@code description}). */
  static CodeOptionResponse toCodeOption(Map<String, Object> row) {
    String code = firstNonBlank(row, "CODE", "code");
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

    return new CodeOptionResponse(code, description != null ? description : "");
  }

  /**
   * Maps cursor columns returned by {@code get_site_resource_reason_code}:
   * {@code code} = {@code frep_site_resource_reason_code} (first column),
   * {@code description}.
   */
  static RejectionReasonResponse toRejectionReasonResponse(Map<String, Object> row) {
    String code = firstNonBlank(row, "FREP_SITE_RESOURCE_REASON_CODE",
        "frep_site_resource_reason_code", "CODE", "code");
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

    return new RejectionReasonResponse(code, description != null ? description : "");
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
