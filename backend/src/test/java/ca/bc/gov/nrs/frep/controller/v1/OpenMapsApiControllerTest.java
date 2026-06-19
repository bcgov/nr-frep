package ca.bc.gov.nrs.frep.controller.v1;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.frep.service.v1.OpenMapsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OpenMapsApiControllerTest {

  @Mock
  private OpenMapsService openMapsService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new OpenMapsApiController(openMapsService)).build();
  }

  @Test
  void returnsWfsFeatureCollection() throws Exception {
    var fc = new ObjectMapper().readTree("{\"type\":\"FeatureCollection\",\"features\":[{\"x\":1}]}");
    when(openMapsService.getOpeningPolygon(eq("987654"))).thenReturn(fc);

    mockMvc.perform(get("/api/v1/openings/987654/polygon"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("FeatureCollection"))
        .andExpect(jsonPath("$.features[0].x").value(1));
  }

  @Test
  void substitutesEmptyCollectionWhenWfsFails() throws Exception {
    when(openMapsService.getOpeningPolygon(eq("987654"))).thenReturn(null);

    mockMvc.perform(get("/api/v1/openings/987654/polygon"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("FeatureCollection"))
        .andExpect(jsonPath("$.features").isEmpty());
  }

  @Test
  void rejectsNonNumericOpeningId() throws Exception {
    mockMvc.perform(get("/api/v1/openings/abc/polygon"))
        .andExpect(status().isBadRequest());
  }
}
