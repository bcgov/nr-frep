package ca.bc.gov.nrs.frep.exception;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the legacy message <em>keys</em> that the FREP PL/SQL packages return in their
 * {@code p_error_message} OUT parameter into English the evaluator can act on.
 *
 * <p>The procs do not emit prose. They emit ApplicationResources bundle keys, optionally with
 * positional arguments, concatenated with {@code ;}:
 *
 * <pre>
 *   frep.evaluatorinfo.delete.evaluator:1,NAR1;frep.evaluatorinfo.delete.evaluator:2,RES1;
 * </pre>
 *
 * <p>The legacy Struts app resolved each key through {@code ApplicationResources.properties} +
 * {@code MessageFormat} before display; nothing in this app did, so the raw keys reached the UI.
 * The catalogue below carries the same messages (reworded for the current tab names), keyed the
 * same way, with {@code {0}}/{@code {1}} in the legacy argument order.
 *
 * <p>Resolution is all-or-nothing on purpose: {@link #resolve} returns a message only when every
 * segment is a known key, so an unrecognised proc error still reaches
 * {@link RestExceptionHandler} verbatim rather than being partly swallowed.
 */
public final class LegacyProcMessages {

  private LegacyProcMessages() {}

  private static final Map<String, String> CATALOG = Map.ofEntries(
      // frep_checklist_cost_resources.delete_bio_team_member — a plot's assessor_name still points
      // at the evaluator being replaced. One segment per matching plot.
      Map.entry("frep.evaluatorinfo.delete.evaluator",
          "Plots tab: plot {0} in stratum {1} is still assigned to this evaluator."),
      // …the water-quality equivalent, keyed on sample site rather than plot.
      Map.entry("frep.water.delete.evaluator",
          "Sample Site tab: sample site {0} is still assigned to this evaluator."),
      // Optimistic-lock conflict raised by the legacy table objects on a stale revision_count.
      Map.entry("frep.web.usr.database.record.modified",
          "Someone else changed this data in {0}. Reload the checklist and try again."),
      Map.entry("frep.web.usr.database.record.modified2",
          "Someone else changed this data. Reload the checklist and try again."),
      Map.entry("frep.error.usr.childexists",
          "This record can't be deleted while other records still reference it."),
      Map.entry("frep.web.error.delete.multirip",
          "This site can't be rejected until every other riparian checklist on it is deleted."),
      Map.entry("frep.data.error.attachment.illegalFile",
          "Files with a {0} extension can't be saved."),
      Map.entry("frep.submit.unsubmit",
          "Unsubmission failed, usually because of a data error. Contact the FREP help desk.")
  );

  /**
   * Turn a raw {@code p_error_message} into user-facing text.
   *
   * @return the resolved message, or empty when the input is blank or carries any key this
   *         catalogue does not know — the caller should then surface the raw string
   */
  public static Optional<String> resolve(String procErrorMessage) {
    if (procErrorMessage == null || procErrorMessage.isBlank()) {
      return Optional.empty();
    }
    // De-duplicated but insertion-ordered: the procs loop over rows, so the same key repeats with
    // different arguments, and occasionally with identical ones.
    Set<String> resolved = new LinkedHashSet<>();
    for (String segment : procErrorMessage.split(";")) {
      String trimmed = segment.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String message = resolveSegment(trimmed);
      if (message == null) {
        return Optional.empty();
      }
      resolved.add(message);
    }
    return resolved.isEmpty() ? Optional.empty() : Optional.of(String.join(" ", resolved));
  }

  /** Resolves one {@code key} or {@code key:arg0,arg1} segment; null when the key is unknown. */
  private static String resolveSegment(String segment) {
    int separator = segment.indexOf(':');
    String key = separator == -1 ? segment : segment.substring(0, separator);
    String template = CATALOG.get(key);
    if (template == null) {
      return null;
    }
    List<String> args = new ArrayList<>();
    if (separator != -1) {
      for (String arg : segment.substring(separator + 1).split(",")) {
        args.add(arg.trim());
      }
    }
    return format(template, args);
  }

  /**
   * Substitutes {@code {n}} placeholders positionally. Hand-rolled rather than
   * {@code MessageFormat} so that a stray apostrophe in a message (which MessageFormat treats as
   * a quoting character) can't silently swallow the placeholders.
   */
  private static String format(String template, List<String> args) {
    String result = template;
    for (int i = 0; i < args.size(); i++) {
      result = result.replace("{" + i + "}", args.get(i));
    }
    return result;
  }
}
