/**
 * The application's display name, used for the header, the browser tab title and the landing and
 * dashboard headings.
 *
 * A constant rather than configuration. It was a `VITE_APP_NAME` deployment variable, which meant
 * the name could differ per environment — and did: a repository variable set to "FREP" left TEST
 * showing "FREP" in the header while the dashboard heading, hardcoded separately, still read
 * "FREP IMS". One name in one place is what stops the two drifting apart again.
 */
export const APP_NAME = 'FREP IMS';

/**
 * The expanded name, shown bold in the header after the {@link APP_NAME} prefix ("FREP IMS Forest
 * and Range Evaluation Program Information System"), matching the FSPTS header treatment.
 *
 * Kept separate from {@link APP_NAME}, which is still what the browser tab, the landing heading and
 * the dashboard heading use — spelling the whole thing out in those places would be shouting.
 */
export const APP_FULL_NAME = 'Forest and Range Evaluation Program Information System';
