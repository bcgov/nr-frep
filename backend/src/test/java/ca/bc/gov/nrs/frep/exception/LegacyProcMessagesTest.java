package ca.bc.gov.nrs.frep.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LegacyProcMessagesTest {

  @Nested
  @DisplayName("SLR stratum and plot keys")
  class SlrKeys {

    @Test
    @DisplayName("names the duplicate plot number and what to do about it")
    void resolvesDuplicatePlotNumber() {
      // frep_biodiversity_plot.validate: plot_number is unique within a stratum. Until this key was
      // catalogued the raw key reached the evaluator, who could not act on it.
      Optional<String> message =
          LegacyProcMessages.resolve("frep.web.usr.database.record.plot.number.already.exists;");

      assertThat(message).contains(
          "A plot with this number already exists in this stratum. "
              + "Give the plot a different number.");
    }

    @Test
    @DisplayName("states the Stratum Id rules as text, never as markup")
    void resolvesBadStratumFormatWithoutHtml() {
      // The legacy string was a line of &nbsp;/<br>; this is rendered as text, so markup would show.
      String message =
          LegacyProcMessages.resolve("frep.web.usr.database.record.badStratumFormat").orElseThrow();

      assertThat(message).doesNotContain("&nbsp;").doesNotContain("<br>");
      assertThat(message).contains("Stratum Id");
    }

    @Test
    @DisplayName("fills the field name into the generic required-field key")
    void resolvesRequiredFieldWithArgument() {
      assertThat(LegacyProcMessages.resolve("sil.error.usr.isrequired:Plot Number;"))
          .contains("Plot Number is required.");
    }

    @Test
    @DisplayName("resolves the clear-cut rule and the two record-modified variants")
    void resolvesRemainingSlrKeys() {
      assertThat(LegacyProcMessages.resolve(
          "frep.submit.biodiversity.stratum.clearcutWithTreesExistPlot"))
          .isPresent();
      assertThat(LegacyProcMessages.resolve("sil.web.usr.database.record.modified.no.params"))
          .contains("Someone else changed this data. Reload the checklist and try again.");
      assertThat(LegacyProcMessages.resolve("sil.web.error.usr.recordExists"))
          .contains("This record already exists.");
    }

    @Test
    @DisplayName("covers every key frep_biodiversity_plot and _stratum can emit")
    void coversEverySlrProcKey() {
      // The set grepped out of the package bodies in nr-mof-db. A proc emitting a key that is not
      // catalogued leaks the raw key to the evaluator — which is the defect this test exists for.
      String[] emitted = {
          "frep.error.usr.childexists",
          "frep.submit.biodiversity.stratum.clearcutWithTreesExistPlot",
          "frep.web.usr.database.record.badStratumFormat",
          "frep.web.usr.database.record.modified2",
          "frep.web.usr.database.record.plot.number.already.exists",
          "sil.error.usr.isrequired:Plot Number",
          "sil.web.error.usr.recordExists",
          "sil.web.usr.database.record.modified.no.params",
      };
      for (String key : emitted) {
        assertThat(LegacyProcMessages.resolve(key))
            .as("proc key %s must resolve to user-facing text", key)
            .isPresent();
      }
    }
  }

  @Nested
  @DisplayName("unknown keys")
  class UnknownKeys {

    @Test
    @DisplayName("resolves nothing when any segment is unrecognised")
    void allOrNothing() {
      // All-or-nothing by design: a partly resolved message would hide the unknown half.
      assertThat(LegacyProcMessages.resolve(
          "frep.web.usr.database.record.plot.number.already.exists;made.up.key;"))
          .isEmpty();
      assertThat(LegacyProcMessages.resolve("")).isEmpty();
    }
  }
}
