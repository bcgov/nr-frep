package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.dto.frep.ClientSearchResult;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.frep.ClientSearchRow;
import ca.bc.gov.nrs.frep.repository.frep.SearchRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * FREP400 checklist search and FREP410 client search backed by the legacy Oracle
 * schema via {@link SearchRepository}.
 */
@Service
@Profile("oracle")
public class SearchService {

  private final SearchRepository searchRepository;

  public SearchService(SearchRepository searchRepository) {
    this.searchRepository = searchRepository;
  }

  /**
   * Run a checklist search. Any blank parameter means "any".
   */
  public List<ChecklistSearchResult> searchChecklists(
      String effectiveYear,
      String orgUnit,
      String protocolType,
      String licenceId,
      String cuttingPermitId,
      String cutBlockId,
      String openingId,
      String clientNumber,
      String checklistStatusCode
  ) {
    return searchRepository.searchChecklists(new ChecklistSearchCriteria(
        trimToNull(effectiveYear),
        trimToNull(orgUnit),
        normalizeProtocolType(protocolType).orElse(null),
        trimToNull(licenceId),
        trimToNull(cuttingPermitId),
        trimToNull(cutBlockId),
        trimToNull(openingId),
        trimToNull(clientNumber),
        trimToNull(checklistStatusCode)
    )).stream()
        .map(SearchService::toChecklistSearchResult)
        .toList();
  }

  /**
   * Run a client search using legacy {@code FREP_410_CLIENT_SEARCH}.
   */
  public List<ClientSearchResult> searchClients(String clientNumber, String clientName) {
    return aggregateClientRows(searchRepository.searchClients(
        trimToNull(clientNumber),
        trimToNull(clientName)
    ));
  }

  static ChecklistSearchResult toChecklistSearchResult(ChecklistSearchRow row) {
    String statusCode = row.checklistStatusCode();
    return new ChecklistSearchResult(
        row.checklistId(),
        row.protocolCode(),
        row.protocolName(),
        row.effectiveYear(),
        row.orgUnitCode(),
        row.licenceId(),
        row.cuttingPermitId(),
        row.cutBlockId(),
        row.openingId(),
        row.clientNumber(),
        blankToNull(row.evaluationDate()),
        row.evaluatorUserid(),
        statusCode,
        statusCode
    );
  }

  static List<ClientSearchResult> aggregateClientRows(List<ClientSearchRow> rows) {
    Map<String, AggregatedClient> grouped = new LinkedHashMap<>();
    for (ClientSearchRow row : rows) {
      String key = preferredClientNumber(row);
      grouped.computeIfAbsent(key, ignored -> new AggregatedClient(row))
          .recordLocation(row.clientLocnCode());
    }
    return grouped.values().stream().map(AggregatedClient::toResult).toList();
  }

  static Optional<String> normalizeProtocolType(String protocolType) {
    if (StringUtils.isBlank(protocolType)) {
      return Optional.empty();
    }
    return switch (protocolType.trim().toUpperCase()) {
      case "BIO", "SLB" -> Optional.of("SLB");
      case "RIP" -> Optional.of("RIP");
      case "WAT", "WTR" -> Optional.of("WTR");
      case "CHR" -> Optional.of("CHR");
      default -> Optional.of(protocolType.trim().toUpperCase());
    };
  }

  private static String preferredClientNumber(ClientSearchRow row) {
    if (!row.displayClientNumber().isBlank()) {
      return row.displayClientNumber();
    }
    return row.clientNumber();
  }

  private static String trimToNull(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static final class AggregatedClient {
    private final ClientSearchRow firstRow;
    private int rowCount;
    private final List<String> locationCodes = new ArrayList<>();

    private AggregatedClient(ClientSearchRow firstRow) {
      this.firstRow = firstRow;
    }

    private void recordLocation(String locationCode) {
      rowCount++;
      if (locationCode != null && !locationCode.isBlank()) {
        locationCodes.add(locationCode.trim());
      }
    }

    private ClientSearchResult toResult() {
      int locationCount = locationCodes.isEmpty()
          ? rowCount
          : (int) locationCodes.stream().distinct().count();
      return new ClientSearchResult(
          preferredClientNumber(firstRow),
          firstRow.clientName(),
          firstRow.clientStatusCode(),
          locationCount
      );
    }
  }
}
