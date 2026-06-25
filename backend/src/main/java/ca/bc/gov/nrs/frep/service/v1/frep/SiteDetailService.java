package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.exception.ConflictFoundException;
import ca.bc.gov.nrs.frep.exception.EntityNotFoundException;
import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceSaveRequest;
import ca.bc.gov.nrs.frep.repository.v1.bean.SiteDetailData;
import ca.bc.gov.nrs.frep.repository.v1.SiteDetailRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.SiteResourceRow;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Site Details (FREP110) lookup backed by the legacy Oracle schema via
 * {@link SiteDetailRepository}.
 *
 * <p>Legacy equivalent: {@code FREP_110_SITE_DETAILS.GET}.
 */
@Service
public class SiteDetailService {

  static final String SUBMITTED = "SUB";
  static final String OTHER_REASON = "OTH";
  static final int MAX_RATIONALE_LENGTH = 50;
  static final int MAX_COMMENTS_LENGTH = 2000;
  private static final String RATIONALE_TOO_LONG = "rationale must be 50 characters or fewer";

  private final SiteDetailRepository siteDetailRepository;
  private final LoggedUserHelper loggedUserHelper;

  public SiteDetailService(SiteDetailRepository siteDetailRepository, LoggedUserHelper loggedUserHelper) {
    this.siteDetailRepository = siteDetailRepository;
    this.loggedUserHelper = loggedUserHelper;
  }

  public Optional<SiteDetailResponse> findSiteDetail(String frepSelectedSiteId) {
    if (StringUtils.isBlank(frepSelectedSiteId)) {
      return Optional.empty();
    }

    SiteDetailData data = siteDetailRepository.findSiteDetail(frepSelectedSiteId.trim());
    if (data.frepSelectedSiteId().isBlank()) {
      return Optional.empty();
    }

    return Optional.of(toResponse(data));
  }

  /**
   * Save resource-value evaluations (accept/reject/target) for a site via
   * {@code FREP_110_SITE_DETAILS.SAVE}; accepting/targeting spawns the corresponding checklist. The
   * immutable site context (opening id, org unit no, effective year) is re-read server-side rather
   * than trusted from the client. Returns the refreshed site detail (new statuses + checklist ids).
   */
  public SiteDetailResponse saveResources(String frepSelectedSiteId, List<SiteResourceSaveRequest> resources) {
    String siteId = StringUtils.trimToEmpty(frepSelectedSiteId);
    SiteDetailData current = siteDetailRepository.findSiteDetail(siteId);
    if (current.frepSelectedSiteId().isBlank()) {
      throw new EntityNotFoundException(SiteDetailData.class, "FrepSelectedSiteId", frepSelectedSiteId);
    }
    if (allResourcesSubmitted(current)) {
      throw new ConflictFoundException(
          "All resource values for this site are submitted and can no longer be edited.");
    }
    validateResources(resources, current);
    siteDetailRepository.saveResources(
        siteId,
        current.openingId(),
        current.orgUnitNo(),
        effectiveYear(current.masterList()),
        resourcesToPersist(resources, current),
        loggedUserHelper.getLoggedUserId()
    );
    return toResponse(siteDetailRepository.findSiteDetail(siteId));
  }

  /**
   * Per-resource save rules ported from legacy {@code Frep110ValidationManager}:
   * <ul>
   *   <li><b>TAR</b> (targeted): rejection reason must be blank; rationale required, ≤ 50 chars.</li>
   *   <li><b>REJ</b> (rejected): rejection reason required; when reason is {@code OTH} the rationale
   *       is required; rationale ≤ 50 chars.</li>
   *   <li><b>ACC</b> (accepted): rejection reason and rationale must both be blank.</li>
   *   <li>Other comments ≤ 2000 chars (all statuses).</li>
   * </ul>
   * Rows with an <b>empty status</b> are allowed (not saved) and skipped. Rows whose checklist is
   * already submitted ({@code SUB}) are locked in the UI and skipped here (mirrors
   * {@code Frep110FieldManager.isEnabledField}). Throws {@code 400} listing all violations.
   */
  static void validateResources(List<SiteResourceSaveRequest> resources, SiteDetailData current) {
    if (resources == null || resources.isEmpty()) {
      return;
    }
    Set<String> submittedIds = submittedResourceIds(current);

    List<String> errors = new ArrayList<>();
    int index = 1;
    for (SiteResourceSaveRequest r : resources) {
      validateRow(r, index, submittedIds, errors);
      index++;
    }

    if (!errors.isEmpty()) {
      var allErrors = String.join(" ", errors);
      ApiError error = ApiError.builder().timestamp(LocalDateTime.now()).message(allErrors).status(BAD_REQUEST).build();
      throw new InvalidPayloadException(error);
    }
  }

  /** Validates one row, appending any violations to {@code errors}. Submitted/empty rows are skipped. */
  private static void validateRow(
      SiteResourceSaveRequest r, int index, Set<String> submittedIds, List<String> errors) {
    if (r.resourceValueId() != null && submittedIds.contains(r.resourceValueId())) {
      return; // submitted (locked) rows aren't editable
    }
    String status = StringUtils.trimToEmpty(r.statusCode()).toUpperCase();
    if (status.isEmpty()) {
      return; // empty status is allowed — the row simply isn't saved
    }
    String reason = StringUtils.trimToEmpty(r.rejectionReasonCode());
    String rationale = StringUtils.trimToEmpty(r.rationale());
    String comments = StringUtils.trimToEmpty(r.otherComments());

    switch (status) {
      case "TAR" -> validateTargeted(index, reason, rationale, errors);
      case "REJ" -> validateRejected(index, reason, rationale, errors);
      case "ACC" -> validateAccepted(index, reason, rationale, errors);
      default -> {
        // Unknown status — no field-level rules.
      }
    }
    if (comments.length() > MAX_COMMENTS_LENGTH) {
      errors.add(rowError(index, "other comments must be 2000 characters or fewer"));
    }
  }

  private static void validateTargeted(int index, String reason, String rationale, List<String> errors) {
    if (!reason.isEmpty()) {
      errors.add(rowError(index, "rejection reason must be blank for targeted resources"));
    }
    if (rationale.isEmpty()) {
      errors.add(rowError(index, "rationale is required for targeted resources"));
    } else if (rationale.length() > MAX_RATIONALE_LENGTH) {
      errors.add(rowError(index, RATIONALE_TOO_LONG));
    }
  }

  private static void validateRejected(int index, String reason, String rationale, List<String> errors) {
    if (reason.isEmpty()) {
      errors.add(rowError(index, "rejection reason is required for rejected resources"));
    } else if (OTHER_REASON.equalsIgnoreCase(reason)) {
      if (rationale.isEmpty()) {
        errors.add(rowError(index, "rationale is required when the rejection reason is Other"));
      } else if (rationale.length() > MAX_RATIONALE_LENGTH) {
        errors.add(rowError(index, RATIONALE_TOO_LONG));
      }
    } else if (rationale.length() > MAX_RATIONALE_LENGTH) {
      errors.add(rowError(index, RATIONALE_TOO_LONG));
    }
  }

  private static void validateAccepted(int index, String reason, String rationale, List<String> errors) {
    if (!reason.isEmpty()) {
      errors.add(rowError(index, "rejection reason must be blank for accepted resources"));
    }
    if (!rationale.isEmpty()) {
      errors.add(rowError(index, "rationale must be blank for accepted resources"));
    }
  }

  private static String rowError(int index, String message) {
    return "Resource " + index + ": " + message;
  }

  static Set<String> submittedResourceIds(SiteDetailData current) {
    return current.resources().stream()
        .filter(r -> SUBMITTED.equalsIgnoreCase(StringUtils.trimToEmpty(r.checklistStatusCode())))
        .map(SiteResourceRow::resourceValueId)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toSet());
  }

  /**
   * Keep only the rows worth persisting: those with a status selected ({@code ACC}/{@code REJ}/
   * {@code TAR}) that are not already submitted. Empty-status rows are allowed but not saved, and
   * sending a new resource with a null status would trip the proc's {@code statCodeRequired} error.
   */
  static List<SiteResourceSaveRequest> resourcesToPersist(
      List<SiteResourceSaveRequest> resources, SiteDetailData current) {
    if (resources == null || resources.isEmpty()) {
      return List.of();
    }
    Set<String> submittedIds = submittedResourceIds(current);
    return resources.stream()
        .filter(r -> StringUtils.isNotBlank(r.statusCode()))
        .filter(r -> r.resourceValueId() == null || !submittedIds.contains(r.resourceValueId()))
        .toList();
  }

  /**
   * True when the site has resources and every one already has a submitted checklist — the legacy
   * FREP110 Save button is disabled in this case ({@code Frep110ButtonManager.getEnableSave}).
   */
  static boolean allResourcesSubmitted(SiteDetailData current) {
    return !current.resources().isEmpty()
        && current.resources().stream()
            .allMatch(r -> SUBMITTED.equalsIgnoreCase(StringUtils.trimToEmpty(r.checklistStatusCode())));
  }

  /** The effective year is the first four characters of the master-list value (legacy parity). */
  static String effectiveYear(String masterList) {
    if (masterList == null) {
      return null;
    }
    String trimmed = masterList.trim();
    return trimmed.length() >= 4 ? trimmed.substring(0, 4) : trimmed;
  }

  SiteDetailResponse toResponse(SiteDetailData data) {
    return new SiteDetailResponse(
        data.frepSelectedSiteId(),
        formatMasterListLabel(data.masterList()),
        data.orgUnit(),
        data.client(),
        data.clientName(),
        data.opening(),
        data.openingId(),
        data.actualOpening(),
        data.licenceNo(),
        data.actualLicence(),
        data.cuttingPermitId(),
        data.cutBlockId(),
        data.fspLink(),
        data.harvestYear(),
        data.resources().stream().map(this::toResourceResponse).toList()
    );
  }

  SiteResourceResponse toResourceResponse(SiteResourceRow row) {
    String checklistId = blankToNull(resolveChecklistId(row));
    return new SiteResourceResponse(
        row.resourceValueId(),
        row.resourceType(),
        row.resourceName(),
        row.statusCode(),
        blankToNull(row.rejectionReasonCode()),
        blankToNull(row.rationale()),
        blankToNull(row.otherComments()),
        checklistId,
        blankToNull(row.checklistStatusCode()),
        blankToNull(row.revisionCount())
    );
  }

  private String resolveChecklistId(SiteResourceRow row) {
    if (row.checklistStatusCode().isBlank()) {
      return "";
    }
    return siteDetailRepository.resolveChecklistId(row.resourceValueId(), row.resourceType());
  }

  static String formatMasterListLabel(String masterList) {
    if (masterList == null || masterList.isBlank()) {
      return "";
    }
    return ConfigurationService.formatMasterListYearLabel(masterList.trim());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
