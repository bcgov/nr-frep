package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.frep.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.frep.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.frep.ClientSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.frep.ClientSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.frep.SearchRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
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
