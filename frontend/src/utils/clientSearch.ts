import type { ClientSearchResult } from '@/types/search';

import API from '@/services/APIs';

/** Terms shorter than this match too much to be useful and hammer the proc. */
export const MIN_CLIENT_TERM_LENGTH = 3;

/** `FOREST_CLIENT.CLIENT_NUMBER` is a zero-padded 8-character string. */
const CLIENT_NUMBER_LENGTH = 8;

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

  const queries = /^\d+$/.test(trimmed)
    ? [{ clientNumber: trimmed.padStart(CLIENT_NUMBER_LENGTH, '0') }]
    : [{ clientName: trimmed }, { clientAcronym: trimmed }];

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
