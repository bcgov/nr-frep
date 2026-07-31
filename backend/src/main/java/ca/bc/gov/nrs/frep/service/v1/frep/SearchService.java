package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.struct.v1.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.ClientSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.SearchRepository;
import java.sql.SQLException;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * FREP400 checklist search and FREP410 client search backed by the legacy Oracle
 * schema via {@link SearchRepository}.
 */
@Service
public class SearchService {

  private final SearchRepository searchRepository;
  private final FamUserDirectoryService famUserDirectoryService;
  private final LoggedUserHelper loggedUserHelper;

  public SearchService(
      SearchRepository searchRepository,
      FamUserDirectoryService famUserDirectoryService,
      LoggedUserHelper loggedUserHelper) {
    this.searchRepository = searchRepository;
    this.famUserDirectoryService = famUserDirectoryService;
    this.loggedUserHelper = loggedUserHelper;
  }

  /** Default page size + cap, and the sortable result columns (public key -> result-set alias). */
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final String DEFAULT_SORT_COLUMN = "protocol_name";
  private static final Map<String, String> SORTABLE_COLUMNS = Map.ofEntries(
      Map.entry("protocolName", "protocol_name"),
      Map.entry("protocolCode", "protocol_code"),
      Map.entry("openingId", "opening_id"),
      Map.entry("effectiveYear", "effective_year"),
      Map.entry("orgUnitCode", "org_unit_code"),
      Map.entry("licenceId", "licence_id"),
      Map.entry("cuttingPermitId", "cutting_permit_id"),
      Map.entry("cutBlockId", "cut_block_id"),
      Map.entry("clientNumber", "client_number"),
      Map.entry("evaluationDate", "evaluation_date"),
      Map.entry("checklistStatusCode", "checklist_status_code"));

  /**
   * Server-side paginated checklist search. Unlike (the legacy VARRAY proc,
   * capped at 5000 and unable to page), this runs a native paginated query and returns the true total,
   * so every page — and counts beyond 5000 — are reachable. {@code sort} is {@code "field"} or
   * {@code "field,(asc|desc)"} where {@code field} is one of the whitelisted keys (unknown fields fall
   * back to the default order); the column is never taken raw from the client.
   */
  public PagedResponse<ChecklistSearchResult> searchChecklistsPaged(
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
      String evaluationDateTo,
      int pageNumber,
      int pageSize,
      String sort
  ) {
    int page = Math.max(pageNumber, 0);
    int size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

    ChecklistSearchCriteria criteria = buildCriteria(
        effectiveYear, orgUnit, protocolType, licenceId, cuttingPermitId, cutBlockId, openingId,
        clientNumber, checklistStatusCode, checklistId, evaluationDateFrom, evaluationDateTo);

    String[] sortField = parseSort(sort);
    String column = sortField[0];
    boolean descending = "desc".equals(sortField[1]);

    long total = searchRepository.countChecklists(criteria);
    int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);

    List<ChecklistSearchResult> content = searchRepository
        .searchChecklistsPage(criteria, page * size, size, column, descending)
        .stream()
        // Enrich the on-screen page with evaluator names (FAM); the CSV stream + legacy proc keep the
        // raw userid (no per-row FAM calls in the uncapped paths).
        .map(row -> toChecklistSearchResult(row, resolveEvaluatorName(row.evaluatorUserid())))
        .toList();

    return new PagedResponse<>(content, total, totalPages, page, size);
  }

  /** Resolves a {@code "field,dir"} sort string to a whitelisted column + direction. */
  static String[] parseSort(String sort) {
    if (StringUtils.isBlank(sort)) {
      return new String[] {DEFAULT_SORT_COLUMN, "asc"};
    }
    String[] parts = sort.split(",", 2);
    String column = SORTABLE_COLUMNS.getOrDefault(parts[0].trim(), DEFAULT_SORT_COLUMN);
    String dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? "desc" : "asc";
    return new String[] {column, dir};
  }

  /**
   * Streams the full (uncapped) checklist-search result set to {@code consumer}, mapping each row to a
   * {@link ChecklistSearchResult} — backs the CSV export, which must not be bounded by the on-screen page
   * size. Returns the number of rows streamed. Uses the default sort (the legacy CSV applied no user
   * sort); the repository enforces a max-rows safety cap.
   */
  public long streamChecklists(
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
      String evaluationDateTo,
      Consumer<ChecklistSearchResult> consumer
  ) {
    ChecklistSearchCriteria criteria = buildCriteria(
        effectiveYear, orgUnit, protocolType, licenceId, cuttingPermitId, cutBlockId, openingId,
        clientNumber, checklistStatusCode, checklistId, evaluationDateFrom, evaluationDateTo);
    return searchRepository.streamChecklists(
        criteria, DEFAULT_SORT_COLUMN, false,
        row -> consumer.accept(toChecklistSearchResult(row)));
  }

  /** Builds the native-query criteria (trim-to-null + protocol normalization) shared by paged + stream. */
  private ChecklistSearchCriteria buildCriteria(
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
    // Protocol/district visibility derived from the caller's roles (see ChecklistSearchCriteria):
    // sys-admins see all CHR; a district editor sees only their districts' CHR; Bio (and other
    // non-CHR) rows are shown only to Bio-capable users (sys-admin or FREP_EDITOR).
    boolean chrSeeAll = loggedUserHelper.isSysAdmin();
    List<String> allowedChrCodes = List.copyOf(loggedUserHelper.chrDistrictCodes());
    boolean nonChrVisible = loggedUserHelper.canEdit();

    return new ChecklistSearchCriteria(
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
        trimToNull(evaluationDateTo),
        chrSeeAll,
        allowedChrCodes,
        nonChrVisible);
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

  /** Falls back to the userid as the display name (the uncapped paths don't resolve FAM names). */
  static ChecklistSearchResult toChecklistSearchResult(ChecklistSearchRow row) {
    return toChecklistSearchResult(row, row.evaluatorUserid());
  }

  static ChecklistSearchResult toChecklistSearchResult(ChecklistSearchRow row, String evaluatorName) {
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
        evaluatorName,
        statusCode,
        statusCode
    );
  }

  /** Evaluator's FAM display name when they have FREP access, else the raw userid. */
  private String resolveEvaluatorName(String evaluatorUserid) {
    if (StringUtils.isBlank(evaluatorUserid)) {
      return evaluatorUserid;
    }
    return famUserDirectoryService.resolveName(evaluatorUserid).orElse(evaluatorUserid);
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
    // Only CHR and biodiversity (SLB legacy / SLR going forward) are in scope; the codes match the DB,
    // so this just normalises case/whitespace — no protocol aliasing.
    return Optional.of(protocolType.trim().toUpperCase());
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
