package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.GenerateMasterListRequest;
import ca.bc.gov.nrs.frep.dto.frep.MasterListAdminResponse;
import ca.bc.gov.nrs.frep.dto.frep.MasterListGenerationStat;
import ca.bc.gov.nrs.frep.repository.frep.MasterListCriteriaData;
import ca.bc.gov.nrs.frep.repository.frep.MasterListGenerationRow;
import ca.bc.gov.nrs.frep.repository.frep.MasterListRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Sys-admin API for FREP700 Generate Master List, backed by the legacy Oracle
 * schema via {@link MasterListRepository}.
 *
 * <p>Legacy equivalents: {@code FREP_700_GEN_MASTER.get} and {@code generate}.
 */
@Service
@Profile("oracle")
public class MasterListAdminService {

  private static final String DEFAULT_MIN_GROSS_AREA_HA = "2";
  private static final String DEFAULT_MAX_SITES_PER_DISTRICT = "300";

  private final MasterListRepository masterListRepository;
  private final LoggedUserHelper loggedUserHelper;

  public MasterListAdminService(
      MasterListRepository masterListRepository,
      LoggedUserHelper loggedUserHelper
  ) {
    this.masterListRepository = masterListRepository;
    this.loggedUserHelper = loggedUserHelper;
  }

  public MasterListAdminResponse getMasterListCriteria(String effectiveYear) {
    return toResponse(effectiveYear, masterListRepository.getCriteria(effectiveYear));
  }

  public MasterListAdminResponse generateMasterList(GenerateMasterListRequest request) {
    if (request == null || StringUtils.isBlank(request.effectiveYear())) {
      throw new IllegalArgumentException("effectiveYear is required");
    }

    String effectiveYear = request.effectiveYear().trim();
    masterListRepository.generate(
        effectiveYear,
        blankToEmpty(request.maxHarvestCompleteDate()),
        blankToEmpty(request.minHarvestCompleteDate()),
        formatDecimal(request.minOpeningGrossAreaHa(), DEFAULT_MIN_GROSS_AREA_HA),
        formatInteger(request.maxSitesPerDistrict(), DEFAULT_MAX_SITES_PER_DISTRICT),
        blankToEmpty(request.comments()),
        loggedUserHelper.getLoggedUserId()
    );

    return getMasterListCriteria(effectiveYear);
  }

  /** Regenerate the master list for a single district (FREP_700_GEN_MASTER.regenerate). */
  public MasterListAdminResponse regenerateDistrict(String effectiveYear, String orgUnitNo) {
    if (StringUtils.isBlank(effectiveYear) || StringUtils.isBlank(orgUnitNo)) {
      throw new IllegalArgumentException("effectiveYear and orgUnitNo are required");
    }
    masterListRepository.regenerateDistrict(
        effectiveYear.trim(), orgUnitNo.trim(), loggedUserHelper.getLoggedUserId());
    return getMasterListCriteria(effectiveYear.trim());
  }

  /** Save generation comments without regenerating (FREP_700_GEN_MASTER.save_comments). */
  public MasterListAdminResponse saveComments(String effectiveYear, String comments) {
    if (StringUtils.isBlank(effectiveYear)) {
      throw new IllegalArgumentException("effectiveYear is required");
    }
    masterListRepository.saveComments(
        effectiveYear.trim(), blankToEmpty(comments), loggedUserHelper.getLoggedUserId());
    return getMasterListCriteria(effectiveYear.trim());
  }

  /** Delete the generated master list for a year (FREP_700_GEN_MASTER.delete_list). */
  public MasterListAdminResponse deleteList(String effectiveYear) {
    if (StringUtils.isBlank(effectiveYear)) {
      throw new IllegalArgumentException("effectiveYear is required");
    }
    masterListRepository.deleteList(effectiveYear.trim());
    return getMasterListCriteria(effectiveYear.trim());
  }

  static MasterListAdminResponse toResponse(String effectiveYear, MasterListCriteriaData data) {
    List<MasterListGenerationStat> stats = data.generationStats().stream()
        .map(MasterListAdminService::toGenerationStat)
        .toList();
    return new MasterListAdminResponse(
        effectiveYear,
        data.minHarvestCompleteDate(),
        data.maxHarvestCompleteDate(),
        data.minOpeningGrossAreaHa(),
        data.maxSitesPerDistrict(),
        data.resourceEvaluationInd(),
        data.generationComments(),
        isGenerated(stats),
        stats
    );
  }

  static MasterListGenerationStat toGenerationStat(MasterListGenerationRow row) {
    String[] orgUnit = parseOrgUnitDisplay(row.orgUnitDisplay());
    return new MasterListGenerationStat(
        row.orgUnitNo(),
        orgUnit[0],
        orgUnit[1],
        row.totalAvailableSites(),
        row.totalSites()
    );
  }

  static boolean isGenerated(List<MasterListGenerationStat> stats) {
    return stats.stream().anyMatch(stat -> stat.selectedSites() > 0);
  }

  static String[] parseOrgUnitDisplay(String display) {
    if (display == null || display.isBlank()) {
      return new String[] {"", ""};
    }
    int separator = display.indexOf(" - ");
    if (separator >= 0) {
      return new String[] {
          display.substring(0, separator).trim(),
          display.substring(separator + 3).trim()
      };
    }
    return new String[] {"", display.trim()};
  }

  private static String blankToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Formats the gross-area number for the proc's {@code TO_NUMBER(p_min_opening_gross_area)}.
   * Uses a plain decimal with no trailing {@code .0} / scientific notation — {@code Double.toString(5.0)}
   * yields {@code "5.0"}, which regresses from the legacy value {@code "5"} and trips {@code ORA-01722}
   * on {@code TO_NUMBER}. {@code 5.0 -> "5"}, {@code 2.5 -> "2.5"}.
   */
  private static String formatDecimal(Double value, String defaultValue) {
    return value == null
        ? defaultValue
        : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  private static String formatInteger(Integer value, String defaultValue) {
    return value == null ? defaultValue : Integer.toString(value);
  }
}
