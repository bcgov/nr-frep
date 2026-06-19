package ca.bc.gov.nrs.frep.controller.v1;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.dto.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.dto.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolResponse;

import java.util.List;

import ca.bc.gov.nrs.frep.service.frep.ConfigurationService;
import ca.bc.gov.nrs.frep.service.frep.FamUserDirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ConfigurationApiControllerTest {

  @Mock
  private ConfigurationService configurationService;

  @Mock
  private FamUserDirectoryService famUserDirectoryService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .standaloneSetup(new ConfigurationApiController(configurationService, famUserDirectoryService))
        .build();
  }

  @Test
  void returnsMasterListYears() throws Exception {
    when(configurationService.getMasterListYears()).thenReturn(List.of(
        new MasterListYearResponse("2024", "2024/2025", true),
        new MasterListYearResponse("2023", "2023/2024", false)
    ));

    mockMvc.perform(get("/api/v1/configuration/master-list-years"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].effectiveYear").value("2024"))
        .andExpect(jsonPath("$[0].label").value("2024/2025"))
        .andExpect(jsonPath("$[0].current").value(true));
  }

  @Test
  void returnsOrgUnits() throws Exception {
    when(configurationService.getOrgUnits()).thenReturn(List.of(
        new OrgUnitResponse("56", "DCK", "Chilliwack Forest District")
    ));

    mockMvc.perform(get("/api/v1/configuration/org-units"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].orgUnitNo").value("56"))
        .andExpect(jsonPath("$[0].orgUnitCode").value("DCK"))
        .andExpect(jsonPath("$[0].orgUnitName").value("Chilliwack Forest District"));
  }

  @Test
  void returnsProtocols() throws Exception {
    when(configurationService.getProtocols()).thenReturn(List.of(
        new ProtocolResponse("BIO", "Biodiversity")
    ));

    mockMvc.perform(get("/api/v1/configuration/protocols"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("BIO"))
        .andExpect(jsonPath("$[0].name").value("Biodiversity"));
  }
}
