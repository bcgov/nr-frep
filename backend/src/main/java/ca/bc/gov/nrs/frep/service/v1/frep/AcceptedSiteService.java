package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSiteResponse;
import ca.bc.gov.nrs.frep.repository.v1.bean.AcceptedSiteRow;
import ca.bc.gov.nrs.frep.repository.v1.AcceptedSitesRepository;
import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import ca.bc.gov.nrs.frep.struct.v1.frep.OrgUnitResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Accepted sites lookup backed by the legacy Oracle schema via
 * {@link AcceptedSitesRepository}.
 *
 * <p>Legacy equivalent: {@code FREP_200_ACCEPTED_SITES.GET}.
 */
@Service
public class AcceptedSiteService {

  private static final String TARGETED_STATUS_CODE = "TAR";

  private final AcceptedSitesRepository acceptedSitesRepository;
  private final CodeListRepository codeListRepository;
  private final ConfigurationService configurationService;
  private final LoggedUserHelper loggedUserHelper;

  public AcceptedSiteService(
      AcceptedSitesRepository acceptedSitesRepository,
      CodeListRepository codeListRepository,
      ConfigurationService configurationService,
      LoggedUserHelper loggedUserHelper
  ) {
    this.acceptedSitesRepository = acceptedSitesRepository;
    this.codeListRepository = codeListRepository;
    this.configurationService = configurationService;
    this.loggedUserHelper = loggedUserHelper;
  }

  public List<AcceptedSiteResponse> findAcceptedSites(
      String effectiveYear,
      String orgUnit,
      String protocolType
  ) {
    Map<String, String> protocolNameToCode = loadProtocolNameToCode();

    // Protocol/district visibility for this (single) district: CHR rows only when the caller may see
    // CHR for it; Biodiversity rows only for Bio-capable callers. Enforced server-side so a district
    // editor can't reach another district's CHR (or Bio) by calling the API directly.
    String districtCode = districtCodeForOrgUnitNo(orgUnit);
    boolean chrVisible = loggedUserHelper.canChr(districtCode);
    boolean nonChrVisible = loggedUserHelper.canEdit();

    // One native query returns only Biodiversity + Cultural Heritage (the legacy proc + the
    // supplementary CHR query, consolidated) — the query itself scopes to SLB/CHR.
    return acceptedSitesRepository.findAcceptedSites(orgUnit, effectiveYear).stream()
        .map(row -> toResponse(row, effectiveYear, orgUnit, protocolNameToCode))
        .filter(site -> matchesProtocol(site, protocolType))
        .filter(site -> "CHR".equalsIgnoreCase(site.protocolCode()) ? chrVisible : nonChrVisible)
        .toList();
  }

  /** Maps a numeric {@code org_unit_no} to its 3-letter district code via the cached org-unit list. */
  private String districtCodeForOrgUnitNo(String orgUnitNo) {
    String target = normalizeOrgUnitNo(orgUnitNo);
    if (target == null) {
      return null;
    }
    return configurationService.getOrgUnits().stream()
        .filter(org -> target.equals(normalizeOrgUnitNo(org.orgUnitNo())))
        .map(OrgUnitResponse::orgUnitCode)
        .findFirst()
        .orElse(null);
  }

  /** Trim and strip a trailing {@code .0} that Oracle JDBC can append to a NUMBER org-unit value. */
  private static String normalizeOrgUnitNo(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.endsWith(".0") ? trimmed.substring(0, trimmed.length() - 2) : trimmed;
  }

  static AcceptedSiteResponse toResponse(
      AcceptedSiteRow row,
      String effectiveYear,
      String orgUnitNo,
      Map<String, String> protocolNameToCode
  ) {
    String checklistType = row.checklistType();
    String protocolCode = protocolNameToCode.getOrDefault(checklistType, "");
    return new AcceptedSiteResponse(
        row.checklistId(),
        checklistType,
        row.sampleNumber(),
        TARGETED_STATUS_CODE.equalsIgnoreCase(row.resourceValueStatCode()),
        row.openingNumber(),
        row.openingId(),
        row.licenceId(),
        row.cuttingPermitId(),
        row.cutBlockId(),
        row.harvestCompleteDate(),
        row.checklistStatusCode(),
        row.checklistStatusCode(),
        protocolCode,
        checklistType,
        effectiveYear,
        orgUnitNo
    );
  }

  private Map<String, String> loadProtocolNameToCode() {
    Map<String, String> protocolNameToCode = new HashMap<>();
    for (var row : codeListRepository.getResourceValue()) {
      var protocol = ConfigurationService.toProtocolResponse(row);
      if (protocol.code() != null && !protocol.name().isBlank()) {
        protocolNameToCode.put(protocol.name(), protocol.code());
      }
    }
    return protocolNameToCode;
  }

  private static boolean matchesProtocol(AcceptedSiteResponse site, String protocolType) {
    if (StringUtils.isBlank(protocolType)) {
      return true;
    }
    return protocolType.equalsIgnoreCase(site.protocolCode());
  }
}
