package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Struct;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SearchRepositoryImplTest {

  @Test
  void fromChecklistSearchStructParsesLegacyObjectAttributes() throws Exception {
    Object[] attrs = {
        "9001", "8001", "ACC", "SLB", "Biodiversity", "2024", "987654", "56", "DCK",
        "RDY", "L1234", "CB-442", "CP-8891", "00010001", null, null,
        "2024-08-12 00:00:00.0", "IDIR\\JDOE"
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    ChecklistSearchRow row = SearchRepositoryImpl.fromChecklistSearchStruct(struct);

    assertEquals("9001", row.checklistId());
    assertEquals("SLB", row.protocolCode());
    assertEquals("Biodiversity", row.protocolName());
    assertEquals("2024", row.effectiveYear());
    assertEquals("DCK", row.orgUnitCode());
    assertEquals("2024-08-12", row.evaluationDate());
  }

  @Test
  void fromClientSearchStructPrefersDisplayClientNumberFields() throws Exception {
    Object[] attrs = {
        "10001", "TOLKO", "00010001", "TOLKO INDUSTRIES LTD.", "", "", "01", "Head Office",
        "Kelowna", "ACT"
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    ClientSearchRow row = SearchRepositoryImpl.fromClientSearchStruct(struct);

    assertEquals("10001", row.clientNumber());
    assertEquals("TOLKO", row.clientAcronym());
    assertEquals("00010001", row.displayClientNumber());
    assertEquals("TOLKO INDUSTRIES LTD.", row.clientName());
    assertEquals("01", row.clientLocnCode());
    assertEquals("Head Office", row.clientLocnName());
    assertEquals("Kelowna", row.city());
    assertEquals("ACT", row.clientStatusCode());
  }

  @Test
  void formatEvaluationDateTrimsTimestampPortion() {
    assertEquals("2024-08-12", SearchRepositoryImpl.formatEvaluationDate("2024-08-12 00:00:00.0"));
    assertEquals("", SearchRepositoryImpl.formatEvaluationDate(""));
  }
}
