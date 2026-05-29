package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.RandomListSiteResponse;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Hard-coded random list data for local development.
 *
 * <p>Replaced in Phase 0.3 by a JDBC implementation calling
 * {@code FREP_100_DIST_RAND_LIST.get(...)}.
 */
@Service
public class StubRandomListService implements RandomListService {

  private static final List<RandomListSiteResponse> SAMPLE = List.of(
      new RandomListSiteResponse(
          "1001", true, "DCK", "A12345", "987654", "L1234", "CP-8891", "CB-442",
          24.5, 22.1, "2023-09-15", "2024-06-15", List.of("BIO")
      ),
      new RandomListSiteResponse(
          "1002", true, "DCK", "B67890", "987655", "L2345", "CP-8892", "CB-443",
          18.2, 17.0, "2023-10-01", "2024-07-01", List.of("RIP")
      ),
      new RandomListSiteResponse(
          "1003", false, "DCK", "C11223", "987656", "L3456", "CP-8893", "CB-444",
          31.7, 28.9, "2023-08-20", "2024-05-20", List.of()
      ),
      new RandomListSiteResponse(
          "1004", true, "DCK", "D44556", "987657", "L4567", "CP-8894", "CB-445",
          12.3, 11.1, "2023-11-10", "2024-08-10", List.of("CHR")
      ),
      new RandomListSiteResponse(
          "2001", true, "DKA", "K10001", "112233", "K1001", "CP-9001", "KB-100",
          44.0, 39.5, "2023-04-12", "2024-04-12", List.of("BIO", "RIP")
      ),
      new RandomListSiteResponse(
          "2002", false, "DKA", "K10002", "112234", "K1002", "CP-9002", "KB-101",
          27.8, 25.2, "2023-05-20", "2024-05-20", List.of()
      )
  );

  @Override
  public List<RandomListSiteResponse> findRandomList(String effectiveYear, String orgUnit) {
    if (StringUtils.isBlank(orgUnit)) {
      return SAMPLE;
    }
    return SAMPLE.stream()
        .filter(site -> matchesOrgUnit(site.orgUnitCode(), orgUnit))
        .toList();
  }

  private static boolean matchesOrgUnit(String siteCode, String requestedOrgUnit) {
    // Stub mapping: 56 → DCK, 58 → DKA, 61 → DNI, 63 → DPC.
    return switch (requestedOrgUnit) {
      case "56" -> "DCK".equals(siteCode);
      case "58" -> "DKA".equals(siteCode);
      case "61" -> "DNI".equals(siteCode);
      case "63" -> "DPC".equals(siteCode);
      default -> siteCode.equalsIgnoreCase(requestedOrgUnit);
    };
  }
}
