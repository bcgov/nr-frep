import { beforeEach, describe, expect, it, vi } from 'vitest';

import { clientLabel, searchClientsAuto } from './clientSearch';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: { search: { searchClients: vi.fn() } },
}));

const api = API.search as unknown as { searchClients: ReturnType<typeof vi.fn> };

const client = (over: Record<string, string> = {}) => ({
  clientNumber: '00066838',
  clientAcronym: 'ABC',
  clientName: 'ACME LOGGING LTD',
  clientLocnCode: '00',
  clientLocnName: 'HEAD OFFICE',
  city: 'VICTORIA',
  clientStatus: 'ACT',
  ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  api.searchClients.mockResolvedValue([]);
});

describe('searchClientsAuto', () => {
  it('does nothing under three characters', async () => {
    for (const term of ['', ' ', 'ab', '  a  ']) {
      expect(await searchClientsAuto(term)).toEqual([]);
    }
    expect(api.searchClients).not.toHaveBeenCalled();
  });

  it('searches name and acronym together for a text term', async () => {
    // The user cannot be expected to know which of the two they are typing.
    await searchClientsAuto('acme');

    expect(api.searchClients).toHaveBeenCalledTimes(2);
    expect(api.searchClients).toHaveBeenCalledWith({ clientName: 'acme' });
    // Bare, NOT "acme%": FREP_410_CLIENT_SEARCH appends the wildcard itself, unlike the SIL21 proc
    // nr-fspts calls where the caller supplies it.
    expect(api.searchClients).toHaveBeenCalledWith({ clientAcronym: 'acme' });
  });

  it('treats an all-digit term as a client number, zero-padded to the stored width', async () => {
    // The proc matches client_number exactly, and FOREST_CLIENT stores it padded to 8 — so typing
    // 66838 has to become 00066838 or it finds nothing.
    await searchClientsAuto('66838');

    expect(api.searchClients).toHaveBeenCalledTimes(1);
    expect(api.searchClients).toHaveBeenCalledWith({ clientNumber: '00066838' });
  });

  it('de-duplicates by client number across the two arms', async () => {
    // A client whose name AND acronym both match appears in both result sets; a client with several
    // locations returns one row per location. The picker wants one entry per client.
    api.searchClients
      .mockResolvedValueOnce([
        client({ clientLocnCode: '00' }),
        client({ clientLocnCode: '01' }),
        client({ clientNumber: '00000123', clientName: 'OTHER' }),
      ])
      .mockResolvedValueOnce([client({ clientLocnCode: '00' })]);

    const results = await searchClientsAuto('acme');

    expect(results.map((r) => r.clientNumber)).toEqual(['00066838', '00000123']);
  });

  it('survives one failing arm', async () => {
    // A failed acronym search must not sink the whole lookup.
    api.searchClients.mockResolvedValueOnce([client()]).mockRejectedValueOnce(new Error('boom'));

    expect(await searchClientsAuto('acme')).toHaveLength(1);
  });
});

describe('clientLabel', () => {
  it('reads as "Name (ACRONYM) · number"', () => {
    expect(clientLabel(client())).toBe('ACME LOGGING LTD (ABC) · 00066838');
  });

  it('drops the parts a client does not have', () => {
    expect(clientLabel(client({ clientAcronym: '' }))).toBe('ACME LOGGING LTD · 00066838');
    expect(clientLabel(client({ clientName: '', clientAcronym: '' }))).toBe('00066838');
  });

  it('strips the trailing commas the proc leaves on a client with no legal names', () => {
    // FREP_410_CLIENT_SEARCH builds client_name as name || ',middle' || ',first' with no TRIM, so a
    // company (which has neither) comes back as "ACME LOGGING LTD,,".
    expect(clientLabel(client({ clientName: 'ACME LOGGING LTD,,', clientAcronym: '' }))).toBe(
      'ACME LOGGING LTD · 00066838',
    );
  });
});
