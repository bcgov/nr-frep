package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.frep.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.frep.ClientSearchRow;
import ca.bc.gov.nrs.frep.repository.frep.SearchRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
  void aggregateClientRowsGroupsByClientAndCountsLocations() {
    var results = SearchService.aggregateClientRows(List.of(
        new ClientSearchRow("10001", "00010001", "TOLKO INDUSTRIES LTD.", "01", "ACT"),
        new ClientSearchRow("10001", "00010001", "TOLKO INDUSTRIES LTD.", "02", "ACT"),
        new ClientSearchRow("10001", "00010001", "TOLKO INDUSTRIES LTD.", "01", "ACT")
    ));

    assertEquals(1, results.size());
    assertEquals("00010001", results.get(0).clientNumber());
    assertEquals(2, results.get(0).locationCount());
  }

  @Test
  void searchChecklistsBuildsCriteriaForRepository() {
    when(searchRepository.searchChecklists(any())).thenReturn(List.of());

    service.searchChecklists("2024", "56", "bio", "L1234", null, null, null, null, "RDY");

    ArgumentCaptor<ChecklistSearchCriteria> captor = ArgumentCaptor.forClass(ChecklistSearchCriteria.class);
    verify(searchRepository).searchChecklists(captor.capture());
    ChecklistSearchCriteria criteria = captor.getValue();
    assertEquals("2024", criteria.effectiveYear());
    assertEquals("56", criteria.orgUnitNo());
    assertEquals("SLB", criteria.protocolTypeCode());
    assertEquals("L1234", criteria.licenceId());
    assertEquals("RDY", criteria.checklistStatusCode());
  }

  @Test
  void searchClientsDelegatesToRepository() {
    when(searchRepository.searchClients("000100", "tolko")).thenReturn(List.of(
        new ClientSearchRow("10001", "00010001", "TOLKO INDUSTRIES LTD.", "01", "ACT")
    ));

    var results = service.searchClients("000100", "tolko");

    assertEquals(1, results.size());
    verify(searchRepository).searchClients("000100", "tolko");
  }
}
