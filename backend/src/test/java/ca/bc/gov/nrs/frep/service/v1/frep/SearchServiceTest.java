package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.SearchRepository;
import ca.bc.gov.nrs.frep.struct.v1.frep.ChecklistSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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

  @InjectMocks
  private SearchService service;

  @Test
  void normalizeProtocolTypeMapsLegacyAliases() {
    assertEquals(Optional.of("SLB"), SearchService.normalizeProtocolType("bio"));
    assertEquals(Optional.of("WTR"), SearchService.normalizeProtocolType("wat"));
    assertEquals(Optional.of("RIP"), SearchService.normalizeProtocolType("rip"));
    assertEquals(Optional.of("CHR"), SearchService.normalizeProtocolType("chr"));
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
  void searchChecklistsBuildsCriteriaForRepository() {
    when(searchRepository.searchChecklists(any())).thenReturn(List.of());

    service.searchChecklists(
        "2024", "56", "bio", "L1234", null, null, null, null, "RDY",
        "9001", "2024-01-01", "2024-12-31");

    ArgumentCaptor<ChecklistSearchCriteria> captor = ArgumentCaptor.forClass(ChecklistSearchCriteria.class);
    verify(searchRepository).searchChecklists(captor.capture());
    ChecklistSearchCriteria criteria = captor.getValue();
    assertEquals("2024", criteria.effectiveYear());
    assertEquals("56", criteria.orgUnitNo());
    assertEquals("SLB", criteria.protocolTypeCode());
    assertEquals("L1234", criteria.licenceId());
    assertEquals("RDY", criteria.checklistStatusCode());
    assertEquals("9001", criteria.checklistId());
    assertEquals("2024-01-01", criteria.evaluationDateFrom());
    assertEquals("2024-12-31", criteria.evaluationDateTo());
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
    when(searchRepository.searchChecklistsPage(any(), eq(0), eq(200), any(), eq(false)))
        .thenReturn(List.of());

    PagedResponse<ChecklistSearchResult> page = service.searchChecklistsPaged(
        null, null, null, null, null, null, null, null, null, null, null, null,
        -5, 9999, "");

    assertEquals(0, page.pageNumber());     // negative page clamped to 0
    assertEquals(200, page.pageSize());     // size capped at MAX_PAGE_SIZE
    verify(searchRepository).searchChecklistsPage(any(), eq(0), eq(200), eq("protocol_name"), eq(false));
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
