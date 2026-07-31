package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.bean.AcceptedSiteRow;
import ca.bc.gov.nrs.frep.repository.v1.AcceptedSitesRepository;
import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import ca.bc.gov.nrs.frep.struct.v1.frep.OrgUnitResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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

  @Mock
  private ConfigurationService configurationService;

  @Mock
  private LoggedUserHelper loggedUserHelper;

  @InjectMocks
  private AcceptedSiteService service;

  @BeforeEach
  void permissiveVisibility() {
    // Default: caller may see everything (org "56" = district DCS). Lenient because the static
    // toResponse test drives no service method. Visibility filtering has its own test below.
    lenient().when(configurationService.getOrgUnits())
        .thenReturn(List.of(new OrgUnitResponse("56", "DCS", "Cascades")));
    lenient().when(loggedUserHelper.canChr(any())).thenReturn(true);
    lenient().when(loggedUserHelper.canEdit()).thenReturn(true);
  }

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
  void findAcceptedSitesMergesCulturalHeritage() {
    // BIO + CHR now arrive together from the single consolidated query.
    when(acceptedSitesRepository.findAcceptedSites("56", "2024")).thenReturn(List.of(
        new AcceptedSiteRow(
            "1001", "Biodiversity", "", "ACC", "RDY",
            "A12345", "987654", "1234567", "CP-8891", "CB-442", "2024-06-15"
        ),
        new AcceptedSiteRow(
            "9001", "Cultural Heritage", "", "ACC", "ACT",
            "C11111", "987656", "L9", "CP-9", "CB-9", "2024-08-01"
        )
    ));
    Map<String, Object> slb = new LinkedHashMap<>();
    slb.put("CODE", "SLB");
    slb.put("DESCRIPTION", "Biodiversity");
    Map<String, Object> chr = new LinkedHashMap<>();
    chr.put("CODE", "CHR");
    chr.put("DESCRIPTION", "Cultural Heritage");
    when(codeListRepository.getResourceValue()).thenReturn(List.of(slb, chr));

    var sites = service.findAcceptedSites("2024", "56", null);

    assertEquals(2, sites.size());
    assertTrue(sites.stream().anyMatch(s -> "CHR".equals(s.protocolCode())));
  }

  @Test
  void findAcceptedSitesHidesBioRowsFromChrOnlyUser() {
    // A CHR-only district editor (canEdit=false, canChr(DCS)=true) sees the CHR row, not the Bio row.
    when(loggedUserHelper.canEdit()).thenReturn(false);
    when(acceptedSitesRepository.findAcceptedSites("56", "2024")).thenReturn(List.of(
        new AcceptedSiteRow(
            "1001", "Biodiversity", "", "ACC", "RDY",
            "A12345", "987654", "1234567", "CP-8891", "CB-442", "2024-06-15"
        ),
        new AcceptedSiteRow(
            "9001", "Cultural Heritage", "", "ACC", "ACT",
            "C11111", "987656", "L9", "CP-9", "CB-9", "2024-08-01"
        )
    ));
    Map<String, Object> slb = new LinkedHashMap<>();
    slb.put("CODE", "SLB");
    slb.put("DESCRIPTION", "Biodiversity");
    Map<String, Object> chr = new LinkedHashMap<>();
    chr.put("CODE", "CHR");
    chr.put("DESCRIPTION", "Cultural Heritage");
    when(codeListRepository.getResourceValue()).thenReturn(List.of(slb, chr));

    var sites = service.findAcceptedSites("2024", "56", null);

    assertEquals(1, sites.size());
    assertEquals("CHR", sites.get(0).protocolCode());
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
