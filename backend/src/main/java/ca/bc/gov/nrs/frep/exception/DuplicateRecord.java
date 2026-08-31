package ca.bc.gov.nrs.frep.exception;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.NestedExceptionUtils;

/**
 * Recognises Oracle's "unique constraint violated" failure (ORA-00001) anywhere in an exception
 * chain and turns it into a message the evaluator can act on.
 *
 * <p>Sibling of {@link ColumnOverflow}, and there for the same reason: the failure reaches the
 * client through the generic handlers, so without this a duplicate reads as "Unexpected system
 * error" — which tells the user nothing about the one field they need to change.
 *
 * <p>Only the derived message is returned, never the raw Oracle text, which names the schema,
 * table and index.
 */
public final class DuplicateRecord {

  private DuplicateRecord() {}

  /**
   * ORA-00001 reads: {@code ORA-00001: unique constraint (THE.CHFID_UK) violated}. The constraint
   * group captures the schema-qualified name; {@link #messageFor} takes the last segment.
   */
  private static final Pattern ORA_00001 = Pattern.compile(
      "unique constraint\\s*\\((?<constraint>[^)]+)\\)\\s*violated",
      Pattern.CASE_INSENSITIVE);

  /**
   * What each constraint means in the evaluator's terms, keyed by index name.
   *
   * <p>{@code CHFID_UK} is {@code UNIQUE (CHR_CHECKLIST_ID, FEATURE_LABEL)} — a feature label has
   * to be unique within its checklist, which is what the Features tab is editing when this fires.
   */
  private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
      "CHFID_UK",
      "A feature with this label already exists on this checklist. "
          + "Give the feature a different label.");

  /** The generic form, for a constraint this class has not been taught to name. */
  private static final String UNNAMED =
      "This record duplicates one that already exists. Change the value that has to be unique, "
          + "then save again.";

  /**
   * Builds the user-facing message when {@code throwable}'s chain contains an ORA-00001.
   *
   * @return the message, or empty when the chain holds no unique-constraint violation
   */
  public static Optional<String> describe(Throwable throwable) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(throwable);
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      Optional<String> found = messageFor(t.getMessage());
      if (found.isPresent()) {
        return found;
      }
      if (t == t.getCause()) {
        break;
      }
    }
    return messageFor(root == null ? null : root.getMessage());
  }

  private static Optional<String> messageFor(String message) {
    if (message == null) {
      return Optional.empty();
    }
    Matcher matcher = ORA_00001.matcher(message);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String constraint = matcher.group("constraint");
    String name = constraint.substring(constraint.lastIndexOf('.') + 1)
        .trim()
        .replace("\"", "")
        .toUpperCase(Locale.ROOT);
    return Optional.of(CONSTRAINT_MESSAGES.getOrDefault(name, UNNAMED));
  }
}
