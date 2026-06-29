package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.OpeningTargetRepository;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchCriteria;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchResult;
import ca.bc.gov.nrs.frep.struct.v1.frep.PagedResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.TargetedSiteValidationResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpeningTargetServiceTest {

  @Mock
  private OpeningTargetRepository openingTargetRepository;

  @InjectMocks
  private OpeningTargetService service;

  @Test
  void validateTargetedSite_noErrors_isValid() {
    when(openingTargetRepository.validateTargetedSite("123", "44")).thenReturn("");

    TargetedSiteValidationResponse result = service.validateTargetedSite("123", "44");

    assertTrue(result.valid());
    assertTrue(result.messages().isEmpty());
    assertEquals("123", result.openingId());
    assertEquals("44", result.orgUnit());
  }

  @Test
  void validateTargetedSite_mapsKnownCodesToFriendlyMessages() {
    when(openingTargetRepository.validateTargetedSite("123", "44"))
        .thenReturn("frep.web.error.usr.invalidOrg;frep.web.error.usr.invalidBlock;");

    TargetedSiteValidationResponse result = service.validateTargetedSite("123", "44");

    assertFalse(result.valid());
    assertEquals(2, result.messages().size());
    assertTrue(result.messages().get(0).contains("different district"));
    assertTrue(result.messages().get(1).contains("active harvest status"));
  }

  @Test
  void validateTargetedSite_unknownCodePassesThrough() {
    when(openingTargetRepository.validateTargetedSite("123", "44"))
        .thenReturn("some.unmapped.code;");

    TargetedSiteValidationResponse result = service.validateTargetedSite("123", "44");

    assertFalse(result.valid());
    assertEquals(List.of("some.unmapped.code"), result.messages());
  }

  @Test
  void searchOpenings_clampsPageSizeTo100AndComputesTotalPages() {
    when(openingTargetRepository.countOpenings(any())).thenReturn(250L);
    when(openingTargetRepository.searchOpenings(any(), eq(0), eq(100)))
        .thenReturn(List.of(
            new OpeningSearchResult("1", "92A.1", "A1", "01", "TM1", "1", "10",
                "FTML", "APP", "N", "LIC-1", "44")));

    // Over-large page size is capped to 100 (every call returns at most 100 rows).
    PagedResponse<OpeningSearchResult> page =
        service.searchOpenings(orgUnitCriteria("44"), 0, 500);

    assertEquals(100, page.pageSize());
    assertEquals(250L, page.totalElements());
    assertEquals(3, page.totalPages());
    assertEquals(0, page.pageNumber());
    assertEquals(1, page.content().size());
  }

  @Test
  void searchOpenings_zeroMatches_skipsThePageQuery() {
    when(openingTargetRepository.countOpenings(any())).thenReturn(0L);

    PagedResponse<OpeningSearchResult> page = service.searchOpenings(orgUnitCriteria("44"), 0, 100);

    assertTrue(page.content().isEmpty());
    assertEquals(0, page.totalPages());
    verify(openingTargetRepository, never()).searchOpenings(any(), anyInt(), anyInt());
  }

  private static OpeningSearchCriteria orgUnitCriteria(String orgUnit) {
    return new OpeningSearchCriteria(
        orgUnit, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null);
  }
}
