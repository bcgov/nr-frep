package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Struct;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SearchRepositoryImplTest {

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
}
