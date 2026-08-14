import { describe, expect, it } from 'vitest';

import { formatSubmitValidation } from './submitValidation';

describe('formatSubmitValidation', () => {
  it('substitutes args and lifts the tab name into the title', () => {
    expect(formatSubmitValidation('frep.submit.biodiversity.plot.notrees:1,av13')).toEqual({
      title: 'Plots tab',
      detail:
        'Plot 1 in stratum av13 has "Trees exist" checked but no stand-table rows. Add at least ' +
        'one row, or uncheck "Trees exist".',
    });
  });

  it('formats a single-arg stratum-size code', () => {
    const { title, detail } = formatSubmitValidation(
      'frep.submit.biodiversity.stratum.size.invalid:190',
    );
    expect(title).toBe('Stratum summary tab');
    expect(detail).toContain('combined stratum size (190 ha)');
  });

  it('handles a code with no args (trailing artifact ignored)', () => {
    expect(formatSubmitValidation("frep.submit.biodiversity.plot.utmrequired:'")).toEqual({
      title: 'Plots tab',
      detail:
        'At least one plot needs UTM coordinates — on a plot, uncheck "No UTM signal available" ' +
        'and enter Zone, Easting and Northing.',
    });
  });

  it('handles a bare code with no colon', () => {
    // The tab is "Opening info" — this message used to name an "Opening tab" that does not exist,
    // and the title is parsed straight from that prefix.
    expect(formatSubmitValidation('frep.submit.biodiversity.opening')).toEqual({
      title: 'Opening info tab',
      detail: 'Enter a Location description and save the tab.',
    });
  });

  it('maps the Opening-info team lead and evaluation date submit checks', () => {
    expect(formatSubmitValidation('frep.submit.common.teamlead')).toEqual({
      title: 'Opening info tab',
      detail: 'an evaluator is required.',
    });
    expect(formatSubmitValidation('frep.submit.common.evaluation')).toEqual({
      title: 'Opening info tab',
      detail: 'an evaluation date is required.',
    });
  });

  it('falls back to the raw code for unknown keys', () => {
    expect(formatSubmitValidation('frep.submit.something.else:9')).toEqual({
      title: 'Validation',
      detail: 'frep.submit.something.else:9',
    });
  });
});
