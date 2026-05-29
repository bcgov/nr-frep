package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.dto.frep.SiteResourceResponse;
import ca.bc.gov.nrs.frep.repository.frep.SiteDetailData;
import ca.bc.gov.nrs.frep.repository.frep.SiteDetailRepository;
import ca.bc.gov.nrs.frep.repository.frep.SiteResourceRow;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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

  public SiteDetailService(SiteDetailRepository siteDetailRepository) {
    this.siteDetailRepository = siteDetailRepository;
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

  SiteDetailResponse toResponse(SiteDetailData data) {
    return new SiteDetailResponse(
        data.frepSelectedSiteId(),
        formatMasterListLabel(data.effectiveYear()),
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
        row.resourceType(),
        row.resourceName(),
        row.statusCode(),
        blankToNull(row.rejectionReasonCode()),
        blankToNull(row.rationale()),
        blankToNull(row.otherComments()),
        checklistId,
        blankToNull(row.checklistStatusCode())
    );
  }

  private String resolveChecklistId(SiteResourceRow row) {
    if (row.checklistStatusCode().isBlank()) {
      return "";
    }
    return siteDetailRepository.resolveChecklistId(row.resourceValueId(), row.resourceType());
  }

  static String formatMasterListLabel(String effectiveYear) {
    if (effectiveYear == null || effectiveYear.isBlank()) {
      return "";
    }
    return ConfigurationService.formatMasterListYearLabel(effectiveYear.trim());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
