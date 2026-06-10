package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.AcceptedSiteResponse;
import ca.bc.gov.nrs.frep.repository.frep.AcceptedSiteRow;
import ca.bc.gov.nrs.frep.repository.frep.AcceptedSitesRepository;
import ca.bc.gov.nrs.frep.repository.frep.CodeListRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Accepted sites lookup backed by the legacy Oracle schema via
 * {@link AcceptedSitesRepository}.
 *
 * <p>Legacy equivalent: {@code FREP_200_ACCEPTED_SITES.GET}.
 */
@Service
@Profile("oracle")
public class AcceptedSiteService {

  private static final String TARGETED_STATUS_CODE = "TAR";
  private static final String CHR_CODE = "CHR";

  // Riparian (RIP) and Water (WTR) are out of scope for the migration — the accepted-sites list
  // surfaces only Biodiversity (SLB) and Cultural Heritage (CHR). The legacy FREP200 proc still
  // returns RIP/WTR rows, so we drop them here.
  private static final Set<String> OUT_OF_SCOPE_CODES = Set.of("RIP", "WTR");

  private final AcceptedSitesRepository acceptedSitesRepository;
  private final CodeListRepository codeListRepository;

  public AcceptedSiteService(
      AcceptedSitesRepository acceptedSitesRepository,
      CodeListRepository codeListRepository
  ) {
    this.acceptedSitesRepository = acceptedSitesRepository;
    this.codeListRepository = codeListRepository;
  }

  public List<AcceptedSiteResponse> findAcceptedSites(
      String effectiveYear,
      String orgUnit,
      String protocolType
  ) {
    Map<String, String> protocolNameToCode = loadProtocolNameToCode();

    // The FREP200 proc returns BIO/RIP/WTR only (it hard-excludes CHR). Cultural Heritage is
    // fetched via a supplementary query and merged in — a deliberate divergence from legacy,
    // which surfaces CHR only on its separate dashboard.
    List<AcceptedSiteRow> rows = new ArrayList<>(
        acceptedSitesRepository.findAcceptedSites(orgUnit, effectiveYear));
    if (includesCulturalHeritage(protocolType)) {
      rows.addAll(acceptedSitesRepository.findCulturalHeritageSites(orgUnit, effectiveYear));
    }

    return rows.stream()
        .map(row -> toResponse(row, effectiveYear, orgUnit, protocolNameToCode))
        .filter(site -> !isOutOfScope(site))
        .filter(site -> matchesProtocol(site, protocolType))
        .toList();
  }

  private static boolean isOutOfScope(AcceptedSiteResponse site) {
    return site.protocolCode() != null
        && OUT_OF_SCOPE_CODES.contains(site.protocolCode().toUpperCase(Locale.ROOT));
  }

  private static boolean includesCulturalHeritage(String protocolType) {
    return StringUtils.isBlank(protocolType) || CHR_CODE.equalsIgnoreCase(protocolType);
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
