package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.dto.frep.ClientSearchResult;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.frep.ClientSearchCriteria;
import ca.bc.gov.nrs.frep.repository.frep.ClientSearchRow;
import ca.bc.gov.nrs.frep.repository.frep.SearchRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
      String checklistStatusCode,
      String checklistId,
      String evaluationDateFrom,
      String evaluationDateTo
  ) {
    try {
      return searchRepository.searchChecklists(new ChecklistSearchCriteria(
          trimToNull(effectiveYear),
          trimToNull(orgUnit),
          normalizeProtocolType(protocolType).orElse(null),
          trimToNull(licenceId),
          trimToNull(cuttingPermitId),
          trimToNull(cutBlockId),
          trimToNull(openingId),
          trimToNull(clientNumber),
          trimToNull(checklistStatusCode),
          trimToNull(checklistId),
          trimToNull(evaluationDateFrom),
          trimToNull(evaluationDateTo)
      )).stream()
          .map(SearchService::toChecklistSearchResult)
          .toList();
    } catch (DataAccessException ex) {
      throw translateTooManyResults(ex);
    }
  }

  /**
   * Run a client search using legacy {@code FREP_410_CLIENT_SEARCH}. Returns one row per
   * client location, matching the legacy {@code frep410ClientSearch.jsp} results grid.
   */
  public List<ClientSearchResult> searchClients(
      String clientNumber,
      String clientAcronym,
      String clientName,
      String legalFirstName,
      String legalMiddleName
  ) {
    try {
      return searchRepository.searchClients(new ClientSearchCriteria(
          trimToNull(clientNumber),
          trimToNull(clientAcronym),
          trimToNull(clientName),
          trimToNull(legalFirstName),
          trimToNull(legalMiddleName)
      )).stream()
          .map(SearchService::toClientSearchResult)
          .toList();
    } catch (DataAccessException ex) {
      throw translateTooManyResults(ex);
    }
  }

  /**
   * The legacy search procs BULK COLLECT into a VARRAY(500); more than 500 matches overflows and is
   * re-raised as ORA-20103 ({@code ...record.varray.index.out.of.bounds:500}). Surface that as a
   * 400 telling the user to narrow their search, rather than a generic 500. Any other data-access
   * failure is rethrown unchanged.
   */
  private static RuntimeException translateTooManyResults(DataAccessException ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      boolean tooMany = (cause instanceof SQLException sqlEx && sqlEx.getErrorCode() == 20103)
          || (cause.getMessage() != null
              && cause.getMessage().contains("varray.index.out.of.bounds"));
      if (tooMany) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Your search matched more than 500 records. Please narrow your search criteria.");
      }
    }
    return ex;
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

  static ClientSearchResult toClientSearchResult(ClientSearchRow row) {
    String displayNumber = row.displayClientNumber().isBlank()
        ? row.clientNumber()
        : row.displayClientNumber();
    return new ClientSearchResult(
        row.clientAcronym(),
        displayNumber,
        row.clientLocnCode(),
        row.clientName(),
        row.clientLocnName(),
        row.city(),
        row.clientStatusCode()
    );
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

  private static String trimToNull(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
