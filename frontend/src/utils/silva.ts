import type { IdpProviderType } from '@/context/auth/types';

import { env } from '@/env';

/**
 * Deep links into SILVA (the corporate openings application).
 *
 * The legacy app linked Opening ID out to the external openings viewer; this restores that.
 *
 * SILVA accepts an `idp_hint` query parameter (`idir` or `bceid`) which pre-selects the identity
 * provider, so a user already signed in to FAM with the same provider lands on the opening without
 * a second login prompt. Omitting the hint drops them on the provider chooser, which is the whole
 * thing this is meant to avoid — so always pass it.
 *
 * Base URL comes from runtime config so it can be repointed without a rebuild (see src/env.ts,
 * which layers window.config over build-time env). SILVA currently only publishes a production
 * instance, so that is the default and non-prod FREP links there too.
 */
const SILVA_BASE_URL = env.VITE_SILVA_BASE_URL || 'https://silva.nrs.gov.bc.ca';

/** `idp_hint` value for the provider the user actually signed in with; defaults to IDIR. */
const idpHint = (idpProvider?: IdpProviderType): string =>
  idpProvider === 'BCEIDBUSINESS' ? 'bceid' : 'idir';

/**
 * URL for one opening in SILVA, or `null` when there is no opening id — callers render plain text
 * in that case rather than a link to nowhere.
 */
export const silvaOpeningUrl = (
  openingId: string | number | undefined | null,
  idpProvider?: IdpProviderType,
): string | null => {
  const id = String(openingId ?? '').trim();
  if (!id) return null;
  return `${SILVA_BASE_URL}/openings/${encodeURIComponent(id)}?idp_hint=${idpHint(idpProvider)}`;
};
