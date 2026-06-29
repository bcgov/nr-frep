package ca.bc.gov.nrs.frep.repository.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.repository.v1.impl.OpeningTargetRepositoryImpl.BuiltSearch;
import ca.bc.gov.nrs.frep.struct.v1.frep.OpeningSearchCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Unit tests for the SIL56-ported opening-search SQL builder (no DB). */
class OpeningTargetRepositoryImplTest {

  @Test
  void primeLicenceFilterAndDistinctAlwaysPresent() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    BuiltSearch built = OpeningTargetRepositoryImpl.buildSearch(crit("44", null, null, null, null, null, null), params);

    assertTrue(built.innerSql().contains("opening_prime_licence_ind = 'Y'"));
    assertTrue(built.innerSql().contains("SELECT DISTINCT"));
    assertEquals("44", params.getValue("orgUnit"));
  }

  @Test
  void openingIdIgnoresEveryOtherFilter() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    BuiltSearch built =
        OpeningTargetRepositoryImpl.buildSearch(crit("44", "ABC", "12345", null, null, null, null), params);

    assertTrue(built.innerSql().contains("op.opening_id = to_number(:openingId)"));
    assertFalse(built.innerSql().contains("admin_district_no = to_number(:orgUnit)"));
    assertEquals("12345", params.getValue("openingId"));
    assertFalse(params.hasValue("forestFileId"));
  }

  @Test
  void licenceAndCutBlockUseWildcards() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    OpeningTargetRepositoryImpl.buildSearch(crit("44", "ab", null, "cd", null, null, null), params);

    assertEquals("AB%", params.getValue("forestFileId")); // prefix wildcard, upper-cased
    assertEquals("%CD%", params.getValue("cutBlockId")); // contains wildcard, upper-cased
  }

  @Test
  void blockStatusJoinsCutBlock() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    BuiltSearch built =
        OpeningTargetRepositoryImpl.buildSearch(crit("44", null, null, null, "HRV", null, null), params);

    assertTrue(built.innerSql().contains("cut_block cb"));
    assertTrue(built.innerSql().contains("cb.block_status_st = :blockStatus"));
    assertEquals("HRV", params.getValue("blockStatus"));
  }

  @Test
  void includeP87AddsUnion() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    BuiltSearch built = OpeningTargetRepositoryImpl.buildSearch(crit("44", null, null, null, null, null, "Y"), params);

    assertTrue(built.innerSql().contains(" UNION "));
    assertTrue(built.innerSql().contains("'P87'"));
  }

  @Test
  void sortBySelectsOrderColumn() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    assertEquals("forest_file_id",
        OpeningTargetRepositoryImpl.buildSearch(crit("44", null, null, null, null, "L", null), params).orderColumn());
    assertEquals("opening_number",
        OpeningTargetRepositoryImpl.buildSearch(crit("44", null, null, null, null, "O", null), params).orderColumn());
  }

  private static OpeningSearchCriteria crit(
      String orgUnit, String forestFileId, String openingId, String cutBlockId,
      String blockStatus, String sortBy, String p87) {
    return new OpeningSearchCriteria(
        orgUnit, null, null, null, null, null, null, forestFileId, openingId, null, null, null,
        cutBlockId, blockStatus, null, null, null, null, null, null, null, null, null, null, null,
        p87, sortBy);
  }
}
