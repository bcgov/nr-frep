package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.dto.frep.SiteResourceResponse;
import ca.bc.gov.nrs.frep.dto.frep.SiteResourceSaveRequest;
import ca.bc.gov.nrs.frep.repository.frep.SiteDetailData;
import ca.bc.gov.nrs.frep.repository.frep.SiteDetailRepository;
import ca.bc.gov.nrs.frep.repository.frep.SiteResourceRow;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Site Details (FREP110) lookup backed by the legacy Oracle schema via
 * {@link SiteDetailRepository}.
 *
 * <p>Legacy equivalent: {@code FREP_110_SITE_DETAILS.GET}.
 */
@Service
@Profile("oracle")
public class SiteDetailService {

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
    assertCanWrite();
    String siteId = StringUtils.trimToEmpty(frepSelectedSiteId);
    SiteDetailData current = siteDetailRepository.findSiteDetail(siteId);
    if (current.frepSelectedSiteId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found: " + frepSelectedSiteId);
    }
    siteDetailRepository.saveResources(
        siteId,
        current.openingId(),
        current.orgUnitNo(),
        effectiveYear(current.masterList()),
        resources == null ? List.of() : resources,
        loggedUserHelper.getLoggedUserId()
    );
    return toResponse(siteDetailRepository.findSiteDetail(siteId));
  }

  /** The effective year is the first four characters of the master-list value (legacy parity). */
  static String effectiveYear(String masterList) {
    if (masterList == null) {
      return null;
    }
    String trimmed = masterList.trim();
    return trimmed.length() >= 4 ? trimmed.substring(0, 4) : trimmed;
  }

  private void assertCanWrite() {
    if (!loggedUserHelper.canWrite()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to edit site details.");
    }
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
