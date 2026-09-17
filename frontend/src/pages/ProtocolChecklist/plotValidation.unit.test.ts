import { describe, expect, it } from 'vitest';

import type { BioPlot } from '@/types/protocolChecklist';

import {
  cwdRowErrors,
  plotHeaderErrors,
  standRowErrors,
} from '@/pages/ProtocolChecklist/plotValidation';

// A valid non-clear-cut plot header: no UTM signal, both legs, an evaluator, one measurement method.
const validHeader = (overrides: Partial<BioPlot> = {}): BioPlot => ({
  utmSignal: 'N',
  firstLegTransect: '120',
  secondLegTransect: '240',
  plotNumber: '1',
  assessorName: 'IDIR\\JDOE',
  basalAreaFactor: '10',
  ...overrides,
});

describe('plotHeaderErrors', () => {
  it('passes a valid header', () => {
    expect(plotHeaderErrors(validHeader(), 'DO')).toEqual({});
  });

  it('requires UTM zone/easting/northing only when a signal is available', () => {
    const withSignal = plotHeaderErrors(validHeader({ utmSignal: 'Y' }), 'DO');
    expect(withSignal.utmZone).toMatch(/required/);
    expect(withSignal.utmEasting).toMatch(/required/);
    expect(withSignal.utmNorthing).toMatch(/required/);
  });

  it('exempts a plot that never answered the UTM question', () => {
    // Legacy rows: UTM_SIGNAL is nullable and predates this app, which always writes 'Y' or 'N'.
    // Silence is not a yes — neither the columns, nor FREP_BIODIVERSITY_PLOT.VALIDATE, nor legacy's
    // UtmSignalCompleteValidator (which tested `equals("Y")`) ever asked those rows for coordinates.
    expect(plotHeaderErrors(validHeader({ utmSignal: undefined }), 'DO')).toEqual({});
    expect(plotHeaderErrors(validHeader({ utmSignal: '' }), 'DO')).toEqual({});
  });

  it('still checks the shape of a coordinate that was entered, whatever the signal says', () => {
    // Exempt from being *required* is not exempt from being right: legacy registered its
    // Easting/Northing field validators unconditionally, and only a blank field is ever excused.
    const e = plotHeaderErrors(validHeader({ utmSignal: undefined, utmEasting: '123' }), 'DO');
    expect(e.utmEasting).toMatch(/exactly 6 digits/);
    expect(e.utmZone).toBeUndefined();
    expect(e.utmNorthing).toBeUndefined();
  });

  it('checks easting/northing digit counts', () => {
    const e = plotHeaderErrors(
      validHeader({ utmSignal: 'Y', utmZone: '10', utmEasting: '123', utmNorthing: '12' }),
      'DO',
    );
    expect(e.utmEasting).toMatch(/exactly 6 digits/);
    expect(e.utmNorthing).toMatch(/exactly 7 digits/);
  });

  it('requires both bearing legs and enforces 0–359', () => {
    expect(plotHeaderErrors(validHeader({ firstLegTransect: '' }), 'DO').firstLegTransect).toMatch(
      /required/,
    );
    expect(
      plotHeaderErrors(validHeader({ secondLegTransect: '400' }), 'DO').secondLegTransect,
    ).toMatch(/at most 359/);
  });

  it('requires Evaluated by', () => {
    expect(plotHeaderErrors(validHeader({ assessorName: '' }), 'DO').assessorName).toMatch(
      /required/,
    );
  });

  it('requires Plot # and enforces 0–999 when present', () => {
    expect(plotHeaderErrors(validHeader({ plotNumber: '' }), 'DO').plotNumber).toMatch(/required/);
    expect(plotHeaderErrors(validHeader({ plotNumber: '1000' }), 'DO').plotNumber).toMatch(
      /at most 999/,
    );
  });

  it('never blocks "Trees exist" — allowed on every stratum type incl. clear-cut', () => {
    // The CC (except-NAR) gate was removed: trees exist is valid regardless of stratum type.
    expect(
      plotHeaderErrors(validHeader({ treeIndicator: 'Y' }), 'CC').treeIndicator,
    ).toBeUndefined();
    expect(
      plotHeaderErrors(validHeader({ treeIndicator: 'Y' }), 'DO').treeIndicator,
    ).toBeUndefined();
    expect(plotHeaderErrors(validHeader({ treeIndicator: 'Y' }), '').treeIndicator).toBeUndefined();
  });

  it('enforces BAF / fixed-area / full-count ranges and decimals', () => {
    expect(plotHeaderErrors(validHeader({ basalAreaFactor: '0' }), 'DO').basalAreaFactor).toMatch(
      /at least 1/,
    );
    expect(
      plotHeaderErrors(validHeader({ basalAreaFactor: '', fixedAreaRadius: '1.234' }), 'DO')
        .fixedAreaRadius,
    ).toMatch(/2 decimal places/);
    expect(
      plotHeaderErrors(validHeader({ basalAreaFactor: '', fullCountArea: '0' }), 'DO')
        .fullCountArea,
    ).toMatch(/at least 0.01/);
  });

  it('requires exactly one measurement method (non clear-cut)', () => {
    expect(plotHeaderErrors(validHeader({ basalAreaFactor: '' }), 'DO').basalAreaFactor).toMatch(
      /exactly one/,
    );
    expect(
      plotHeaderErrors(validHeader({ basalAreaFactor: '10', fullCountArea: '5' }), 'DO')
        .basalAreaFactor,
    ).toMatch(/exactly one/);
  });

  it('requires fixed-area radius (only) for a clear-cut plot', () => {
    expect(plotHeaderErrors(validHeader({ basalAreaFactor: '' }), 'CC').fixedAreaRadius).toMatch(
      /clear-cut/,
    );
    expect(plotHeaderErrors(validHeader({ basalAreaFactor: '10' }), 'CC').basalAreaFactor).toMatch(
      /blank for a clear-cut/,
    );
  });
});

describe('standRowErrors', () => {
  it('requires species, WT class, DBH and height', () => {
    const e = standRowErrors({});
    expect(e.speciesCode).toMatch(/required/);
    expect(e.decayClassCode).toMatch(/required/);
    expect(e.dbh).toMatch(/required/);
    expect(e.height).toMatch(/required/);
  });

  it('enforces DBH/height range and 1 decimal', () => {
    expect(standRowErrors({ dbh: '10' }).dbh).toMatch(/must be over 12.5/);
    expect(standRowErrors({ dbh: '20.55' }).dbh).toMatch(/1 decimal place/);
    expect(standRowErrors({ height: '0.5' }).height).toMatch(/at least 1.4/);
  });

  it('passes a complete row', () => {
    expect(
      standRowErrors({ speciesCode: 'FD', decayClassCode: '1', dbh: '25.4', height: '18.2' }),
    ).toEqual({});
  });
});

describe('cwdRowErrors', () => {
  it('requires species, decay class, diameter and length', () => {
    const e = cwdRowErrors({});
    expect(e.speciesCode).toMatch(/required/);
    expect(e.decayClassCode).toMatch(/required/);
    expect(e.logDiameter).toMatch(/required/);
    expect(e.logLength).toMatch(/required/);
  });

  it('enforces diameter/length ranges (length must be > 0)', () => {
    expect(cwdRowErrors({ logDiameter: '5' }).logDiameter).toMatch(/at least 7.6/);
    expect(cwdRowErrors({ logLength: '0' }).logLength).toMatch(/must be over 0/);
  });

  it('passes a complete row', () => {
    expect(
      cwdRowErrors({
        speciesCode: 'FD',
        decayClassCode: '1',
        logDiameter: '15.2',
        logLength: '3.5',
      }),
    ).toEqual({});
  });
});

describe('plotHeaderErrors — plot number uniqueness', () => {
  it('reports a number another plot in the stratum already holds', () => {
    // Otherwise FREP_BIODIVERSITY_PLOT.VALIDATE rejects the save with
    // frep.web.usr.database.record.plot.number.already.exists, costing a round-trip.
    const e = plotHeaderErrors(validHeader({ plotNumber: '2' }), 'DO', ['1', '2', '3']);
    expect(e.plotNumber).toBe('Plot 2 already exists in this stratum. Use a different number.');
  });

  it('accepts a number no other plot holds', () => {
    expect(plotHeaderErrors(validHeader({ plotNumber: '4' }), 'DO', ['1', '2', '3'])).toEqual({});
  });

  it('compares numerically, the way the column does', () => {
    // PLOT_NUMBER is NUMBER(3), so "01" and "1" are the same plot number to Oracle. Comparing as
    // text would pass this straight through to the proc.
    expect(plotHeaderErrors(validHeader({ plotNumber: '01' }), 'DO', ['1']).plotNumber).toMatch(
      /already exists/,
    );
  });

  it('says the number is invalid before it says it is taken', () => {
    // "Plot # must be a whole number" is the more useful of the two messages.
    const e = plotHeaderErrors(validHeader({ plotNumber: 'abc' }), 'DO', ['abc']);
    expect(e.plotNumber).not.toMatch(/already exists/);
  });

  it('ignores blanks among the taken numbers', () => {
    expect(plotHeaderErrors(validHeader({ plotNumber: '4' }), 'DO', ['', ' '])).toEqual({});
  });

  it('reports nothing when no other plots are passed', () => {
    // The default keeps every existing caller behaving exactly as before.
    expect(plotHeaderErrors(validHeader({ plotNumber: '1' }), 'DO')).toEqual({});
  });
});

/**
 * What may be said on every keystroke, and what has to wait for Save. A value no further typing can
 * rescue is reported at once; a value that is merely unfinished is not. See utils/validation.ts.
 */
describe('plot rules while the user is still typing', () => {
  const typing = (over: Partial<BioPlot>) =>
    plotHeaderErrors(validHeader(over), 'DO', [], 'typing');

  it('holds an Easting that is only part-typed, and names one that has overshot', () => {
    // Every six-digit Easting is typed through one, two and three digits first.
    expect(typing({ utmSignal: 'Y', utmEasting: '123' }).utmEasting).toBeUndefined();
    expect(typing({ utmSignal: 'Y', utmEasting: '1234567' }).utmEasting).toMatch(/exactly 6/);
    expect(typing({ utmSignal: 'Y', utmEasting: '12x' }).utmEasting).toMatch(/exactly 6/);
  });

  it('names a bearing past the compass straight away', () => {
    expect(typing({ firstLegTransect: '400' }).firstLegTransect).toMatch(/at most 359/);
  });

  it('holds a plot number that clashes until the save', () => {
    // "1" is a legitimate step towards "12"; the clash is reported once the number is finished.
    expect(
      plotHeaderErrors(validHeader({ plotNumber: '1' }), 'DO', ['1'], 'typing').plotNumber,
    ).toBeUndefined();
    expect(plotHeaderErrors(validHeader({ plotNumber: '1' }), 'DO', ['1']).plotNumber).toMatch(
      /already exists/,
    );
  });

  it('holds a measurement value below its floor, and names one above its ceiling', () => {
    expect(typing({ basalAreaFactor: '0' }).basalAreaFactor).toBeUndefined();
    expect(typing({ basalAreaFactor: '100' }).basalAreaFactor).toMatch(/at most 99/);
  });

  it('says nothing about a blank the plot still owes', () => {
    expect(plotHeaderErrors({ utmSignal: 'N' } as BioPlot, 'DO', [], 'typing')).toEqual({});
  });

  it('holds a stand row below its minimum, and names a decimal place too many', () => {
    // DBH starts at 12.5, so "1" is the first keystroke of every valid entry.
    expect(standRowErrors({ dbh: '1', height: '10' }, 'typing').dbh).toBeUndefined();
    expect(standRowErrors({ dbh: '20.55', height: '10' }, 'typing').dbh).toMatch(/1 decimal place/);
    expect(standRowErrors({ dbh: '20', height: '10' }, 'typing').speciesCode).toBeUndefined();
  });

  it('holds a CWD length that is still being typed', () => {
    expect(cwdRowErrors({ logDiameter: '1', logLength: '2.' }, 'typing')).toEqual({});
    expect(cwdRowErrors({ logDiameter: '1', logLength: '2.' }).logLength).toMatch(
      /must be a number/,
    );
  });
});
