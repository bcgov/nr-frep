import { describe, expect, it } from 'vitest';

import {
  openingMissingCount,
  openingStatus,
  plotsOutstanding,
  plotsStatus,
  stratumOutstanding,
  stratumStatus,
} from './tabStatus';

import type { StratumBundle } from './tabStatus';
import type { BiodiversityOpening, BioPlot, BioStratum } from '@/types/protocolChecklist';

const completeOpening: BiodiversityOpening = {
  locationDescription: 'Block 12, north face',
  evaluationDate: '2026-01-15',
  teamLeadNameId: 'ASODHI',
  invasivePlantIndicator: 'N',
  innovativePracticeInd: 'N',
  frepSiteEvaluationCode: 'H',
  grossArea: '50',
  netArea: '40',
};

/** A plot that passes every per-plot rule, including UTM. */
const goodPlot = (over: Partial<BioPlot> = {}): BioPlot => ({
  plotId: '1',
  plotNumber: '1',
  utmSignal: 'Y',
  utmZone: '10',
  utmEasting: '123456',
  utmNorthing: '1234567',
  firstLegTransect: '090',
  secondLegTransect: '180',
  basalAreaFactor: '5',
  treeIndicator: 'N',
  cwdTransectIndicator: 'N',
  standTable: [],
  cwdTable: [],
  ...over,
});

const stratum = (over: Partial<BioStratum> = {}): BioStratum => ({
  stratumId: '1',
  stratumNumber: '1',
  strataTypeCode: 'CC',
  consistentMapInd: 'Y',
  size: '10',
  plotCount: '1',
  harvestAreaCode: 'HDR',
  ...over,
});

const bundle = (
  over: Partial<BioStratum> = {},
  plots: BioPlot[] = [goodPlot()],
): StratumBundle => ({
  stratum: stratum(over),
  plots,
});

describe('openingStatus / openingMissingCount', () => {
  it('stays empty until something has been saved on the tab', () => {
    // Read-only RESULTS values arrive on every record and must not count as progress, so a record
    // carrying only those is still untouched — no red count on a checklist nobody has opened.
    const untouched = { grossArea: '50', netArea: '40', harvestDate: '2025-06-01' };
    expect(openingStatus(untouched)).toBe('empty');
    expect(openingStatus(null)).toBe('empty');
    // The count itself is still computed — it is the status that holds it back.
    expect(openingMissingCount(untouched)).toBe(6);
  });

  it('shows the count once any editable field has been saved', () => {
    // One field saved, five required ones still owed.
    expect(openingStatus({ locationDescription: 'Block 12' })).toBe('errors');
    expect(openingMissingCount({ locationDescription: 'Block 12' })).toBe(5);
  });

  it('counts an unreadable record as owing everything', () => {
    expect(openingMissingCount(null)).toBe(6);
  });

  it('counts down as fields are filled in', () => {
    expect(openingMissingCount({ ...completeOpening, teamLeadNameId: '' })).toBe(1);
    expect(openingStatus({ ...completeOpening, teamLeadNameId: '' })).toBe('errors');
  });

  it('counts a conditional comment only once its indicator is Yes', () => {
    expect(openingMissingCount({ ...completeOpening, invasivePlantIndicator: 'N' })).toBe(0);
    expect(openingMissingCount({ ...completeOpening, invasivePlantIndicator: 'Y' })).toBe(1);
  });

  it('ignores format errors — they block the save, they are not missing fields', () => {
    // A 60-byte location is too long for the column, but it is not *missing*.
    expect(openingMissingCount({ ...completeOpening, locationDescription: 'x'.repeat(60) })).toBe(
      0,
    );
  });

  it('is complete when nothing is outstanding', () => {
    expect(openingStatus(completeOpening)).toBe('complete');
    expect(openingMissingCount(completeOpening)).toBe(0);
  });
});

describe('stratumStatus / stratumOutstanding', () => {
  it('reads as empty with no strata, but still owes one', () => {
    // The tab is quiet (nothing saved on it yet); the submit pre-flight still gets the item, since
    // `frep.submit.biodiversity.stratum.mandatory` would refuse the checklist.
    expect(stratumStatus([], completeOpening)).toBe('empty');
    expect(stratumOutstanding([], completeOpening)).toEqual([
      'No strata have been added — at least one is required',
    ]);
  });

  it('lists a plot count that disagrees with the Plots tab', () => {
    const items = stratumOutstanding([bundle({ plotCount: '2' })], completeOpening);
    expect(stratumStatus([bundle({ plotCount: '2' })], completeOpening)).toBe('errors');
    expect(items).toEqual(['Stratum 1 — “# of plots in stratum” says 2, but 1 plot exists']);
  });

  it('lists a blank plot count', () => {
    expect(stratumOutstanding([bundle({ plotCount: undefined })], completeOpening)).toEqual([
      'Stratum 1 — missing “# of plots in stratum”',
    ]);
  });

  it('lists the fields the tab no longer refuses to save', () => {
    const items = stratumOutstanding(
      [bundle({ stratumNumber: undefined, strataTypeCode: undefined, size: undefined })],
      completeOpening,
    );
    expect(items).toEqual([
      'Stratum 1 — missing Stratum number',
      'Stratum 1 — missing Stratum type',
      'Stratum 1 — missing Mapped size',
    ]);
  });

  it('counts the combined size against the area cap once for the tab', () => {
    const items = stratumOutstanding([bundle({ size: '80' })], completeOpening);
    expect(items[0]).toContain('Combined stratum size (80 ha) is over the 50 ha limit');
  });

  it('takes the cap from the override when it is larger than the gross area', () => {
    const opening = { ...completeOpening, frepWtpOverride: '100' };
    expect(stratumStatus([bundle({ strataTypeCode: 'PR', size: '80' })], opening)).toBe('complete');
  });

  it('uses the estimated size when the stratum is not map-consistent', () => {
    const notMapped = {
      strataTypeCode: 'PR',
      consistentMapInd: 'N',
      size: '80',
      estimatedSize: '10',
    };
    expect(stratumStatus([bundle(notMapped)], completeOpening)).toBe('complete');
  });

  it('lists a NAR-capped type that exceeds the NAR', () => {
    // netArea 40; mapped size 45 on a CC stratum.
    const items = stratumOutstanding([bundle({ size: '45' })], completeOpening);
    expect(items).toContain('Stratum 1 — mapped size (45 ha) is over the NAR (40 ha)');
  });

  it('leaves other stratum types uncapped by the NAR', () => {
    expect(stratumStatus([bundle({ strataTypeCode: 'PR', size: '45' })], completeOpening)).toBe(
      'complete',
    );
  });

  it('is complete when every stratum rule passes', () => {
    expect(stratumStatus([bundle()], completeOpening)).toBe('complete');
    expect(stratumOutstanding([bundle()], completeOpening)).toEqual([]);
  });
});

describe('plotsStatus / plotsOutstanding', () => {
  it('reads as empty with no plots, but still owes the UTM rule', () => {
    // `frep.submit.biodiversity.plot.utmrequired` is checklist-wide and fires with zero plots too.
    expect(plotsStatus([bundle({}, [])])).toBe('empty');
    expect(plotsOutstanding([bundle({}, [])])).toEqual([
      'No plot has UTM coordinates — one plot needs Zone, Easting and Northing',
    ]);
  });

  it('counts missing UTM once for the tab, not once per plot', () => {
    const plots = [goodPlot({ utmSignal: 'N' }), goodPlot({ plotId: '2', utmSignal: 'N' })];
    const items = plotsOutstanding([bundle({ plotCount: '2' }, plots)]);
    expect(plotsStatus([bundle({ plotCount: '2' }, plots)])).toBe('errors');
    expect(items).toEqual([
      'No plot has UTM coordinates — one plot needs Zone, Easting and Northing',
    ]);
  });

  it('needs only one plot with UTM across the checklist', () => {
    const plots = [goodPlot(), goodPlot({ plotId: '2', plotNumber: '2', utmSignal: 'N' })];
    expect(plotsStatus([bundle({ plotCount: '2' }, plots)])).toBe('complete');
  });

  it('lists a missing bearing leg', () => {
    expect(plotsOutstanding([bundle({}, [goodPlot({ secondLegTransect: '' })])])).toEqual([
      'Plot 1 (Stratum 1) — missing Bearing 1st leg / 2nd leg',
    ]);
  });

  it('lists a plot with no measurement method', () => {
    expect(plotsOutstanding([bundle({}, [goodPlot({ basalAreaFactor: '' })])])).toEqual([
      'Plot 1 (Stratum 1) — no BAF, fixed-area radius or full-count area entered',
    ]);
  });

  it('lists "Trees exist" with no stand-table rows', () => {
    const items = plotsOutstanding([bundle({}, [goodPlot({ treeIndicator: 'Y' })])]);
    expect(items).toContain(
      'Plot 1 (Stratum 1) — “Trees exist” is checked but the stand table is empty',
    );
  });

  it('lists "CWD in transect" with no CWD rows', () => {
    const items = plotsOutstanding([bundle({}, [goodPlot({ cwdTransectIndicator: 'Y' })])]);
    expect(items).toContain(
      'Plot 1 (Stratum 1) — “CWD in transect” is checked but the CWD table is empty',
    );
  });

  it('lists stand-table rows under a no-retention stratum', () => {
    const plots = [goodPlot({ treeIndicator: 'Y', standTable: [{}] })];
    const items = plotsOutstanding([bundle({ harvestAreaCode: 'HNR' }, plots)]);
    expect(items).toContain(
      'Plot 1 (Stratum 1) — has stand-table rows, but its stratum has no retention',
    );
  });

  it('lists a first full-count area that is not under the stratum size', () => {
    // stratum size 10, full-count area 12.
    const plots = [goodPlot({ basalAreaFactor: '', fullCountArea: '12' })];
    const items = plotsOutstanding([bundle({}, plots)]);
    expect(items).toContain(
      'Plot 1 (Stratum 1) — full-count area (12 ha) is not under the stratum size (10 ha)',
    );
  });

  it('allows only the first full-count plot to record trees', () => {
    const first = goodPlot({ basalAreaFactor: '', fullCountArea: '5' });
    const second = goodPlot({
      plotId: '2',
      plotNumber: '2',
      basalAreaFactor: '',
      fullCountArea: '5',
      treeIndicator: 'Y',
      standTable: [{}],
    });
    const items = plotsOutstanding([bundle({ plotCount: '2' }, [first, second])]);
    expect(items).toContain(
      'Plot 2 (Stratum 1) — only the first full-count plot in a stratum may record trees',
    );
  });

  it('is complete when every plot rule passes', () => {
    expect(plotsStatus([bundle()])).toBe('complete');
    expect(plotsOutstanding([bundle()])).toEqual([]);
  });
});
