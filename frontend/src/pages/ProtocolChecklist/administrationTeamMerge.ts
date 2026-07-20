import type { AdministrationData } from '@/types/protocolChecklist';

// Fields the user edits in the Administration form. Team add/remove hit the server immediately and
// return the whole administration record; without this, that response would clobber these still-
// unsaved edits (e.g. the People-on-block value the user just typed). So on a team update we keep
// the in-progress values for these fields and take everything else (team list, lead, revision
// counts) from the server.
const EDITABLE_FIELDS: (keyof AdministrationData)[] = [
  'evaluationDate',
  'blockAccessTime',
  'hoursOnBlock',
  'peopleOnBlock',
  'siteAccessCode',
  'additionalComments',
];

/**
 * Merge a server response from a team add/remove without discarding the user's in-progress field
 * edits. {@code server} is authoritative for the team roster and revision counts; the editable
 * fields keep whatever the user has typed but not yet saved.
 */
export function mergeTeamUpdate(
  server: AdministrationData,
  pending: AdministrationData | null,
): AdministrationData {
  if (!pending) return server;
  const merged = { ...server } as Record<string, unknown>;
  const prev = pending as Record<string, unknown>;
  for (const key of EDITABLE_FIELDS) {
    merged[key] = prev[key];
  }
  return merged as AdministrationData;
}
