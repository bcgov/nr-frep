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
    ).toMatch(/from 0 to 359/);
  });

  it('requires Evaluated by', () => {
    expect(plotHeaderErrors(validHeader({ assessorName: '' }), 'DO').assessorName).toMatch(
      /required/,
    );
  });

  it('requires Plot # and enforces 0–999 when present', () => {
    expect(plotHeaderErrors(validHeader({ plotNumber: '' }), 'DO').plotNumber).toMatch(/required/);
    expect(plotHeaderErrors(validHeader({ plotNumber: '1000' }), 'DO').plotNumber).toMatch(
      /from 0 to 999/,
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
      /from 1 to 99/,
    );
    expect(
      plotHeaderErrors(validHeader({ basalAreaFactor: '', fixedAreaRadius: '1.234' }), 'DO')
        .fixedAreaRadius,
    ).toMatch(/2 decimal places/);
    expect(
      plotHeaderErrors(validHeader({ basalAreaFactor: '', fullCountArea: '0' }), 'DO')
        .fullCountArea,
    ).toMatch(/between 0.01 and 9999.99/);
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
    expect(standRowErrors({ dbh: '10' }).dbh).toMatch(/greater than 12.5/);
    expect(standRowErrors({ dbh: '20.55' }).dbh).toMatch(/1 decimal place/);
    expect(standRowErrors({ height: '0.5' }).height).toMatch(/between 1.4 and 99.9/);
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
    expect(cwdRowErrors({ logDiameter: '5' }).logDiameter).toMatch(/between 7.6 and 400/);
    expect(cwdRowErrors({ logLength: '0' }).logLength).toMatch(/greater than 0/);
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
