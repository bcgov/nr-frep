/**
 * Friendly text for the biodiversity submit-validation codes returned by
 * {@code FREP_TOMBSTONE.FREP_SUBMISSION_VALIDATION} (legacy ApplicationResources
 * {@code frep.submit.biodiversity.*}).
 *
 * <p>The backend returns each failure as a raw `key:arg0,arg1,...` code (args in the same `{0}`,
 * `{1}` order the legacy MessageFormat used). These are deliberately submit-only completeness checks
 * — most are aggregate/cross-record (combined stratum size, "at least one plot with UTM", opening
 * completeness), so they can't be enforced on a single tab's save without blocking incremental
 * edits. Each message names the tab to fix, which we surface as the notification title.
 */

const CATALOG: Record<string, string> = {
  // Opening-info checks shared across protocols (legacy frep.submit.common.*). The evaluator (team
  // lead) + evaluation date moved from the retired Administration tab to Opening info.
  'frep.submit.common.teamlead': 'Opening info tab: an evaluator is required.',
  'frep.submit.common.evaluation': 'Opening info tab: an evaluation date is required.',
  'frep.submit.biodiversity.failure':
    'Stand Level Retention submission failed for an unknown reason. Please try again.',
  'frep.submit.biodiversity.stratum.mandatory':
    'Stratum summary tab: Add at least one stratum before submitting.',
  'frep.submit.biodiversity.stratum.fields.mandatory':
    'Stratum summary tab: Stratum {0} is incomplete — fill in its required fields.',
  'frep.submit.biodiversity.stratum.plotnumber':
    'Stratum summary tab: Stratum {0} has "# of plots in stratum" set to {1}, but {2} plot(s) ' +
    'exist on the Plots tab. Change the count, or add/remove plots so the two agree.',
  'frep.submit.biodiversity.stratum.size.invalid':
    "Stratum summary tab: The combined stratum size ({0} ha) exceeds the opening's gross area " +
    '(or the FREP gross-area override). Reduce the stratum sizes.',
  'frep.submit.biodiversity.stratum.greaterThanNAR':
    'Stratum summary tab: Stratum {0} — for stratum types CC, DO, DR, DT and DW, the mapped ' +
    'stratum size (ha) must not exceed the NAR (net area to be reforested).',
  'frep.submit.biodiversity.opening':
    'Opening info tab: Enter a Location description and save the tab.',
  'frep.submit.biodiversity.plot.invalidtrees':
    'Plots tab: Plot {1} in stratum {0} has a full-count area set and stand-table rows, but only ' +
    'plot {2} may record trees. Uncheck "Trees exist" on plot {1}, or clear its full-count area.',
  'frep.submit.biodiversity.plot.fullcountarea':
    'Plots tab: The full-count area for plot {0} in stratum {1} must be less than the stratum ' +
    'size ({2} ha).',
  // Raised when the stratum's harvest area is HNR and the plot has rows in
  // biodiversity_stand_detail (FREP_TOMBSTONE).
  //
  // The original wording named the on-screen label "Tick one of", which is meaningless on its own —
  // it is the label *inside* the "Harvest area" fieldset, so the user has nothing to search for. It
  // also said "stand-table entries", which is not what they clicked: they ticked "Trees exist" on
  // the plot and typed rows. Name the fieldset, the value it is set to, and both ways out.
  //
  // Context, deliberately NOT in the message: choosing stratum type "Clear cut" SETS harvest area
  // to HNR once (BioStratumView applyStratumType). It does not hold it there — applyHarvest never
  // re-forces it, the field is never disabled, and no rule blocks CC + HDR — so both remedies above
  // work as written, including on a clear cut. The only residual is that the default is silent, and
  // re-picking the stratum type resets it. Whether that warrants surfacing is an open question.
  'frep.submit.biodiversity.plot.noretention':
    'Plots tab: Plot {0} has stand-table rows, but stratum {1} has Harvest area set to "Harvest ' +
    'area with no retention". Either uncheck "Trees exist" on the plot to remove its stand-table ' +
    'rows, or change Harvest area on the Stratum summary tab.',
  'frep.submit.biodiversity.plot.notrees':
    'Plots tab: Plot {0} in stratum {1} has "Trees exist" checked but no stand-table rows. Add at ' +
    'least one row, or uncheck "Trees exist".',
  'frep.submit.biodiversity.plot.nocwd':
    'Plots tab: Plot {0} in stratum {1} has "CWD in transect" checked but no rows in the CWD ' +
    'table. Add at least one row, or uncheck "CWD in transect".',
  'frep.submit.biodiversity.plot.nobearingleg':
    'Plots tab: Plot {0} in stratum {1} needs both Bearing 1st Leg and Bearing 2nd Leg.',
  'frep.submit.biodiversity.plot.utmrequired':
    'Plots tab: At least one plot needs UTM coordinates — on a plot, uncheck "No UTM signal ' +
    'available" and enter Zone, Easting and Northing.',
  // Removed 2026-08-12 with the rule itself: FREP_BIODIVERSITY_STRATUM.VALIDATE no longer raises
  // `frep.submit.biodiversity.stratum.clearcutWithTreesExistPlot` (nr-mof-db 209db94c6, PR #574 —
  // "Trees exist" is now permitted on every stratum type, clear-cut included). If that migration
  // has not reached a given environment, the code still arrives there and falls through to the raw
  // -code fallback below rather than breaking — see the note on formatSubmitValidation.
};

export type SubmitValidationMessage = { title: string; detail: string };

/**
 * Turns a raw `key:arg0,arg1` submit-validation code into a friendly title + detail. Unknown codes
 * fall back to the raw code (so nothing is hidden from the user / support).
 */
export function formatSubmitValidation(code: string): SubmitValidationMessage {
  const separator = code.indexOf(':');
  const key = separator === -1 ? code : code.slice(0, separator);
  const argString = separator === -1 ? '' : code.slice(separator + 1);
  const args = argString === '' ? [] : argString.split(',');

  const template = CATALOG[key];
  if (!template) {
    return { title: 'Validation', detail: code };
  }

  const resolved = template.replace(/\{(\d+)\}/g, (_, i: string) => args[Number(i)] ?? '');
  const tabMatch = /^(.+? tab): (.+)$/s.exec(resolved);
  return tabMatch
    ? { title: tabMatch[1], detail: tabMatch[2] }
    : { title: 'Submit blocked', detail: resolved };
}
