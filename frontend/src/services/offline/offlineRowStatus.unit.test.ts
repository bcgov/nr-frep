import { describe, expect, it } from 'vitest';

import { bioRowStatus, chrRowStatus, type BioRowInputs } from '@/services/offline/offlineRowStatus';

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

  it('shows an unverifiable copy with nothing local as unverified rather than clean', () => {
    expect(bioRowStatus(inputs({ verdict: 'UNVERIFIED' })))
      .toMatchObject({ label: 'Unverified', tag: 'cool-gray' });
  });

  it('reports unsynced work ahead of "we could not check"', () => {
    // Offline EVERY row is unverified, so leading with it made the whole column read the same word
    // and hid the copies holding work that has not reached the server — the fact that matters in
    // the field, which is exactly where this list is read.
    expect(bioRowStatus(inputs({ syncState: 'DIRTY', verdict: 'UNVERIFIED' }))).toMatchObject({
      label: 'Unsynced changes',
      tag: 'magenta',
    });
  });

  it('keeps the unverified caveat on the row rather than dropping it', () => {
    expect(bioRowStatus(inputs({ syncState: 'DIRTY', verdict: 'UNVERIFIED' })).detail)
      .toContain('offline');
  });

  it('counts a queued file as unsynced work even on a clean, unverified copy', () => {
    expect(
      bioRowStatus(inputs({ verdict: 'UNVERIFIED', pendingAttachments: 1 })),
    ).toMatchObject({ label: 'Unsynced changes' });
  });

  it('still lets staleness and rejected files outrank both', () => {
    // The fix must not promote local state above the two states that make a copy un-checkinable.
    expect(bioRowStatus(inputs({ syncState: 'DIRTY', verdict: 'RECLAIMED' })))
      .toMatchObject({ label: 'Out of date' });
    expect(bioRowStatus(inputs({ syncState: 'DIRTY', verdict: 'UNVERIFIED', rejectedAttachments: 1 })))
      .toMatchObject({ label: '1 file rejected' });
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

describe('chrRowStatus', () => {
  it('reports unsynced work ahead of "we could not check", as SLR does', () => {
    expect(chrRowStatus({ dirty: true, verdict: 'UNVERIFIED' })).toMatchObject({
      label: 'Unsynced changes',
      tag: 'magenta',
    });
    expect(chrRowStatus({ dirty: true, verdict: 'UNVERIFIED' }).detail).toContain('offline');
  });

  it('says unverified when there is nothing local to report', () => {
    expect(chrRowStatus({ dirty: false, verdict: 'UNVERIFIED' }))
      .toMatchObject({ label: 'Unverified' });
  });

  it('still puts staleness first', () => {
    expect(chrRowStatus({ dirty: true, verdict: 'SUBMITTED_ELSEWHERE' }))
      .toMatchObject({ label: 'Out of date' });
  });
});
