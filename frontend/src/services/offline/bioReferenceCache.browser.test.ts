import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { bioDb } from '@/services/offline/bioDb';
import { withBioReferenceCache } from '@/services/offline/bioConfigurationFacade';
import { bioReferenceCache, filterBec } from '@/services/offline/bioReferenceCache';

import type { ConfigurationService } from '@/services/configuration.service';
import type { BecRow } from '@/types/configuration';

const bec = (over: Partial<BecRow> = {}): BecRow => ({
  bgcZoneCode: 'SBS',
  bgcSubzoneCode: 'mk',
  bgcVariant: '1',
  bgcPhase: '',
  becSiteSeriesCd: '01',
  siteSeriesPhaseCd: '',
  seral: '',
  description: 'SxwFdi - Oak fern',
  ...over,
});

const stubClient = () =>
  ({
    getSpecies: vi.fn().mockResolvedValue([{ code: 'SX', description: 'Spruce' }]),
    getWildlifeTreeDecay: vi.fn().mockResolvedValue([{ code: '1', description: 'Class 1' }]),
    getCwdDecay: vi.fn().mockResolvedValue([{ code: '2', description: 'Class 2' }]),
    getStrataTypes: vi.fn().mockResolvedValue([{ code: 'DO', description: 'Dominant' }]),
    searchBec: vi.fn().mockResolvedValue([bec(), bec({ bgcZoneCode: 'IDF', becSiteSeriesCd: '03' })]),
  }) as unknown as ConfigurationService;

/** Force `navigator.onLine`, which the facade reads to skip a doomed network attempt. */
const setOnline = (value: boolean) => {
  Object.defineProperty(navigator, 'onLine', { value, configurable: true });
};

describe('bioReferenceCache', () => {
  let client: ConfigurationService;

  beforeEach(async () => {
    await bioDb.bioReference.clear();
    client = stubClient();
    setOnline(true);
  });

  afterEach(() => setOnline(true));

  it('pulls and stores every list, including the whole BEC catalogue', async () => {
    await bioReferenceCache.refresh(client);

    expect(await bioReferenceCache.codeList('species')).toHaveLength(1);
    expect(await bioReferenceCache.codeList('strataTypes')).toHaveLength(1);
    expect(await bioReferenceCache.bec()).toHaveLength(2);
    // Blank criteria = the entire catalogue; no dedicated endpoint was needed.
    expect(client.searchBec).toHaveBeenCalledWith({});
  });

  it('stores reference data app-wide, not under a checklist', async () => {
    // Several checkouts on one device must not mean several copies of a ~2 MB catalogue.
    await bioReferenceCache.refresh(client);
    await bioReferenceCache.refresh(client);

    expect(await bioDb.bioReference.count()).toBe(5);
  });

  describe('facade routing', () => {
    it('prefers the network so an online user is never served stale codes', async () => {
      await bioReferenceCache.refresh(client);
      const api = withBioReferenceCache(client);

      await api.getSpecies();

      // Called twice: once by the refresh above, once by this read.
      expect(client.getSpecies).toHaveBeenCalledTimes(2);
    });

    it('falls back to the cache when the network fails', async () => {
      await bioReferenceCache.refresh(client);
      (client.getSpecies as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('offline'));
      const api = withBioReferenceCache(client);

      expect(await api.getSpecies()).toEqual([{ code: 'SX', description: 'Spruce' }]);
    });

    it('skips the network entirely when the browser knows it is offline', async () => {
      // A device on no signal shouldn't wait for five timeouts to render a dropdown.
      await bioReferenceCache.refresh(client);
      const api = withBioReferenceCache(client);
      (client.getSpecies as ReturnType<typeof vi.fn>).mockClear();
      setOnline(false);

      expect(await api.getSpecies()).toHaveLength(1);
      expect(client.getSpecies).not.toHaveBeenCalled();
    });

    it('raises rather than returning an empty list on a cache miss', async () => {
      // An empty dropdown reads as "no species exist" and would have the evaluator conclude the
      // data is wrong; an error at least says the app could not load it.
      const api = withBioReferenceCache(client);
      setOnline(false);

      await expect(api.getSpecies()).rejects.toThrow(/isn't available offline/i);
    });

    it('filters the cached catalogue offline instead of calling the server', async () => {
      await bioReferenceCache.refresh(client);
      const api = withBioReferenceCache(client);
      (client.searchBec as ReturnType<typeof vi.fn>).mockClear();
      setOnline(false);

      const rows = await api.searchBec({ bgcZoneCode: 'idf' });

      expect(rows).toHaveLength(1);
      expect(rows[0].bgcZoneCode).toBe('IDF');
      expect(client.searchBec).not.toHaveBeenCalled();
    });
  });
});

describe('filterBec', () => {
  // Reimplements FREP_52_BGC_SEARCH's semantics: every criterion is an UPPER(col) LIKE '%x%'
  // contains-match, and a blank one matches everything. A picker that matched differently offline
  // would have field staff selecting codes they can't reproduce at their desk.
  const rows = [
    bec(),
    bec({ bgcZoneCode: 'IDF', bgcSubzoneCode: 'dk', becSiteSeriesCd: '03', seral: 'MS' }),
  ];

  it('matches everything when no criteria are given', () => {
    expect(filterBec(rows, {})).toHaveLength(2);
  });

  it('treats a blank criterion as no filter', () => {
    expect(filterBec(rows, { bgcZoneCode: '   ' })).toHaveLength(2);
  });

  it('is case-insensitive', () => {
    expect(filterBec(rows, { bgcZoneCode: 'sbs' })).toHaveLength(1);
  });

  it('matches on a substring, not just a prefix', () => {
    expect(filterBec(rows, { bgcZoneCode: 'DF' })).toHaveLength(1);
  });

  it('ands multiple criteria together', () => {
    expect(filterBec(rows, { bgcZoneCode: 'IDF', seral: 'MS' })).toHaveLength(1);
    expect(filterBec(rows, { bgcZoneCode: 'IDF', seral: 'ZZ' })).toHaveLength(0);
  });

  it('matches a row whose column is empty only when the criterion is blank', () => {
    expect(filterBec(rows, { seral: '' })).toHaveLength(2);
    expect(filterBec(rows, { seral: 'MS' })).toHaveLength(1);
  });
});
