package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Struct;
import java.sql.Types;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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

  // --- Protocol-type filter -------------------------------------------------------------------
  //
  // These run SearchRepositoryImpl.PROTOCOL_TYPE_PREDICATE itself — the same string spliced into the
  // production query — against an in-memory H2 table standing in for `frv`. There is no local Oracle,
  // so this covers the predicate's boolean logic, not the full query's Oracle-specific syntax.

  private static NamedParameterJdbcTemplate jdbc;

  @BeforeAll
  static void seedProtocolRows() {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:protocolfilter;DB_CLOSE_DELAY=-1");
    jdbc = new NamedParameterJdbcTemplate(ds);
    jdbc.getJdbcOperations().execute(
        "CREATE TABLE frv (frep_resource_value_type_code VARCHAR(3))");
    jdbc.getJdbcOperations().execute(
        "INSERT INTO frv VALUES ('SLB'), ('SLR'), ('CHR'), ('RIP')");
  }

  /** Codes surviving the production predicate for the given :protocolType, sorted. */
  private static List<String> matching(String protocolType) {
    return jdbc.queryForList(
        "SELECT frep_resource_value_type_code FROM frv WHERE "
            + SearchRepositoryImpl.PROTOCOL_TYPE_PREDICATE + " ORDER BY 1",
        new MapSqlParameterSource().addValue("protocolType", protocolType, Types.VARCHAR),
        String.class);
  }

  @Test
  void slrSearchAlsoReturnsHistoricalSlbRecords() {
    // The rename retired SLB without migrating data, so legacy rows stay SLB and must remain
    // findable under an SLR search. A merge silently reverted this once — hence the test.
    assertEquals(List.of("SLB", "SLR"), matching("SLR"));
  }

  @Test
  void slbSearchDoesNotWidenToSlr() {
    // The widening is deliberately one-way: asking for the legacy code must not pull in new records.
    assertEquals(List.of("SLB"), matching("SLB"));
  }

  @Test
  void otherProtocolsAreUnaffectedByTheSlbWidening() {
    assertEquals(List.of("CHR"), matching("CHR"));
  }

  @Test
  void nullProtocolTypeMatchesEveryProtocol() {
    assertEquals(List.of("CHR", "RIP", "SLB", "SLR"), matching(null));
  }
}
