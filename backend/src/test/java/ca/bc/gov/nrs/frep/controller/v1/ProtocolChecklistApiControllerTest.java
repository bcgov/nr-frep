package ca.bc.gov.nrs.frep.controller.v1;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolChecklistField;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolChecklistSection;
import ca.bc.gov.nrs.frep.service.v1.frep.ProtocolChecklistService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProtocolChecklistApiControllerTest {

  @Mock
  private ProtocolChecklistService protocolChecklistService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .standaloneSetup(new ProtocolChecklistApiController(protocolChecklistService))
        .build();
  }

  @Test
  void returnsChecklistWhenFound() throws Exception {
    when(protocolChecklistService.findChecklist("bio", "9001"))
        .thenReturn(Optional.of(new ProtocolChecklistResponse(
            "9001", "SLB", "Biodiversity", "1001", "A12345", "2024",
            "RDY", "Ready", "IDIR\\JDOE", "John Doe (JDOE)", "2024-08-12",
            List.of(new ProtocolChecklistSection("opening", "Opening info",
                List.of(new ProtocolChecklistField("Stand age", "82", "NUMBER"))))
        )));

    mockMvc.perform(get("/api/v1/protocol-checklists/bio/9001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checklistId").value("9001"))
        .andExpect(jsonPath("$.protocolType").value("SLB"))
        .andExpect(jsonPath("$.sections[0].fields[0].label").value("Stand age"));
  }

  @Test
  void returnsNotFoundWhenUnknown() throws Exception {
    when(protocolChecklistService.findChecklist("bio", "9999"))
        .thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/protocol-checklists/bio/9999"))
        .andExpect(status().isNotFound());
  }
}
