package ca.bc.gov.nrs.frep.repository.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Struct;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AcceptedSitesRepositoryTest {

  @Test
  void fromStructParsesLegacyObjectAttributes() throws Exception {
    Object[] attrs = {
        "9000", "8000", "1001", "1", "Biodiversity", "TAR", "SUB",
        "987654", "A12345", "1234567", "CP-8891", "CB-442", "2024-06-15",
        "100", "200", "300", "400", "1", "user1", "user2"
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    AcceptedSiteRow row = AcceptedSitesRepository.fromStruct(struct);

    assertEquals("1001", row.checklistId());
    assertEquals("Biodiversity", row.checklistType());
    assertEquals("1", row.sampleNumber());
    assertEquals("TAR", row.resourceValueStatCode());
    assertEquals("A12345", row.openingNumber());
    assertEquals("987654", row.openingId());
  }
}
