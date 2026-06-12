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
  'frep.submit.biodiversity.failure':
    'Biodiversity submission failed for an unknown reason. Please try again.',
  'frep.submit.biodiversity.stratum.mandatory':
    'Stratum summary tab: Add at least one stratum before submitting.',
  'frep.submit.biodiversity.stratum.fields.mandatory':
    'Stratum summary tab: Stratum {0} is incomplete — fill in its required fields.',
  'frep.submit.biodiversity.stratum.plotnumber':
    'Stratum summary tab: Stratum {0} needs {1} valid plot(s) when {2} are present.',
  'frep.submit.biodiversity.stratum.size.invalid':
    "Stratum summary tab: The combined stratum size ({0} ha) exceeds the opening's gross area " +
    '(or the FREP gross-area override). Reduce the stratum sizes.',
  'frep.submit.biodiversity.stratum.greaterThanNAR':
    'Stratum summary tab: Stratum {0} — for stratum type CC, DO, DR, DT or DW, the mapped stratum ' +
    'size (ha) must be less than or equal to the NAR.',
  'frep.submit.biodiversity.opening':
    'Opening tab: Enter a Location description and save the Opening tab.',
  'frep.submit.biodiversity.plot.invalidtrees':
    'Plots tab: Plot {1} in stratum {0} must have no stand-table entries when a full-count area is ' +
    'set. Only plot {2} may have trees.',
  'frep.submit.biodiversity.plot.fullcountarea':
    'Plots tab: The full-count area for plot {0} in stratum {1} must be less than the stratum ' +
    'size ({2} ha).',
  'frep.submit.biodiversity.plot.noretention':
    "Plots tab: Plot {0} in stratum {1} must have no stand-table entries, or change the stratum's " +
    "'Tick one of' question.",
  'frep.submit.biodiversity.plot.notrees':
    'Plots tab: Plot {0} in stratum {1} needs trees entered in the stand table, or change the ' +
    'indicator.',
  'frep.submit.biodiversity.plot.nocwd':
    'Plots tab: Plot {0} in stratum {1} needs coarse woody debris in the CWD table, or change the ' +
    'indicator.',
  'frep.submit.biodiversity.plot.nobearingleg':
    'Plots tab: Plot {0} in stratum {1} needs both Bearing 1st Leg and Bearing 2nd Leg.',
  'frep.submit.biodiversity.plot.utmrequired':
    'Plots tab: At least one plot needs UTM coordinates — on a plot, uncheck "No UTM signal ' +
    'available" and enter Zone, Easting and Northing.',
  'frep.submit.biodiversity.stratum.clearcutWithTreesExistPlot':
    "Stratum summary tab: Stratum type can't be Clear Cut while its plots have 'Trees exist' " +
    'checked.',
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
