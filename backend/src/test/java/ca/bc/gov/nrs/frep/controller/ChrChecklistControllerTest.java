package ca.bc.gov.nrs.frep.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.dto.frep.CheckList;
import ca.bc.gov.nrs.frep.service.chr.ChrChecklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ChrChecklistControllerTest {

  @Mock
  private ChrChecklistService chrChecklistService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ChrChecklistController(chrChecklistService)).build();
  }

  @Test
  void getChecklistReturnsResource() throws Exception {
    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setStatus("ACT");
    when(chrChecklistService.getChecklist(1001L)).thenReturn(checklist);

    mockMvc.perform(get("/api/v1/chr/checklists/1001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checklistID").value("1001"))
        .andExpect(jsonPath("$.status").value("ACT"));
  }

  @Test
  void saveChecklistDelegatesToService() throws Exception {
    CheckList saved = new CheckList();
    saved.setChecklistID("1001");
    when(chrChecklistService.saveChecklist(org.mockito.ArgumentMatchers.any(CheckList.class)))
        .thenReturn(saved);

    mockMvc.perform(post("/api/v1/chr/checklists")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"checklistID\":\"1001\",\"status\":\"ACT\",\"revisionCount\":\"1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checklistID").value("1001"));

    verify(chrChecklistService).saveChecklist(org.mockito.ArgumentMatchers.any(CheckList.class));
  }

  @Test
  void submitChecklistUsesPathId() throws Exception {
    CheckList submitted = new CheckList();
    submitted.setChecklistID("1001");
    submitted.setStatus("SUB");
    when(chrChecklistService.submitChecklist(eq(1001L), org.mockito.ArgumentMatchers.any(CheckList.class)))
        .thenReturn(submitted);

    mockMvc.perform(post("/api/v1/chr/checklists/1001/submit")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"checklistID\":\"1001\",\"status\":\"ACT\",\"revisionCount\":\"1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUB"));
  }
}
