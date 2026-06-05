package ca.bc.gov.nrs.frep.util;

import org.apache.commons.lang3.StringUtils;

public final class ChrStringUtils {

  private ChrStringUtils() {}

  public static boolean hasAValue(String value) {
    return StringUtils.isNotBlank(value);
  }

  public static String booleanToIndictor(String boolValue) {
    if (!hasAValue(boolValue)) {
      return "N";
    }
    return "true".equalsIgnoreCase(boolValue) ? "Y" : "N";
  }

  public static String booleanToIndictorInverseLogic(String boolValue) {
    if (!hasAValue(boolValue)) {
      return "N";
    }
    return "true".equalsIgnoreCase(boolValue) ? "N" : "Y";
  }

  public static String indicatorToBooleanStr(String indicator) {
    return "Y".equalsIgnoreCase(indicator) ? "true" : "false";
  }

  public static String indicatorToBooleanStrInverseLogic(String indicator) {
    return "N".equalsIgnoreCase(indicator) ? "true" : "false";
  }
}
