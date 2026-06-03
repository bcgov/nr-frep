package ca.bc.gov.nrs.frep.repository.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Array;
import java.sql.Struct;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RandomListRepositoryTest {

  @Test
  void fromStructParsesLegacyObjectAttributes() throws Exception {
    Struct checklist = checklistStruct("8001", "SLB");
    Array checklistArray = arrayOf(checklist);

    Object[] attrs = {
        "1001", "Y", "DCK", "987654", "A12345", "987654", "L1234", "L1234",
        "CP-8891", "CB-442", "12.5", "2023-09-15", "2024-06-15", "MU1",
        "24.5", "22.1", "100", "200", "300", "400", "1", "entry", "update",
        checklistArray
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    RandomListRow row = RandomListRepository.fromStruct(struct);

    assertEquals("1001", row.frepSelectedSiteId());
    assertEquals("Y", row.isReview());
    assertEquals("DCK", row.orgUnitCode());
    assertEquals("A12345", row.openingNumber());
    assertEquals("987654", row.openingId());
    assertEquals("12.5", row.exhibitArea());
    assertEquals("MU1", row.managementUnit());
    assertEquals("24.5", row.grossArea());
    assertEquals("22.1", row.netArea());
    assertEquals("2023-09-15", row.disturbanceStartDate());
    assertEquals("2024-06-15", row.disturbanceEndDate());
    assertEquals(List.of("SLB"), row.existingChecklistTypes());
  }

  @Test
  void fromStructStripsTrailingDecimalFromNumericAttributes() throws Exception {
    Object[] attrs = {
        "1001.0", "Y", "DCK", "987654.0", "A12345", "987654.0", "L1234", "L1234",
        "CP-8891", "CB-442", "12.5", "2023-09-15", "2024-06-15", "MU1",
        "24.5", "22.1", "100.0", "200.0", "300.0", "400.0", "1.0", "entry", "update",
        null
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    RandomListRow row = RandomListRepository.fromStruct(struct);

    assertEquals("1001", row.frepSelectedSiteId());
    assertEquals("987654", row.openingId());
    assertTrue(row.existingChecklistTypes().isEmpty());
  }

  private static Struct checklistStruct(String checklistId, String resourceValueType) throws Exception {
    Object[] attrs = { "7001", resourceValueType, "ACC", checklistId, "SUB", "Biodiversity" };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);
    return struct;
  }

  private static Array arrayOf(Struct element) throws Exception {
    Array array = Mockito.mock(Array.class);
    Mockito.when(array.getArray()).thenReturn(new Object[] { element });
    return array;
  }
}
