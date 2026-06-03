package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.frep.RandomListRepository;
import ca.bc.gov.nrs.frep.repository.frep.RandomListResult;
import ca.bc.gov.nrs.frep.repository.frep.RandomListRow;
import ca.bc.gov.nrs.frep.repository.frep.RandomListSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RandomListServiceTest {

  @Mock
  private RandomListRepository randomListRepository;

  @InjectMocks
  private RandomListService service;

  @Test
  void toResponseMapsLegacyRowFields() {
    var response = RandomListService.toResponse(new RandomListRow(
        "1001",
        "Y",
        "DCK",
        "987654",
        "A12345",
        "L1234",
        "CP-8891",
        "CB-442",
        "12.5",
        "2023-09-15",
        "2024-06-15",
        "MU1",
        "24.5",
        "22.1",
        List.of("SLB", "RIP")
    ));

    assertEquals("1001", response.frepSelectedSiteId());
    assertTrue(response.underReview());
    assertEquals("DCK", response.orgUnitCode());
    assertEquals("A12345", response.openingNumber());
    assertEquals("987654", response.openingId());
    assertEquals(12.5, response.exhibitArea());
    assertEquals(24.5, response.grossArea());
    assertEquals(22.1, response.netArea());
    assertEquals("2023-09-15", response.disturbanceStartDate());
    assertEquals("2024-06-15", response.disturbanceEndDate());
    assertEquals("MU1", response.managementUnit());
    assertEquals(List.of("SLB", "RIP"), response.existingChecklists());
  }

  @Test
  void toResponseTreatsBlankNumericAndDateFieldsAsNull() {
    var response = RandomListService.toResponse(new RandomListRow(
        "1002", "N", "DCK", "", "B67890", "", "", "", "", "", "", "", "", "", List.of()
    ));

    assertFalse(response.underReview());
    assertNull(response.exhibitArea());
    assertNull(response.grossArea());
    assertNull(response.netArea());
    assertNull(response.disturbanceStartDate());
    assertNull(response.disturbanceEndDate());
    assertNull(response.managementUnit());
    assertTrue(response.existingChecklists().isEmpty());
  }

  @Test
  void findRandomListDelegatesToRepositoryAndMapsSummary() {
    when(randomListRepository.findRandomList("2024", "56")).thenReturn(new RandomListResult(
        new RandomListSummary("Cariboo-Chilcotin", "3", "0", "1", "2"),
        List.of(new RandomListRow(
            "1001", "Y", "DCK", "987654", "A12345", "L1234", "CP-8891", "CB-442",
            "12.5", "2023-09-15", "2024-06-15", "MU1", "24.5", "22.1", List.of("SLB")
        ))
    ));

    var response = service.findRandomList("2024", "56");

    assertEquals(1, response.sites().size());
    assertEquals("A12345", response.sites().get(0).openingNumber());
    assertEquals("Cariboo-Chilcotin", response.summary().orgUnitDescription());
    assertEquals(3, response.summary().biodiversity());
    assertEquals(2, response.summary().culturalHeritage());
    assertEquals(0, response.summary().riparian());
    assertEquals(1, response.summary().water());
    verify(randomListRepository).findRandomList("2024", "56");
  }

  @Test
  void findRandomListPassesNullOrgUnitThrough() {
    when(randomListRepository.findRandomList("2024", null)).thenReturn(
        new RandomListResult(new RandomListSummary("", "", "", "", ""), List.of()));

    service.findRandomList("2024", null);

    verify(randomListRepository).findRandomList(eq("2024"), isNull());
  }
}
