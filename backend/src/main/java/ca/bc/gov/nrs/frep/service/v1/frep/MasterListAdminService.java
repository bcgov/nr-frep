package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.exception.ConflictFoundException;
import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.exception.StoredProcedureException;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import ca.bc.gov.nrs.frep.struct.v1.frep.GenerateMasterListRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.MasterListAdminResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.MasterListGenerationStat;
import ca.bc.gov.nrs.frep.repository.v1.bean.MasterListCriteriaData;
import ca.bc.gov.nrs.frep.repository.v1.bean.MasterListGenerationRow;
import ca.bc.gov.nrs.frep.repository.v1.MasterListRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Sys-admin API for FREP700 Generate Master List, backed by the legacy Oracle
 * schema via {@link MasterListRepository}.
 *
 * <p>Legacy equivalents: {@code FREP_700_GEN_MASTER.get} and {@code generate}.
 */
@Service
public class MasterListAdminService {

  private static final String DEFAULT_MIN_GROSS_AREA_HA = "2";
  private static final String DEFAULT_MAX_SITES_PER_DISTRICT = "300";

  // Generate-List field rules, ported from legacy Frep700ValidationManager.
  private static final LocalDate MIN_HARVEST_DATE = LocalDate.of(1997, 6, 15);
  private static final LocalDate MAX_HARVEST_DATE = LocalDate.of(2050, 12, 31);
  private static final double MAX_GROSS_AREA_HA = 99999.9999;
  private static final int MAX_GROSS_AREA_DECIMAL_PLACES = 4;
  private static final int MIN_SITES_PER_DISTRICT = 1;
  private static final int MAX_SITES_PER_DISTRICT = 500;
  private static final int MAX_COMMENTS_LENGTH = 4000;

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
    validateGenerateRequest(request);
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

  /** Save generation comments without regenerating (FREP_700_GEN_MASTER.save_comments). */
  public MasterListAdminResponse saveComments(String effectiveYear, String comments) {
    List<String> errors = new ArrayList<>();
    validateComments(comments, errors);
    throwIfInvalid(errors);
    masterListRepository.saveComments(
        effectiveYear.trim(), blankToEmpty(comments), loggedUserHelper.getLoggedUserId());
    return getMasterListCriteria(effectiveYear.trim());
  }

  /** Delete the generated master list for a year (FREP_700_GEN_MASTER.delete_list). */
  public MasterListAdminResponse deleteList(String effectiveYear) {
    try {
      masterListRepository.deleteList(effectiveYear.trim());
    } catch (StoredProcedureException ex) {
      // Proc backstop: the list has evaluated resources and can't be deleted. Clean 409, not a 500.
      if (StringUtils.containsIgnoreCase(ex.getOracleErrorMessage(), "resources associated")) {
        throw new ConflictFoundException(
            "This master list has evaluated resources, so it can't be deleted.");
      }
      throw ex;
    }
    return getMasterListCriteria(effectiveYear.trim());
  }

  /**
   * Field validation for a Generate-List request, ported from legacy {@code Frep700ValidationManager}:
   * <ul>
   *   <li>Min gross area (ha): required, 0–99999.9999, ≤ 4 decimal places.</li>
   *   <li>Min/Max harvest-complete dates: required, valid {@code yyyy-MM-dd}, within 1997-06-15…2050-12-31.</li>
   *   <li>Min harvest date must be before max harvest date.</li>
   *   <li>Max sites per district: required integer, 1–500.</li>
   *   <li>Comments ≤ 4000 chars.</li>
   * </ul>
   * Throws a {@code 400} listing all violations.
   */
  static void validateGenerateRequest(GenerateMasterListRequest request) {
    List<String> errors = new ArrayList<>();

    Double area = request.minOpeningGrossAreaHa();
    if (area == null) {
      errors.add("Min opening gross area (ha) is required.");
    } else if (area < 0 || area > MAX_GROSS_AREA_HA) {
      errors.add("Min opening gross area (ha) must be between 0 and 99999.9999.");
    } else if (BigDecimal.valueOf(area).stripTrailingZeros().scale() > MAX_GROSS_AREA_DECIMAL_PLACES) {
      errors.add("Min opening gross area (ha) must have at most 4 decimal places.");
    }

    LocalDate min = parseHarvestDate(request.minHarvestCompleteDate(), "Min harvest-complete date", errors);
    LocalDate max = parseHarvestDate(request.maxHarvestCompleteDate(), "Max harvest-complete date", errors);
    if (min != null && max != null && !min.isBefore(max)) {
      errors.add("Min harvest-complete date must be before max harvest-complete date.");
    }

    Integer sites = request.maxSitesPerDistrict();
    if (sites == null) {
      errors.add("Max sites per district is required.");
    } else if (sites < MIN_SITES_PER_DISTRICT || sites > MAX_SITES_PER_DISTRICT) {
      errors.add("Max sites per district must be between 1 and 500.");
    }

    validateComments(request.comments(), errors);
    throwIfInvalid(errors);
  }

  private static LocalDate parseHarvestDate(String value, String label, List<String> errors) {
    if (StringUtils.isBlank(value)) {
      errors.add(label + " is required.");
      return null;
    }
    LocalDate date;
    try {
      date = LocalDate.parse(value.trim());
    } catch (DateTimeParseException ex) {
      errors.add(label + " must be a valid date (YYYY-MM-DD).");
      return null;
    }
    if (date.isBefore(MIN_HARVEST_DATE) || date.isAfter(MAX_HARVEST_DATE)) {
      errors.add(label + " must be between 1997-06-15 and 2050-12-31.");
    }
    return date;
  }

  /**
   * Comments length, measured in UTF-8 <b>bytes</b> — the unit the column enforces.
   * {@code FREP_EVALUATION_YEAR.GENERATION_COMMENTS} is {@code VARCHAR2(4000 BYTE)} (nr-mof-db
   * {@code scripts/THE/TABLES/V2.01258__FREP_EVALUATION_YEAR.sql}), so a character count accepts
   * text the update rejects: a curly quote or em-dash costs 3 bytes, not 1. The UI counter measures
   * the same way, so the number an admin sees is the number that decides whether the save succeeds.
   */
  private static void validateComments(String comments, List<String> errors) {
    if (comments == null) {
      return;
    }
    int used = comments.getBytes(StandardCharsets.UTF_8).length;
    if (used > MAX_COMMENTS_LENGTH) {
      errors.add("Comments is too long — the limit is " + MAX_COMMENTS_LENGTH
          + " and this entry uses " + used + ".");
    }
  }

  private static void throwIfInvalid(List<String> errors) {
    if (!errors.isEmpty()) {
      var allErrors = String.join(" ", errors);
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message(allErrors).status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }
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
        row.totalSites(),
        row.resourceValueInd()
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
