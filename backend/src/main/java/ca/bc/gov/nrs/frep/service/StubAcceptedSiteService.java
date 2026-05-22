package ca.bc.gov.nrs.frep.service;

import ca.bc.gov.nrs.frep.dto.AcceptedSiteResponse;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Hard-coded accepted sites for local development until Oracle {@code FREP_200_ACCEPTED_SITES}
 * is wired via {@code JdbcTemplate}.
 */
@Service
public class StubAcceptedSiteService implements AcceptedSiteService {

  private static final List<AcceptedSiteResponse> SAMPLE_SITES = List.of(
      site(
          "1001", "Bio", "1", false,
          "A12345", "987654", "1234567", "CP-8891", "CB-442",
          "2024-06-15", "RDY", "Ready for evaluation",
          "BIO", "Biodiversity", "2024", "56"
      ),
      site(
          "1002", "Riparian", "", true,
          "B67890", "987655", "2345678", "CP-8892", "CB-443",
          "2024-07-01", "SUB", "Submitted",
          "RIP", "Riparian", "2024", "56"
      ),
      site(
          "1003", "Water", "2", false,
          "C11223", "987656", "3456789", "CP-8893", "CB-444",
          "2024-05-20", "RDY", "Ready for evaluation",
          "WAT", "Water Quality", "2024", "56"
      ),
      site(
          "1004", "CHR", "", false,
          "D44556", "987657", "4567890", "CP-8894", "CB-445",
          "2024-08-10", "RDY", "Ready for evaluation",
          "CHR", "Culture Heritage", "2024", "56"
      ),
      site(
          "1005", "Bio", "", false,
          "E77889", "987658", "5678901", "CP-8895", "CB-446",
          "2023-09-30", "SUB", "Submitted",
          "BIO", "Biodiversity", "2023", "56"
      ),
      site(
          "1006", "Bio", "", true,
          "F99001", "987659", "6789012", "CP-8896", "CB-447",
          "2024-04-12", "RDY", "Ready for evaluation",
          "BIO", "Biodiversity", "2024", "58"
      )
  );

  @Override
  public List<AcceptedSiteResponse> findAcceptedSites(
      String effectiveYear,
      String orgUnit,
      String protocolType
  ) {
    return SAMPLE_SITES.stream()
        .filter(site -> effectiveYear.equals(site.effectiveYear()))
        .filter(site -> orgUnit.equals(site.orgUnitNo()))
        .filter(site -> matchesProtocol(site, protocolType))
        .toList();
  }

  private static boolean matchesProtocol(AcceptedSiteResponse site, String protocolType) {
    if (StringUtils.isBlank(protocolType)) {
      return true;
    }
    return protocolType.equalsIgnoreCase(site.protocolCode());
  }

  private static AcceptedSiteResponse site(
      String checklistId,
      String checklistType,
      String sampleNumber,
      boolean targeted,
      String openingNumber,
      String openingId,
      String licenceId,
      String cuttingPermitId,
      String cutBlockId,
      String harvestCompleteDate,
      String checklistStatusCode,
      String checklistStatus,
      String protocolCode,
      String protocolName,
      String effectiveYear,
      String orgUnitNo
  ) {
    return new AcceptedSiteResponse(
        checklistId,
        checklistType,
        sampleNumber,
        targeted,
        openingNumber,
        openingId,
        licenceId,
        cuttingPermitId,
        cutBlockId,
        harvestCompleteDate,
        checklistStatusCode,
        checklistStatus,
        protocolCode,
        protocolName,
        effectiveYear,
        orgUnitNo
    );
  }
}
