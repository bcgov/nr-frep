package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.SiteDetailResponse;
import ca.bc.gov.nrs.frep.dto.frep.SiteResourceResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Hard-coded site detail data for local development.
 *
 * <p>Replaced in Phase 0.3 by a JDBC implementation calling
 * {@code FREP_110_SITE_DETAILS.get(...)}.
 */
@Service
public class StubSiteDetailService implements SiteDetailService {

  private static final Map<String, SiteDetailResponse> SAMPLE = Map.of(
      "1001", new SiteDetailResponse(
          "1001",
          "2024/2025",
          "DCK - Chilliwack Forest District",
          "00010001",
          "GORMAN BROS. LUMBER LTD.",
          "A12345",
          "987654",
          "987654",
          "L1234",
          "L1234",
          "CP-8891",
          "CB-442",
          "FSP-101",
          "2024",
          List.of(
              new SiteResourceResponse("BIO", "Biodiversity", "ACC", null,
                  "Site within target stratum", null, "9001", "RDY"),
              new SiteResourceResponse("RIP", "Riparian", "REJ", "NSTR",
                  "No streams in opening", "Field check confirms no S6+ streams.", null, null),
              new SiteResourceResponse("WAT", "Water Quality", "TAR", null,
                  "Targeted for quality monitoring", null, "9002", "RDY")
          )
      ),
      "1002", new SiteDetailResponse(
          "1002",
          "2024/2025",
          "DCK - Chilliwack Forest District",
          "00010002",
          "TOLKO INDUSTRIES LTD.",
          "B67890",
          "987655",
          "987655",
          "L2345",
          "L2345",
          "CP-8892",
          "CB-443",
          "FSP-102",
          "2024",
          List.of(
              new SiteResourceResponse("RIP", "Riparian", "ACC", null,
                  "Stream class S4 along block edge", null, "9003", "SUB")
          )
      ),
      "2001", new SiteDetailResponse(
          "2001",
          "2024/2025",
          "DKA - Kamloops Forest District",
          "00010050",
          "INTERFOR CORPORATION",
          "K10001",
          "112233",
          "112233",
          "K1001",
          "K1001",
          "CP-9001",
          "KB-100",
          "FSP-220",
          "2024",
          List.of(
              new SiteResourceResponse("BIO", "Biodiversity", "ACC", null,
                  "Within mixed-conifer stratum", null, "9100", "RDY"),
              new SiteResourceResponse("RIP", "Riparian", "ACC", null,
                  "S5/S6 streams adjacent", null, "9101", "RDY")
          )
      )
  );

  @Override
  public Optional<SiteDetailResponse> findSiteDetail(String frepSelectedSiteId) {
    return Optional.ofNullable(SAMPLE.get(frepSelectedSiteId));
  }
}
