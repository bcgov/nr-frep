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
