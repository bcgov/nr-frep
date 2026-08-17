import type { ClientSearchResult } from '@/types/search';

import API from '@/services/APIs';

/** Terms shorter than this match too much to be useful and hammer the proc. */
export const MIN_CLIENT_TERM_LENGTH = 3;

/** `FOREST_CLIENT.CLIENT_NUMBER` is a zero-padded 8-character string. */
const CLIENT_NUMBER_LENGTH = 8;

/**
 * Widths of the matching attributes on `THE.FREP_CLIENT_SEARCH_VW_OBJECT`, the object the criteria
 * are passed in.
 *
 * <p>These are hard limits, not preferences: the driver pickles the criteria into the object before
 * the call, so an over-long value fails at bind time with ORA-17072 ("Inserted value too large for
 * column") and never reaches the proc. `CLIENT_NAME` is `VARCHAR2(200)`, but the acronym and number
 * are only `VARCHAR2(8)` — so a term as ordinary as "lakeside pacific" used to 500 the acronym arm
 * on every keystroke. A term longer than the column cannot match a value stored in it anyway, so
 * the arm is skipped rather than truncated: truncating would invent matches the user never asked
 * for.
 */
const CLIENT_ACRONYM_LENGTH = 8;
const CLIENT_NAME_LENGTH = 200;

/**
 * Label for a client suggestion: `Name (ACRONYM) · 00012345`, absent parts dropped.
 *
 * <p>Built here rather than taken from the proc because `FREP_410_CLIENT_SEARCH` composes its
 * `client_name` as `name,middle,first` — middle before first, commas with no spaces, and no TRIM,
 * so a client with no legal names comes back with trailing commas. Nothing downstream wants that
 * shape, and the checklist filter only needs the number.
 */
export const clientLabel = (client: ClientSearchResult): string => {
  const parts: string[] = [];
  const name = client.clientName?.trim().replace(/,+$/, '');
  if (name) parts.push(name);
  const acronym = client.clientAcronym?.trim();
  if (acronym) parts.push(`(${acronym})`);
  const label = parts.join(' ');
  const number = client.clientNumber?.trim();
  if (!number) return label;
  return label ? `${label} · ${number}` : number;
};

/**
 * Suggestions for a free-text client term, for the lookup combo boxes.
 *
 * <p>An all-digit term is a client number — matched exactly by the proc, so it is zero-padded to the
 * stored width first (typing `66838` finds `00066838`). Anything else is searched as a name AND as
 * an acronym in parallel, because the user cannot be expected to know which they are typing; the two
 * result sets are merged and de-duplicated by client number.
 *
 * <p>Note the acronym term is passed bare. `FREP_410_CLIENT_SEARCH` appends the wildcard itself
 * (`LIKE UPPER(acronym || '%')`), unlike the equivalent SIL21 proc that nr-fspts calls, where the
 * caller supplies it — passing `TERM%` here would search for a literal `%`.
 *
 * <p>One failing arm must not sink the lookup, so each is caught independently.
 */
export const searchClientsAuto = async (term: string): Promise<ClientSearchResult[]> => {
  const trimmed = term.trim();
  if (trimmed.length < MIN_CLIENT_TERM_LENGTH) return [];

  const queries: Array<Record<string, string>> = [];
  if (/^\d+$/.test(trimmed)) {
    // Longer than the stored width is not a client number at all, so there is nothing to pad.
    if (trimmed.length <= CLIENT_NUMBER_LENGTH) {
      queries.push({ clientNumber: trimmed.padStart(CLIENT_NUMBER_LENGTH, '0') });
    }
  } else {
    if (trimmed.length <= CLIENT_NAME_LENGTH) queries.push({ clientName: trimmed });
    if (trimmed.length <= CLIENT_ACRONYM_LENGTH) queries.push({ clientAcronym: trimmed });
  }
  if (queries.length === 0) return [];

  const responses = await Promise.all(
    queries.map((query) => API.search.searchClients(query).catch(() => null)),
  );

  // A client with several locations returns one row per location; the combo box wants one entry per
  // client, and the caller only takes the number and the name.
  const seen = new Set<string>();
  const unique: ClientSearchResult[] = [];
  for (const rows of responses) {
    for (const row of rows ?? []) {
      const key = row.clientNumber?.trim() ?? '';
      if (key && !seen.has(key)) {
        seen.add(key);
        unique.push(row);
      }
    }
  }
  return unique;
};

/**
 * True when the client field holds text the user never resolved to an actual client.
 *
 * <p>The combo box only yields a client number when a suggestion is *picked*. Typing a term that
 * matches nothing — or editing the text after picking — leaves the number unset, so the search ran
 * with no client filter at all while the field still showed the term: searching "lakepaced"
 * returned every checklist in the system rather than none. Callers use this to refuse the search
 * and mark the field, instead of quietly answering a different question.
 */
export const isClientTermUnresolved = (
  term: string,
  selectedLabel: string,
  clientNumber?: string,
): boolean => {
  const trimmed = term.trim();
  if (!trimmed) return false;
  return !clientNumber || trimmed !== selectedLabel.trim();
};

/** Shown on the field when {@link isClientTermUnresolved} holds. */
export const CLIENT_UNRESOLVED_MESSAGE = 'Select a client from the list of suggestions.';
