package ca.bc.gov.nrs.frep.repository.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Struct;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MasterListRepositoryTest {

  @Test
  void fromGenerationStructParsesLegacyObjectAttributes() throws Exception {
    Object[] attrs = {
        "56", "DCK - Chilliwack Forest District", "3", "5", "38", "12", "N"
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    MasterListGenerationRow row = MasterListRepository.fromGenerationStruct(struct);

    assertEquals("DCK - Chilliwack Forest District", row.orgUnitDisplay());
    assertEquals(38, row.totalSites());
    assertEquals(12, row.totalAvailableSites());
    assertEquals("N", row.resourceValueInd());
  }
}
