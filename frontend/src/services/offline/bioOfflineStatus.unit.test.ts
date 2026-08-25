import { describe, expect, it } from 'vitest';

import { bioRowStatus, type BioRowInputs } from '@/services/offline/bioOfflineStatus';

const inputs = (over: Partial<BioRowInputs> = {}): BioRowInputs => ({
  syncState: 'CLEAN',
  pendingAttachments: 0,
  rejectedAttachments: 0,
  ...over,
});

describe('bioRowStatus', () => {
  it('shows a clean copy as synced', () => {
    expect(bioRowStatus(inputs())).toMatchObject({ label: 'Synced', tag: 'green' });
  });

  it('shows local edits as unsynced', () => {
    expect(bioRowStatus(inputs({ syncState: 'DIRTY' })))
      .toMatchObject({ label: 'Unsynced changes', tag: 'magenta' });
  });

  it('treats a queued file as an unsynced change on an otherwise clean copy', () => {
    // A file captured offline is a local change whether or not a field was edited, and its bytes
    // exist nowhere else. Showing "Synced" would invite the user to remove the copy.
    expect(bioRowStatus(inputs({ syncState: 'CLEAN', pendingAttachments: 1 })))
      .toMatchObject({ label: 'Unsynced changes', tag: 'magenta' });
  });

  it('names which half of the check-in is running', () => {
    expect(bioRowStatus(inputs({ syncState: 'FLUSHING_ATTACHMENTS', pendingAttachments: 3 })))
      .toMatchObject({ label: 'Uploading files (3 left)', tag: 'blue' });
    expect(bioRowStatus(inputs({ syncState: 'SYNCING_GRAPH' })))
      .toMatchObject({ label: 'Checking in', tag: 'blue' });
  });

  it('surfaces a conflict with its reason', () => {
    expect(bioRowStatus(inputs({ syncState: 'CONFLICT', conflictReason: 'Reclaimed' })))
      .toMatchObject({ label: 'Needs attention', tag: 'red', detail: 'Reclaimed' });
  });

  it('shows a stale copy as out of date', () => {
    expect(bioRowStatus(inputs({ syncState: 'DIRTY', verdict: 'SUBMITTED_ELSEWHERE' })))
      .toMatchObject({ label: 'Out of date', tag: 'red' });
  });

  it('shows an unverifiable copy as unverified rather than clean', () => {
    expect(bioRowStatus(inputs({ verdict: 'UNVERIFIED' })))
      .toMatchObject({ label: 'Unverified', tag: 'cool-gray' });
  });

  it('ranks rejected files above staleness and above a conflict', () => {
    // The one state holding bytes that exist nowhere else and need a conscious decision. Burying it
    // under "Out of date" is how field evidence gets discarded by accident.
    expect(bioRowStatus(inputs({
      syncState: 'CONFLICT',
      verdict: 'RECLAIMED',
      rejectedAttachments: 2,
    }))).toMatchObject({ label: '2 files rejected', tag: 'red' });
  });

  it('singularises one rejected file', () => {
    expect(bioRowStatus(inputs({ rejectedAttachments: 1 })).label).toBe('1 file rejected');
  });

  it('ranks staleness above the local sync state', () => {
    // A superseded copy cannot be checked in at all, so "Unsynced changes" would be misleading.
    expect(bioRowStatus(inputs({ syncState: 'DIRTY', verdict: 'GONE' })).label).toBe('Out of date');
  });
});
