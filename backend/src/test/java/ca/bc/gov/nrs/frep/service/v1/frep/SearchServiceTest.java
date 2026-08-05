package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.SearchRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import ca.bc.gov.nrs.frep.struct.v1.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  @Mock
  private SearchRepository searchRepository;

  @Mock
  private FamUserDirectoryService famUserDirectoryService;

  @Mock
  private LoggedUserHelper loggedUserHelper;

  @InjectMocks
  private SearchService service;

  @BeforeEach
  void defaultVisibility() {
    // chrDistrictCodes() must be non-null (buildCriteria copies it); default no-CHR/no-Bio user.
    // Lenient because the pure-mapping tests never build criteria.
    lenient().when(loggedUserHelper.chrDistrictCodes()).thenReturn(Set.of());
  }

  @Test
  void normalizeProtocolTypeNormalisesCaseWithoutAliasing() {
    // The in-scope codes match the DB (SLB/SLR/CHR), so this only trims + upper-cases — no aliasing.
    assertEquals(Optional.of("SLB"), SearchService.normalizeProtocolType("slb"));
    assertEquals(Optional.of("SLR"), SearchService.normalizeProtocolType("slr"));
    assertEquals(Optional.of("CHR"), SearchService.normalizeProtocolType(" chr "));
    assertTrue(SearchService.normalizeProtocolType("").isEmpty());
  }

  @Test
  void toChecklistSearchResultMapsRowFields() {
    var result = SearchService.toChecklistSearchResult(new ChecklistSearchRow(
        "9001", "SLB", "Biodiversity", "2024", "DCK",
        "L1234", "CP-8891", "CB-442", "987654", "00010001",
        "2024-08-12", "IDIR\\JDOE", "RDY"
    ));

    assertEquals("9001", result.checklistId());
    assertEquals("SLB", result.protocolCode());
    assertEquals("RDY", result.checklistStatusCode());
    assertEquals("RDY", result.checklistStatus());
    // The static mapper (CSV / legacy paths) leaves the evaluator name as the raw userid.
    assertEquals("IDIR\\JDOE", result.evaluatorUserid());
    assertEquals("IDIR\\JDOE", result.evaluatorName());
  }

  @Test
  void searchChecklistsPagedResolvesEvaluatorNameViaFam() {
    when(searchRepository.countChecklists(any())).thenReturn(1L);
    when(searchRepository.searchChecklistsPage(any(), eq(0), eq(20), any(), eq(false)))
        .thenReturn(List.of(new ChecklistSearchRow(
            "9001", "SLB", "Biodiversity", "2024", "DCK", "L1", "CP", "BLK", "OP", "CL",
            "2024-05-01", "JDOE", "SUB")));
    when(famUserDirectoryService.resolveName("JDOE")).thenReturn(Optional.of("Jane Doe (JDOE)"));

    PagedResponse<ChecklistSearchResult> page = service.searchChecklistsPaged(
        null, null, null, null, null, null, null, null, null, null, null, null, 0, 20, "");

    assertEquals("Jane Doe (JDOE)", page.content().get(0).evaluatorName());
    assertEquals("JDOE", page.content().get(0).evaluatorUserid());
  }

  @Test
  void searchChecklistsPagedFallsBackToUseridWhenNotFrepUser() {
    when(searchRepository.countChecklists(any())).thenReturn(1L);
    when(searchRepository.searchChecklistsPage(any(), eq(0), eq(20), any(), eq(false)))
        .thenReturn(List.of(new ChecklistSearchRow(
            "9002", "SLB", "Biodiversity", "2024", "DCK", "L1", "CP", "BLK", "OP", "CL",
            "2024-05-01", "OLDUSER", "SUB")));
    when(famUserDirectoryService.resolveName("OLDUSER")).thenReturn(Optional.empty());

    PagedResponse<ChecklistSearchResult> page = service.searchChecklistsPaged(
        null, null, null, null, null, null, null, null, null, null, null, null, 0, 20, "");

    assertEquals("OLDUSER", page.content().get(0).evaluatorName());
  }

  @Test
  void searchChecklistsPagedThreadsChrDistrictVisibilityIntoCriteria() {
    // A CHR-district editor (no Bio, districts {DCK}) → criteria scopes CHR to DCK, hides Bio.
    when(loggedUserHelper.isSysAdmin()).thenReturn(false);
    when(loggedUserHelper.chrDistrictCodes()).thenReturn(Set.of("DCK"));
    when(loggedUserHelper.canEdit()).thenReturn(false);
    when(searchRepository.countChecklists(any())).thenReturn(0L);

    service.searchChecklistsPaged(
        null, null, null, null, null, null, null, null, null, null, null, null, 0, 20, "");

    ArgumentCaptor<ChecklistSearchCriteria> captor =
        ArgumentCaptor.forClass(ChecklistSearchCriteria.class);
    verify(searchRepository).countChecklists(captor.capture());
    ChecklistSearchCriteria c = captor.getValue();
    assertFalse(c.chrSeeAll());
    assertEquals(List.of("DCK"), c.allowedChrDistrictCodes());
    assertFalse(c.nonChrVisible());
  }

  @Test
  void searchChecklistsPagedThreadsAdminVisibilityIntoCriteria() {
    when(loggedUserHelper.isSysAdmin()).thenReturn(true);
    when(loggedUserHelper.canEdit()).thenReturn(true);
    when(searchRepository.countChecklists(any())).thenReturn(0L);

    service.searchChecklistsPaged(
        null, null, null, null, null, null, null, null, null, null, null, null, 0, 20, "");

    ArgumentCaptor<ChecklistSearchCriteria> captor =
        ArgumentCaptor.forClass(ChecklistSearchCriteria.class);
    verify(searchRepository).countChecklists(captor.capture());
    assertTrue(captor.getValue().chrSeeAll());
    assertTrue(captor.getValue().nonChrVisible());
  }

  @Test
  void toClientSearchResultMapsLocationRowFields() {
    var result = SearchService.toClientSearchResult(new ClientSearchRow(
        "10001", "TOLKO", "00010001", "TOLKO INDUSTRIES LTD.",
        "01", "VERNON OFFICE", "VERNON", "ACT"
    ));

    assertEquals("TOLKO", result.clientAcronym());
    assertEquals("00010001", result.clientNumber());
    assertEquals("01", result.clientLocnCode());
    assertEquals("TOLKO INDUSTRIES LTD.", result.clientName());
    assertEquals("VERNON OFFICE", result.clientLocnName());
    assertEquals("VERNON", result.city());
    assertEquals("ACT", result.clientStatus());
  }

  @Test
  void searchChecklistsPagedComputesPageMathAndOffset() {
    when(searchRepository.countChecklists(any())).thenReturn(53L);
    when(searchRepository.searchChecklistsPage(any(), eq(40), eq(20), eq("opening_id"), eq(true)))
        .thenReturn(List.of(new ChecklistSearchRow(
            "9001", "SLB", "Stand Level Biodiversity", "2024", "DCK", "L1234",
            "CP01", "BLK1", "1700001", "00010001", "2024-05-01", "IDIR\\JDOE", "RDY")));

    PagedResponse<ChecklistSearchResult> page = service.searchChecklistsPaged(
        null, null, null, null, null, null, null, null, null, null, null, null,
        2, 20, "openingId,desc");

    assertEquals(53L, page.totalElements());
    assertEquals(3, page.totalPages());     // ceil(53 / 20)
    assertEquals(2, page.pageNumber());
    assertEquals(20, page.pageSize());
    assertEquals(1, page.content().size());
    assertEquals("9001", page.content().get(0).checklistId());
    // offset = pageNumber * pageSize = 2 * 20 = 40, descending opening_id
    verify(searchRepository).searchChecklistsPage(any(), eq(40), eq(20), eq("opening_id"), eq(true));
  }

  @Test
  void searchChecklistsPagedClampsNegativePageAndCapsSize() {
    when(searchRepository.countChecklists(any())).thenReturn(0L);
    when(searchRepository.searchChecklistsPage(any(), eq(0), eq(100), any(), eq(false)))
        .thenReturn(List.of());

    PagedResponse<ChecklistSearchResult> page = service.searchChecklistsPaged(
        null, null, null, null, null, null, null, null, null, null, null, null,
        -5, 9999, "");

    assertEquals(0, page.pageNumber());     // negative page clamped to 0
    assertEquals(100, page.pageSize());     // size capped at MAX_PAGE_SIZE
    verify(searchRepository).searchChecklistsPage(any(), eq(0), eq(100), eq("protocol_name"), eq(false));
  }

  @Test
  void searchChecklistsPagedDefaultsBlankSizeToDefaultPageSize() {
    when(searchRepository.countChecklists(any())).thenReturn(0L);
    when(searchRepository.searchChecklistsPage(any(), eq(0), eq(20), any(), eq(false)))
        .thenReturn(List.of());

    PagedResponse<ChecklistSearchResult> page = service.searchChecklistsPaged(
        null, null, null, null, null, null, null, null, null, null, null, null,
        0, 0, "");

    assertEquals(20, page.pageSize());      // zero/blank size -> DEFAULT_PAGE_SIZE
  }

  @Test
  void parseSortDefaultsToProtocolNameAscWhenBlankOrUnknown() {
    assertArrayEquals(new String[] {"protocol_name", "asc"}, SearchService.parseSort(""));
    assertArrayEquals(new String[] {"protocol_name", "asc"}, SearchService.parseSort(null));
    // unknown field falls back to default column but keeps a valid direction
    assertArrayEquals(new String[] {"protocol_name", "desc"}, SearchService.parseSort("bogusField,desc"));
  }

  @Test
  void parseSortMapsWhitelistedFieldAndDirection() {
    assertArrayEquals(new String[] {"opening_id", "desc"}, SearchService.parseSort("openingId,desc"));
    assertArrayEquals(new String[] {"opening_id", "asc"}, SearchService.parseSort("openingId"));
    assertArrayEquals(new String[] {"evaluation_date", "asc"}, SearchService.parseSort("evaluationDate,whatever"));
  }

  @Test
  void streamChecklistsMapsRowsToResults() {
    when(searchRepository.streamChecklists(any(), eq("protocol_name"), eq(false), any()))
        .thenAnswer(invocation -> {
          Consumer<ChecklistSearchRow> consumer = invocation.getArgument(3);
          consumer.accept(new ChecklistSearchRow(
              "9001", "SLB", "Stand Level Biodiversity", "2024", "DCK", "L1234",
              "CP01", "BLK1", "1700001", "00010001", "2024-05-01", "IDIR\\JDOE", "RDY"));
          return 1L;
        });

    List<ChecklistSearchResult> collected = new ArrayList<>();
    long count = service.streamChecklists(
        null, null, null, null, null, null, null, null, null, null, null, null, collected::add);

    assertEquals(1L, count);
    assertEquals(1, collected.size());
    assertEquals("9001", collected.get(0).checklistId());
    assertEquals("Stand Level Biodiversity", collected.get(0).protocolName());
  }

  @Test
  void searchClientsBuildsCriteriaForRepository() {
    when(searchRepository.searchClients(any())).thenReturn(List.of(
        new ClientSearchRow("10001", "TOLKO", "00010001", "TOLKO INDUSTRIES LTD.",
            "01", "VERNON OFFICE", "VERNON", "ACT")
    ));

    var results = service.searchClients("000100", "TOLKO", "tolko", "John", "Q");

    assertEquals(1, results.size());
    ArgumentCaptor<ClientSearchCriteria> captor = ArgumentCaptor.forClass(ClientSearchCriteria.class);
    verify(searchRepository).searchClients(captor.capture());
    ClientSearchCriteria criteria = captor.getValue();
    assertEquals("000100", criteria.clientNumber());
    assertEquals("TOLKO", criteria.clientAcronym());
    assertEquals("tolko", criteria.clientName());
    assertEquals("John", criteria.legalFirstName());
    assertEquals("Q", criteria.legalMiddleName());
  }

  @Test
  void searchClientsTranslatesVarrayOverflowToBadRequest() {
    // Legacy proc raises ORA-20103 (varray index out of bounds) when > 500 rows match.
    SQLException oraError = new SQLException(
        "ORA-20103: frep.web.usr.database.record.varray.index.out.of.bounds:500", "72000", 20103);
    when(searchRepository.searchClients(any()))
        .thenThrow(new DataAccessResourceFailureException("call failed", oraError));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.searchClients(null, null, "a", null, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason() != null && ex.getReason().contains("narrow"));
  }

  @Test
  void searchClientsRethrowsOtherDataAccessErrors() {
    DataAccessResourceFailureException other = new DataAccessResourceFailureException(
        "connection closed", new SQLException("ORA-12345: something else", "72000", 12345));
    when(searchRepository.searchClients(any())).thenThrow(other);

    DataAccessResourceFailureException thrown = assertThrows(
        DataAccessResourceFailureException.class,
        () -> service.searchClients(null, null, "a", null, null));
    assertSame(other, thrown);
  }
}
