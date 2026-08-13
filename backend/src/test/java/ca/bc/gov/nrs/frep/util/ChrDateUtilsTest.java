package ca.bc.gov.nrs.frep.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;
import org.junit.jupiter.api.Test;

class ChrDateUtilsTest {

  @Test
  void isStrictDateAcceptsARealPaddedDate() {
    assertTrue(ChrDateUtils.isStrictDate("2026-08-13"));
    assertTrue(ChrDateUtils.isStrictDate("  2026-08-13  "));
    assertTrue(ChrDateUtils.isStrictDate("2024-02-29")); // leap year
  }

  @Test
  void isStrictDateTreatsBlankAsValid() {
    // Absent is not the same as malformed — callers decide whether a missing date is allowed.
    assertTrue(ChrDateUtils.isStrictDate(null));
    assertTrue(ChrDateUtils.isStrictDate(""));
    assertTrue(ChrDateUtils.isStrictDate("   "));
  }

  @Test
  void isStrictDateRejectsImpossibleCalendarDays() {
    // The reason this helper exists: getDate's lenient SimpleDateFormat rolls these forward instead
    // of failing, so the user's typo would be stored as a different, plausible-looking date.
    assertFalse(ChrDateUtils.isStrictDate("2026-02-31"));
    assertFalse(ChrDateUtils.isStrictDate("2026-13-01"));
    assertFalse(ChrDateUtils.isStrictDate("2025-02-29")); // 2025 is not a leap year
  }

  @Test
  void isStrictDateRejectsMalformedShapes() {
    // A non-lenient parse alone would accept the first two (it stops at the first bad character and
    // an unpadded month still parses); the shape check is what makes the YYYY-MM-DD contract hold.
    assertFalse(ChrDateUtils.isStrictDate("2026-08-13xyz"));
    assertFalse(ChrDateUtils.isStrictDate("2026-8-3"));
    assertFalse(ChrDateUtils.isStrictDate("13/08/2026"));
    assertFalse(ChrDateUtils.isStrictDate("not a date"));
  }

  @Test
  void getDateStillRollsOverLenientlyWhichIsWhatIsStrictDateGuardsAgainst() throws ParseException {
    // Documents the behaviour being guarded, so a future change to getDate that fixes or worsens it
    // shows up here rather than silently.
    assertEquals("2026-03-03", ChrDateUtils.formatDate(ChrDateUtils.getDate("2026-02-31")));
  }
}
