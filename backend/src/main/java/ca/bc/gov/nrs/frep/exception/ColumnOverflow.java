package ca.bc.gov.nrs.frep.exception;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.NestedExceptionUtils;

/**
 * Recognises Oracle's "value too large for column" failure (ORA-12899) anywhere in an exception
 * chain and turns it into a message the evaluator can act on.
 *
 * <p>Nothing between the textarea and the database enforces these column widths today, so an
 * over-long free-text field is only rejected on flush. Reported as a length problem — naming the
 * field and both numbers — the user knows exactly what to do; reported as the raw failure they get
 * "Unexpected system error".
 *
 * <p><b>Byte vs character.</b> The CHR text columns use byte semantics ({@code CHAR_USED = 'B'}),
 * so on a UTF-8 database an accented letter costs 2 bytes and a curly quote, em-dash or ellipsis
 * costs 3 — the characters Word and Outlook insert automatically. A comment under the character
 * count can therefore still overflow, which is why the message quotes the raw numbers and avoids
 * the word "characters".
 */
public final class ColumnOverflow {

  private ColumnOverflow() {}

  /**
   * ORA-12899 reads:
   * {@code ORA-12899: value too large for column "THE"."CHR_FEATURE_IDENTITY"."COMMENTS"
   * (actual: 1234, maximum: 500)}. The column group captures the whole quoted, schema-qualified
   * path; {@link #columnLabel} takes the last segment.
   */
  private static final Pattern ORA_12899 = Pattern.compile(
      "value too large for column\\s+(?<column>[^\\s(]+)\\s*"
          + "\\(actual:\\s*(?<actual>\\d+),\\s*maximum:\\s*(?<maximum>\\d+)\\)",
      Pattern.CASE_INSENSITIVE);

  /**
   * Column names that don't humanise well on their own — Oracle's 30-character identifier limit
   * left them abbreviated. Anything not listed falls back to sentence-casing the column name,
   * which is accurate for the plain ones ({@code COMMENTS}, {@code DAMAGE_DESCRIPTION}).
   */
  private static final Map<String, String> COLUMN_LABELS = Map.of(
      "LIMITING_OPERATNL_FACTORS_DESC", "Limiting operational factors",
      "EFFECTIVE_STRATS_USED_DESC", "Effective strategies used",
      "ALTERNATE_STRATS_AVAIL_DESC", "Alternate strategies available",
      "EVALUATION_RATING_RATIONALE", "Rating rationale",
      "OTHER_DESCRIPTION", "Other description",
      "OTHER_STRATEGY", "Other strategy",
      "LOCATION_DESCRIPTION", "Location description",
      "BLOCK_COMMENTS", "Block comments");

  /**
   * Builds the user-facing message when {@code throwable}'s chain contains an ORA-12899.
   *
   * <p>Only the derived field label and the two numbers are surfaced — never the raw Oracle text,
   * which would leak the schema and table name.
   *
   * @return the message, or empty when this is some other failure
   */
  public static Optional<String> describe(Throwable throwable) {
    Throwable cause = NestedExceptionUtils.getMostSpecificCause(throwable);
    // Walk the chain rather than trusting the most-specific cause alone: a commit-time flush
    // failure nests JPA -> Hibernate -> SQLException, and which link carries the ORA text varies.
    for (Throwable current = cause; current != null; current = current.getCause()) {
      Optional<String> message = fromMessage(current.getMessage());
      if (message.isPresent()) {
        return message;
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return fromMessage(throwable.getMessage());
  }

  private static Optional<String> fromMessage(String message) {
    if (message == null || message.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = ORA_12899.matcher(message);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String label = columnLabel(matcher.group("column"));
    String maximum = matcher.group("maximum");
    String actual = matcher.group("actual");
    // Deliberately not phrased as "characters": the limit is in bytes, so a 500-character comment
    // full of curly quotes really does exceed 500. Stating the raw numbers keeps the message true
    // without explaining Oracle's storage model.
    return Optional.of(
        label + " is too long — the limit is " + maximum + " and this entry uses " + actual
            + ". Shorten it and save again.");
  }

  /** Last segment of {@code "THE"."CHR_FEATURE_IDENTITY"."COMMENTS"}, mapped or sentence-cased. */
  private static String columnLabel(String qualifiedColumn) {
    String[] segments = qualifiedColumn.replace("\"", "").split("\\.");
    String column = segments[segments.length - 1].toUpperCase(Locale.ROOT);
    String mapped = COLUMN_LABELS.get(column);
    if (mapped != null) {
      return mapped;
    }
    String words = column.replace('_', ' ').toLowerCase(Locale.ROOT);
    return words.isEmpty()
        ? "That field"
        : Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }
}
