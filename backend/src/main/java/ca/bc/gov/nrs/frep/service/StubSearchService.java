package ca.bc.gov.nrs.frep.service;

import ca.bc.gov.nrs.frep.dto.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.dto.ClientSearchResult;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Hard-coded search results for local dev.
 *
 * <p>Replaced in Phase 0.3 by JDBC calls to {@code FREP_CHECKLIST_SEARCH.search}
 * and the Forest Client REST API.
 */
@Service
public class StubSearchService implements SearchService {

  private static final List<ChecklistSearchResult> CHECKLISTS = List.of(
      new ChecklistSearchResult("9001", "BIO", "Biodiversity", "2024", "DCK",
          "L1234", "CP-8891", "CB-442", "987654", "00010001",
          "2024-08-12", "IDIR\\JDOE", "RDY", "Ready for evaluation"),
      new ChecklistSearchResult("9002", "WAT", "Water Quality", "2024", "DCK",
          "L1234", "CP-8891", "CB-442", "987654", "00010001",
          "2024-09-04", "IDIR\\JDOE", "RDY", "Ready for evaluation"),
      new ChecklistSearchResult("9003", "RIP", "Riparian", "2024", "DCK",
          "L2345", "CP-8892", "CB-443", "987655", "00010002",
          "2024-07-22", "IDIR\\ASMITH", "SUB", "Submitted"),
      new ChecklistSearchResult("9100", "BIO", "Biodiversity", "2024", "DKA",
          "K1001", "CP-9001", "KB-100", "112233", "00010050",
          "2024-08-30", "IDIR\\BLEE", "RDY", "Ready for evaluation"),
      new ChecklistSearchResult("9101", "RIP", "Riparian", "2024", "DKA",
          "K1001", "CP-9001", "KB-100", "112233", "00010050",
          "2024-08-31", "IDIR\\BLEE", "RDY", "Ready for evaluation")
  );

  private static final List<ClientSearchResult> CLIENTS = List.of(
      new ClientSearchResult("00010001", "GORMAN BROS. LUMBER LTD.", "ACT", 4),
      new ClientSearchResult("00010002", "TOLKO INDUSTRIES LTD.", "ACT", 12),
      new ClientSearchResult("00010050", "INTERFOR CORPORATION", "ACT", 8),
      new ClientSearchResult("00099999", "WESTERN FOREST PRODUCTS INC.", "ACT", 22),
      new ClientSearchResult("00012345", "OLDCO LOGGING (HISTORICAL)", "DAC", 1)
  );

  @Override
  public List<ChecklistSearchResult> searchChecklists(
      String effectiveYear, String orgUnit, String protocolType, String licenceId,
      String cuttingPermitId, String cutBlockId, String openingId, String clientNumber,
      String checklistStatusCode
  ) {
    return CHECKLISTS.stream()
        .filter(c -> blankOrEquals(effectiveYear, c.effectiveYear()))
        .filter(c -> blankOrEqualsOrgUnit(orgUnit, c.orgUnitCode()))
        .filter(c -> blankOrEqualsIgnoreCase(protocolType, c.protocolCode()))
        .filter(c -> blankOrContainsIgnoreCase(licenceId, c.licenceId()))
        .filter(c -> blankOrContainsIgnoreCase(cuttingPermitId, c.cuttingPermitId()))
        .filter(c -> blankOrContainsIgnoreCase(cutBlockId, c.cutBlockId()))
        .filter(c -> blankOrContainsIgnoreCase(openingId, c.openingId()))
        .filter(c -> blankOrContainsIgnoreCase(clientNumber, c.clientNumber()))
        .filter(c -> blankOrEqualsIgnoreCase(checklistStatusCode, c.checklistStatusCode()))
        .toList();
  }

  @Override
  public List<ClientSearchResult> searchClients(String clientNumber, String clientName) {
    if (StringUtils.isAllBlank(clientNumber, clientName)) {
      return CLIENTS;
    }
    return CLIENTS.stream()
        .filter(c -> {
          boolean byNumber =
              StringUtils.isNotBlank(clientNumber) && c.clientNumber().contains(clientNumber.trim());
          boolean byName =
              StringUtils.isNotBlank(clientName)
                  && c.clientName().toLowerCase().contains(clientName.trim().toLowerCase());
          return byNumber || byName;
        })
        .toList();
  }

  private static boolean blankOrEquals(String filter, String value) {
    return StringUtils.isBlank(filter) || filter.trim().equals(value);
  }

  private static boolean blankOrEqualsIgnoreCase(String filter, String value) {
    return StringUtils.isBlank(filter) || filter.trim().equalsIgnoreCase(value);
  }

  private static boolean blankOrContainsIgnoreCase(String filter, String value) {
    if (StringUtils.isBlank(filter)) return true;
    if (value == null) return false;
    return value.toLowerCase().contains(filter.trim().toLowerCase());
  }

  private static boolean blankOrEqualsOrgUnit(String filter, String orgUnitCode) {
    if (StringUtils.isBlank(filter)) return true;
    return switch (filter) {
      case "56" -> "DCK".equals(orgUnitCode);
      case "58" -> "DKA".equals(orgUnitCode);
      case "61" -> "DNI".equals(orgUnitCode);
      case "63" -> "DPC".equals(orgUnitCode);
      default -> filter.equalsIgnoreCase(orgUnitCode);
    };
  }
}
