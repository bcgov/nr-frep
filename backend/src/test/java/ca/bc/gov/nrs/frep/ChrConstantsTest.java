package ca.bc.gov.nrs.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.bc.gov.nrs.frep.ChrConstants.ChrFeatureDamageAgentCode;
import ca.bc.gov.nrs.frep.ChrConstants.ChrWindthrowTreatmentCode;
import org.junit.jupiter.api.Test;

/**
 * Pins the code values that reach a foreign key.
 *
 * <p>A wrong value here does not fail to compile and does not fail a unit test that only checks the
 * shape of a request — it fails at the database, as ORA-02291, in whatever environment happens to
 * run the save first. The windthrow treatments were spelled out as names ("NONE", "FEATHERING")
 * rather than the single letters the code table holds, so every ticked treatment blew up on
 * CHFWTX_CHWTC_FK.
 *
 * <p>Values verified against the code tables themselves, which is the only authority — legacy's
 * {@code Constants} is wrong for at least one of them ({@code INDUSTR} where the table holds
 * {@code INDUST}), so agreeing with legacy proves nothing on its own.
 */
class ChrConstantsTest {

  @Test
  void windthrowTreatmentsAreTheCodesNotTheNames() {
    assertEquals("N", ChrWindthrowTreatmentCode.NONE);
    assertEquals("B", ChrWindthrowTreatmentCode.BUFFER);
    assertEquals("P", ChrWindthrowTreatmentCode.PRUNING);
    assertEquals("F", ChrWindthrowTreatmentCode.FEATHERING);
    assertEquals("T", ChrWindthrowTreatmentCode.TOPPING);
    assertEquals("O", ChrWindthrowTreatmentCode.OTHER);
  }

  @Test
  void damageAgentsMatchTheCodeTable() {
    assertEquals("HARV", ChrFeatureDamageAgentCode.HARV);
    assertEquals("SAFETY", ChrFeatureDamageAgentCode.SAFETY);
    assertEquals("SIL", ChrFeatureDamageAgentCode.SIL);
    assertEquals("RECUSE", ChrFeatureDamageAgentCode.RECUSE);
    assertEquals("FIRE", ChrFeatureDamageAgentCode.FIRE);
    // The constant keeps legacy's name; only the value was wrong.
    assertEquals("INDUST", ChrFeatureDamageAgentCode.INDUSTR);
    assertEquals("ROADBD", ChrFeatureDamageAgentCode.ROADBD);
    assertEquals("LVS", ChrFeatureDamageAgentCode.LVS);
    assertEquals("WINDTHR", ChrFeatureDamageAgentCode.WINDTHR);
    assertEquals("OTH", ChrFeatureDamageAgentCode.OTH);
  }
}
