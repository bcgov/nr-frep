import { describe, expect, it } from 'vitest';

import { silvaOpeningUrl } from './silva';

describe('silvaOpeningUrl', () => {
  it('builds an opening deep link with the IDIR hint by default', () => {
    expect(silvaOpeningUrl('110356')).toBe(
      'https://silva.nrs.gov.bc.ca/openings/110356?idp_hint=idir',
    );
  });

  it('sends the bceid hint for a BCeID Business session', () => {
    expect(silvaOpeningUrl('110356', 'BCEIDBUSINESS')).toBe(
      'https://silva.nrs.gov.bc.ca/openings/110356?idp_hint=bceid',
    );
  });

  it('sends the idir hint for an IDIR session', () => {
    expect(silvaOpeningUrl('110356', 'IDIR')).toBe(
      'https://silva.nrs.gov.bc.ca/openings/110356?idp_hint=idir',
    );
  });

  // The hint is the whole point of the integration — without it the user lands on the provider
  // chooser instead of the opening, which is the second login this was meant to remove.
  it('always carries an idp_hint', () => {
    expect(silvaOpeningUrl('1')).toContain('idp_hint=');
    expect(silvaOpeningUrl('1', 'IDIR')).toContain('idp_hint=');
    expect(silvaOpeningUrl('1', 'BCEIDBUSINESS')).toContain('idp_hint=');
  });

  it('accepts a numeric opening id', () => {
    expect(silvaOpeningUrl(110356)).toContain('/openings/110356?');
  });

  // Callers render plain text rather than a link to nowhere.
  it('returns null when there is no opening id', () => {
    expect(silvaOpeningUrl(undefined)).toBeNull();
    expect(silvaOpeningUrl(null)).toBeNull();
    expect(silvaOpeningUrl('')).toBeNull();
    expect(silvaOpeningUrl('   ')).toBeNull();
  });

  it('trims and encodes the opening id', () => {
    expect(silvaOpeningUrl('  110356  ')).toContain('/openings/110356?');
    expect(silvaOpeningUrl('a b')).toContain('/openings/a%20b?');
  });
});
