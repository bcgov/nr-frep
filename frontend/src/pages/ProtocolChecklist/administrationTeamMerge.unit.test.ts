import { describe, expect, it } from 'vitest';

import { mergeTeamUpdate } from './administrationTeamMerge';

import type { AdministrationData } from '@/types/protocolChecklist';

describe('mergeTeamUpdate', () => {
  it('keeps the user’s in-progress field edits while taking the team roster + revision counts from the server', () => {
    // The user typed People-on-block = "2" (unsaved) then added a team member. The server response
    // still has peopleOnBlock blank because it was never persisted — without the merge it would wipe
    // the "2" and trip the "must be >= team size" validation.
    const pending: AdministrationData = {
      peopleOnBlock: '2',
      hoursOnBlock: '8',
      siteAccessCode: 'HELI',
      additionalComments: 'draft note',
      teamMembers: [],
      revisionCount: '5',
      revisionCountAccess: '3',
    };
    const server: AdministrationData = {
      peopleOnBlock: '',
      hoursOnBlock: '',
      siteAccessCode: '',
      additionalComments: '',
      teamMembers: [{ evaluatorUserid: 'IDIR\\JDOE', teamLeadInd: 'N', revisionCount: '1' }],
      revisionCount: '6',
      revisionCountAccess: '4',
    };

    const merged = mergeTeamUpdate(server, pending);

    // Editable fields keep the pending edits.
    expect(merged.peopleOnBlock).toBe('2');
    expect(merged.hoursOnBlock).toBe('8');
    expect(merged.siteAccessCode).toBe('HELI');
    expect(merged.additionalComments).toBe('draft note');
    // Roster + revision counts come from the authoritative server response.
    expect(merged.teamMembers).toHaveLength(1);
    expect(merged.revisionCount).toBe('6');
    expect(merged.revisionCountAccess).toBe('4');
  });

  it('returns the server record unchanged when there is no pending state', () => {
    const server: AdministrationData = { peopleOnBlock: '', teamMembers: [] };
    expect(mergeTeamUpdate(server, null)).toBe(server);
  });
});
