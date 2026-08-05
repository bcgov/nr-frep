package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.exception.ConflictFoundException;
import ca.bc.gov.nrs.frep.exception.EntityNotFoundException;
import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import ca.bc.gov.nrs.frep.struct.v1.frep.CreateTargetedSiteRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.SiteResourceSaveRequest;
import ca.bc.gov.nrs.frep.repository.v1.bean.SiteDetailData;
import ca.bc.gov.nrs.frep.repository.v1.SiteDetailRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.SiteResourceRow;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;

import java.nio.charset.StandardCharsets;
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
  /** Protocol resource types the new app supports: biodiversity (SLB legacy + SLR go-forward) and
   * Cultural Heritage. The legacy GET also returns Riparian/Water rows, which are out of migration
   * scope and are dropped. */
  static final Set<String> SUPPORTED_PROTOCOLS = Set.of("SLB", "SLR", "CHR");
  static final int MAX_RATIONALE_LENGTH = 50;
  static final int MAX_COMMENTS_LENGTH = 2000;
  /**
   * Free-text lengths here are measured in UTF-8 <b>bytes</b>, the unit the columns enforce:
   * {@code FREP_RESOURCE_VALUE.REJECTION_REASON} is {@code VARCHAR2(50 BYTE)} and
   * {@code ADDITIONAL_COMMENTS} is {@code VARCHAR2(2000 BYTE)} (nr-mof-db
   * {@code scripts/THE/TABLES/V2.01261__FREP_RESOURCE_VALUE.sql}). A character count accepts text
   * the insert rejects — a curly quote costs 3 bytes — and the UI counter measures the same way.
   */
  private static int length(String value) {
    return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
  }

  /** Row-scoped over-limit message, phrased without "characters" — the limit is bytes. */
  private static String tooLong(String label, String value, int max) {
    return label + " is too long — the limit is " + max + " and this entry uses "
        + length(value) + "";
  }

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

    return Optional.of(toResponse(withRetiredCodesHidden(data)));
  }

  /**
   * Load the site-detail context for a brand-new targeted opening (no selected site yet), so the
   * FREP200 "Add Target Site" flow can show the opening header and a blank row per protocol type to
   * evaluate. {@code effectiveYear} is the master-list year. Returns empty if {@code openingId} is blank.
   */
  public Optional<SiteDetailResponse> findSiteDetailForOpening(String openingId, String effectiveYear) {
    if (StringUtils.isBlank(openingId)) {
      return Optional.empty();
    }
    SiteDetailData data =
        siteDetailRepository.findSiteDetailByOpening(openingId.trim(), effectiveYear(effectiveYear));
    return Optional.of(toResponse(supportedProtocolsOnly(data)));
  }

  /**
   * Create a targeted site for an opening: validate the resource evaluations, then persist via
   * {@code FREP_110_SITE_DETAILS.SAVE} with a blank selected-site id (the proc creates the selected
   * site and spawns the checklists). The opening context is taken from the request — already vetted by
   * {@code ADD_TARGETED_SITE} — rather than re-read. Returns the newly created site detail.
   */
  public SiteDetailResponse createTargetedSite(CreateTargetedSiteRequest request) {
    String openingId = StringUtils.trimToEmpty(request.openingId());
    String year = effectiveYear(request.effectiveYear());
    SiteDetailData context =
        supportedProtocolsOnly(siteDetailRepository.findSiteDetailByOpening(openingId, year));
    validateResources(request.resources(), context);
    String newSiteId =
        siteDetailRepository.saveResources(
            context.frepSelectedSiteId(), // blank → create the selected site; existing id → update
            openingId,
            StringUtils.trimToEmpty(request.orgUnit()),
            year,
            resourcesToPersist(request.resources(), context),
            loggedUserHelper.getLoggedUserId());
    return toResponse(withRetiredCodesHidden(siteDetailRepository.findSiteDetail(newSiteId)));
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
    return toResponse(withRetiredCodesHidden(siteDetailRepository.findSiteDetail(siteId)));
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
    // Length is checked here, outside the per-status rules, because those are else-if chains: a
    // REJ row with no rejection reason short-circuits on "reason is required" and would never reach
    // a nested length check, letting an over-long rationale through unreported. ACC is excluded
    // because "rationale must be blank" already covers it and is the more useful message.
    if (("TAR".equals(status) || "REJ".equals(status))
        && length(rationale) > MAX_RATIONALE_LENGTH) {
      errors.add(rowError(index, tooLong("rationale", rationale, MAX_RATIONALE_LENGTH)));
    }
    if (length(comments) > MAX_COMMENTS_LENGTH) {
      errors.add(rowError(index, tooLong("other comments", comments, MAX_COMMENTS_LENGTH)));
    }
  }

  private static void validateTargeted(int index, String reason, String rationale, List<String> errors) {
    if (!reason.isEmpty()) {
      errors.add(rowError(index, "rejection reason must be blank for targeted resources"));
    }
    if (rationale.isEmpty()) {
      errors.add(rowError(index, "rationale is required for targeted resources"));
    }
  }

  private static void validateRejected(int index, String reason, String rationale, List<String> errors) {
    if (reason.isEmpty()) {
      errors.add(rowError(index, "rejection reason is required for rejected resources"));
    } else if (OTHER_REASON.equalsIgnoreCase(reason) && rationale.isEmpty()) {
      errors.add(rowError(index, "rationale is required when the rejection reason is Other"));
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
        .map(SiteDetailService::withGoForwardBioCode)
        .toList();
  }

  /**
   * Biodiversity is created under the go-forward code {@code SLR}; legacy {@code SLB} is view-only and
   * never re-persisted (all pre-cutover SLB is submitted, so it's filtered out upstream). Force any
   * new (id-less) biodiversity target to SLR so the backend is authoritative even for a stale client
   * that still offers SLB. Existing rows keep their stored type.
   */
  static SiteResourceSaveRequest withGoForwardBioCode(SiteResourceSaveRequest r) {
    if (StringUtils.isBlank(r.resourceValueId())
        && "SLB".equalsIgnoreCase(StringUtils.trimToEmpty(r.resourceType()))) {
      return new SiteResourceSaveRequest("", "SLR", r.statusCode(), r.rejectionReasonCode(),
          r.rationale(), r.otherComments(), r.revisionCount());
    }
    return r;
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

  /**
   * Drop resource rows for protocols the new app doesn't support (Riparian/Water), keeping only
   * biodiversity ({@code SLB} legacy + {@code SLR} go-forward) and Cultural Heritage ({@code CHR}).
   * Used in the targeted-site create flow so the picker only offers the protocols that have checklist
   * pages. Retired codes are dropped too — see {@link #withRetiredCodesHidden}.
   *
   * <p>Deliberately NOT applied to the Site Details page, which shows every protocol the site was
   * evaluated against (Riparian included) rather than only the ones this app can open.
   */
  SiteDetailData supportedProtocolsOnly(SiteDetailData data) {
    List<SiteResourceRow> kept = data.resources().stream()
        .filter(r -> SUPPORTED_PROTOCOLS.contains(StringUtils.trimToEmpty(r.resourceType()).toUpperCase()))
        .toList();
    return withRetiredCodesHidden(withResources(data, kept));
  }

  /**
   * Hide blank rows for codes that had already expired by the site's evaluation year. Applied on
   * every read path — the Site Details page included, where the protocol allowlist deliberately is
   * not.
   */
  SiteDetailData withRetiredCodesHidden(SiteDetailData data) {
    Set<String> retiredTypes = siteDetailRepository.retiredResourceTypes();
    if (retiredTypes.isEmpty()) {
      return data;
    }
    List<SiteResourceRow> kept = data.resources().stream()
        .filter(r -> isOffered(r, retiredTypes))
        .toList();
    return withResources(data, kept);
  }

  private static SiteDetailData withResources(SiteDetailData data, List<SiteResourceRow> resources) {
    return new SiteDetailData(
        data.frepSelectedSiteId(), data.masterList(), data.orgUnit(), data.orgUnitNo(),
        data.client(), data.clientName(), data.opening(), data.openingId(), data.actualOpening(),
        data.licenceNo(), data.actualLicence(), data.cuttingPermitId(), data.cutBlockId(),
        data.fspLink(), data.harvestYear(), resources);
  }

  /**
   * Is this row offered on the page?
   *
   * <p>{@code FREP_110_SITE_DETAILS.GET} builds its rows <em>from</em> the code table — the outer
   * join is on the data side — so it emits a blank row for every code that exists, and it never
   * reads {@code EXPIRY_DATE}. Expiring a code therefore does nothing on its own; without this
   * filter, retiring SLB in favour of SLR simply offered both biodiversity rows on every site.
   *
   * <p>A row is dropped only when <b>both</b> hold:
   * <ul>
   *   <li>the code is retired (past its expiry), and</li>
   *   <li>the row carries no resource-value id — nothing was ever evaluated against it. Because
   *       {@code FREP_RESOURCE_VALUE.FREP_RESOURCE_VALUE_STAT_CODE} is NOT NULL, an id-less row is
   *       exactly one with no status: a blank the proc seeded, not a record.</li>
   * </ul>
   *
   * <p>So a historical site still shows the Biodiversity resource it was actually evaluated
   * against, at any age, while no site offers a fresh SLB row to evaluate.
   */
  static boolean isOffered(SiteResourceRow r, Set<String> retiredTypes) {
    if (StringUtils.isNotBlank(r.resourceValueId())) {
      return true; // a real evaluation — never hidden, however old the code
    }
    return !retiredTypes.contains(StringUtils.trimToEmpty(r.resourceType()).toUpperCase());
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
