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
