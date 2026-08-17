import { beforeEach, describe, expect, it, vi } from 'vitest';

import { clientLabel, isClientTermUnresolved, searchClientsAuto } from './clientSearch';

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

  it('skips the acronym arm when the term is too long to be an acronym', async () => {
    // THE.FREP_CLIENT_SEARCH_VW_OBJECT.CLIENT_ACRONYM is VARCHAR2(8). The driver pickles the
    // criteria before the call, so a longer value failed at bind time with ORA-17072 and 500ed —
    // on a term as ordinary as this one, on every keystroke.
    await searchClientsAuto('lakeside pacific');

    expect(api.searchClients).toHaveBeenCalledTimes(1);
    expect(api.searchClients).toHaveBeenCalledWith({ clientName: 'lakeside pacific' });
  });

  it('still searches both arms at exactly the acronym width', async () => {
    await searchClientsAuto('LAKEPAC1');

    expect(api.searchClients).toHaveBeenCalledWith({ clientAcronym: 'LAKEPAC1' });
  });

  it('does not bind a selected label to the acronym arm', async () => {
    // Carbon fires onInputChange with the item's own label when a suggestion is picked. This is the
    // exact value from the ORA-17072 in the TEST logs.
    expect(await searchClientsAuto('LAKESIDE PACIFIC FOREST PRODUCTS (LAKEPAC) \u00b7 00002483')).toEqual(
      [],
    );
    expect(api.searchClients).toHaveBeenCalledTimes(1);
    expect(api.searchClients).not.toHaveBeenCalledWith(
      expect.objectContaining({ clientAcronym: expect.anything() }),
    );
  });

  it('ignores a digit run too long to be a client number', async () => {
    // padStart cannot shorten, so 9+ digits used to bind over CLIENT_NUMBER's VARCHAR2(8).
    expect(await searchClientsAuto('123456789')).toEqual([]);
    expect(api.searchClients).not.toHaveBeenCalled();
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

  it('labels with the real client number, not the acronym-as-number', () => {
    // The backend used to return NVL(acronym, number) in the clientNumber slot, so a client with an
    // acronym read "ARDEW WOOD PRODUCTS LTD. (ARDEW) · ARDEW" — and the filter it fed was 'ARDEW',
    // which the checklist search pads to '000ARDEW' and never matches.
    expect(
      clientLabel(
        client({
          clientNumber: '00003680',
          displayClientNumber: 'ARDEW',
          clientAcronym: 'ARDEW',
          clientName: 'ARDEW WOOD PRODUCTS LTD.',
        }),
      ),
    ).toBe('ARDEW WOOD PRODUCTS LTD. (ARDEW) · 00003680');
  });
});

describe('isClientTermUnresolved', () => {
  const LABEL = 'ACME LOGGING LTD (ABC) \u00b7 00066838';

  it('flags a term that matched no client', () => {
    // The reported bug: "lakepaced" left clientNumber unset, so the search ran with no client
    // filter and returned all 1962 checklists instead of none.
    expect(isClientTermUnresolved('lakepaced', '', undefined)).toBe(true);
  });

  it('accepts a term the user actually picked', () => {
    expect(isClientTermUnresolved(LABEL, LABEL, '00066838')).toBe(false);
  });

  it('accepts an empty field', () => {
    // No client filter is a legitimate search; only *unresolved text* is not.
    expect(isClientTermUnresolved('', '', undefined)).toBe(false);
    expect(isClientTermUnresolved('   ', '', undefined)).toBe(false);
  });

  it('flags text edited after a pick, which leaves the old number behind', () => {
    expect(isClientTermUnresolved('ACME LOGG', LABEL, '00066838')).toBe(true);
  });
});
