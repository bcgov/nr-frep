package ca.bc.gov.nrs.frep.repository.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Struct;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SiteDetailRepositoryTest {

  @Test
  void fromResourceStructParsesLegacyObjectAttributes() throws Exception {
    Object[] attrs = {
        "8001", "R", "SLB", "Biodiversity", "ACC", "RDY", "Within stratum", "", "Field note"
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    SiteResourceRow row = SiteDetailRepository.fromResourceStruct(struct);

    assertEquals("8001", row.resourceValueId());
    assertEquals("SLB", row.resourceType());
    assertEquals("Biodiversity", row.resourceName());
    assertEquals("ACC", row.statusCode());
    assertEquals("RDY", row.checklistStatusCode());
    assertEquals("Within stratum", row.rationale());
    assertEquals("Field note", row.otherComments());
  }
}
