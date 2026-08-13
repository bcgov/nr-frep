package ca.bc.gov.nrs.frep.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;

public final class ChrDateUtils {

  private static final String DATE_PATTERN = "yyyy-MM-dd";
  private static final DateTimeFormatter SYSTEM_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private ChrDateUtils() {}

  public static String getSystemDateTime() {
    return LocalDateTime.now().format(SYSTEM_DATE_TIME);
  }

  public static Date getDate(String value) throws ParseException {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return new SimpleDateFormat(DATE_PATTERN).parse(value.trim());
  }

  /**
   * True when {@code value} is a real calendar date in {@code yyyy-MM-dd}. Strict on purpose:
   * {@link SimpleDateFormat} is lenient by default, so {@link #getDate} silently rolls
   * {@code 2026-02-31} forward to March 3 rather than rejecting it. Blank is treated as valid —
   * callers decide whether an absent date is allowed.
   */
  public static boolean isStrictDate(String value) {
    if (StringUtils.isBlank(value)) {
      return true;
    }
    // Shape first: a non-lenient parse still accepts trailing garbage ("2026-08-13xyz" stops at the
    // first bad character and succeeds) and an unpadded month, so the regex is what makes the
    // "YYYY-MM-DD" contract true. The strict parse then rejects impossible days (2026-02-31).
    if (!value.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
      return false;
    }
    SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
    format.setLenient(false);
    try {
      format.parse(value.trim());
      return true;
    } catch (ParseException ex) {
      return false;
    }
  }

  public static String formatDate(Date date) throws ParseException {
    if (date == null) {
      return null;
    }
    return new SimpleDateFormat(DATE_PATTERN).format(date);
  }

  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

  /** Format a timestamp as {@code yyyy-MM-dd HH:mm:ss} (matches the downloadedDate convention). */
  public static String formatDateTime(Date date) {
    if (date == null) {
      return null;
    }
    return new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
  }

  public static short getYear(String value) {
    if (StringUtils.isBlank(value)) {
      return 0;
    }
    return Short.parseShort(value.trim());
  }

  public static String formatYear(Date date) {
    if (date == null) {
      return null;
    }
    return new SimpleDateFormat("yyyy").format(date);
  }
}
