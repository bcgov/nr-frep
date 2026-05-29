package ca.bc.gov.nrs.frep.service.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.frep.AcceptedSiteRow;
import ca.bc.gov.nrs.frep.repository.frep.AcceptedSitesRepository;
import ca.bc.gov.nrs.frep.repository.frep.CodeListRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcceptedSiteServiceTest {

  @Mock
  private AcceptedSitesRepository acceptedSitesRepository;

  @Mock
  private CodeListRepository codeListRepository;

  @InjectMocks
  private AcceptedSiteService service;

  @Test
  void toResponseMapsLegacyRowFields() {
    var row = new AcceptedSiteRow(
        "1001",
        "Biodiversity",
        "1",
        "ACC",
        "RDY",
        "A12345",
        "987654",
        "1234567",
        "CP-8891",
        "CB-442",
        "2024-06-15"
    );

    var mapped = AcceptedSiteService.toResponse(
        row,
        "2024",
        "56",
        Map.of("Biodiversity", "SLB")
    );

    assertEquals("1001", mapped.checklistId());
    assertEquals("Biodiversity", mapped.checklistType());
    assertFalse(mapped.targeted());
    assertEquals("SLB", mapped.protocolCode());
    assertEquals("2024", mapped.effectiveYear());
    assertEquals("56", mapped.orgUnitNo());
  }

  @Test
  void findAcceptedSitesFiltersByProtocolType() {
    when(acceptedSitesRepository.findAcceptedSites("56", "2024")).thenReturn(List.of(
        new AcceptedSiteRow(
            "1001", "Biodiversity", "", "ACC", "RDY",
            "A12345", "987654", "1234567", "CP-8891", "CB-442", "2024-06-15"
        ),
        new AcceptedSiteRow(
            "1002", "Riparian", "", "TAR", "SUB",
            "B67890", "987655", "2345678", "CP-8892", "CB-443", "2024-07-01"
        )
    ));
    Map<String, Object> slb = new LinkedHashMap<>();
    slb.put("CODE", "SLB");
    slb.put("DESCRIPTION", "Biodiversity");
    Map<String, Object> rip = new LinkedHashMap<>();
    rip.put("CODE", "RIP");
    rip.put("DESCRIPTION", "Riparian");
    when(codeListRepository.getResourceValue()).thenReturn(List.of(slb, rip));

    var sites = service.findAcceptedSites("2024", "56", "SLB");

    assertEquals(1, sites.size());
    assertEquals("SLB", sites.get(0).protocolCode());
    assertFalse(sites.get(0).targeted());
  }

  @Test
  void findAcceptedSitesMarksTargetedRows() {
    when(acceptedSitesRepository.findAcceptedSites("56", "2024")).thenReturn(List.of(
        new AcceptedSiteRow(
            "1002", "Riparian", "", "TAR", "SUB",
            "B67890", "987655", "2345678", "CP-8892", "CB-443", "2024-07-01"
        )
    ));
    when(codeListRepository.getResourceValue()).thenReturn(List.of());

    var sites = service.findAcceptedSites("2024", "56", null);

    assertEquals(1, sites.size());
    assertTrue(sites.get(0).targeted());
  }
}
