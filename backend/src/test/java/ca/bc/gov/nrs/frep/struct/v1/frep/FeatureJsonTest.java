package ca.bc.gov.nrs.frep.struct.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * The CHR Feature "Summary" description fields (Q4/Q5/Q6) are round-tripped as {@code q4Description}
 * / {@code q5Description} / {@code q6Description} — capital {@code D}, matching the frontend Feature
 * type. A lowercase key here silently drops the typed descriptions on save and hides existing ones
 * on read.
 */
class FeatureJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializesSummaryDescriptionsWithCapitalDKeys() throws Exception {
    Feature feature = new Feature();
    feature.setQ4Description("q4");
    feature.setQ5Description("q5");
    feature.setQ6Description("q6");

    String json = mapper.writeValueAsString(feature);

    assertTrue(json.contains("\"q4Description\":\"q4\""), json);
    assertTrue(json.contains("\"q5Description\":\"q5\""), json);
    assertTrue(json.contains("\"q6Description\":\"q6\""), json);
  }

  @Test
  void deserializesSummaryDescriptionsFromCapitalDKeys() throws Exception {
    String json = "{\"q4Description\":\"a\",\"q5Description\":\"b\",\"q6Description\":\"c\"}";

    Feature feature = mapper.readValue(json, Feature.class);

    assertEquals("a", feature.getQ4Description());
    assertEquals("b", feature.getQ5Description());
    assertEquals("c", feature.getQ6Description());
  }

  /**
   * Representative sample of the wider audit: damage-cause, planning-strategy, windthrow, location and
   * other Feature fields must round-trip under the exact camelCase keys the frontend uses. A mis-cased
   * key here lands in the {@code @JsonAnySetter} catch-all and is silently dropped on save.
   */
  @Test
  void roundTripsAuditedFieldsUnderFrontendCamelCaseKeys() throws Exception {
    String json =
        "{\"harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause\":\"Y\","
            + "\"retaininHarvestAreaFN\":\"Y\",\"windthrowTechniqueNone\":\"Y\","
            + "\"locationOther\":\"Y\",\"inReserve\":\"Y\",\"sitePermitIssued\":\"Y\","
            + "\"trailLength\":\"42\"}";

    Feature feature = mapper.readValue(json, Feature.class);

    // Deserialization bound each key to its typed field (not the catch-all).
    assertEquals("Y", feature.getHarvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause());
    assertEquals("Y", feature.getRetaininHarvestAreaFN());
    assertEquals("Y", feature.getWindthrowTechniqueNone());
    assertEquals("Y", feature.getLocationOther());
    assertEquals("Y", feature.getInReserve());
    assertEquals("Y", feature.getSitePermitIssued());
    assertEquals("42", feature.getTrailLength());

    // ...and serialization emits the same camelCase keys the frontend reads.
    String out = mapper.writeValueAsString(feature);
    assertTrue(out.contains("\"retaininHarvestAreaFN\":\"Y\""), out);
    assertTrue(out.contains("\"windthrowTechniqueNone\":\"Y\""), out);
    assertTrue(out.contains("\"trailLength\":\"42\""), out);
  }
}
