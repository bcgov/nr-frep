package ca.bc.gov.nrs.frep.repository.v1.bean;

/**
 * Common checklist header values returned by legacy GET procedures.
 */
public record ChecklistHeaderData(
    String frepSelectedSiteId,
    String openingNumber,
    String effectiveYear,
    String statusCode,
    String evaluatorUserid,
    String evaluationDate
) {

  public static ChecklistHeaderData empty() {
    return new ChecklistHeaderData("", "", "", "", "", "");
  }

  public ChecklistHeaderData mergedWith(ChecklistHeaderData other) {
    return new ChecklistHeaderData(
        firstNonBlank(frepSelectedSiteId, other.frepSelectedSiteId),
        firstNonBlank(openingNumber, other.openingNumber),
        firstNonBlank(effectiveYear, other.effectiveYear),
        firstNonBlank(statusCode, other.statusCode),
        firstNonBlank(evaluatorUserid, other.evaluatorUserid),
        firstNonBlank(evaluationDate, other.evaluationDate)
    );
  }

  private static String firstNonBlank(String left, String right) {
    return left != null && !left.isBlank() ? left : (right != null ? right : "");
  }
}
