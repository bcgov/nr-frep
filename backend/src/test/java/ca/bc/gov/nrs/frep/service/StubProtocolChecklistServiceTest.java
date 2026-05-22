package ca.bc.gov.nrs.frep.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.dto.ProtocolChecklistResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StubProtocolChecklistServiceTest {

  private final StubProtocolChecklistService service = new StubProtocolChecklistService();

  @Test
  void returnsBiodiversityChecklistByIdRegardlessOfCase() {
    Optional<ProtocolChecklistResponse> bio = service.findChecklist("BIO", "9001");
    Optional<ProtocolChecklistResponse> bioLower = service.findChecklist("bio", "9001");

    assertTrue(bio.isPresent());
    assertTrue(bioLower.isPresent());
    assertEquals(3, bio.get().sections().size(),
        "Bio stub should expose FREP210, FREP211, FREP212 sections");
  }

  @Test
  void riparianHasSixSections() {
    Optional<ProtocolChecklistResponse> rip = service.findChecklist("RIP", "9003");

    assertTrue(rip.isPresent());
    assertEquals(6, rip.get().sections().size(),
        "Riparian stub should expose FREP230 through FREP235 sections");
  }

  @Test
  void returnsEmptyForUnknownProtocolOrId() {
    assertFalse(service.findChecklist("XXX", "9001").isPresent());
    assertFalse(service.findChecklist("BIO", "doesNotExist").isPresent());
    assertFalse(service.findChecklist(null, "9001").isPresent());
    assertFalse(service.findChecklist("BIO", null).isPresent());
  }
}
