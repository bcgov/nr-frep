import { describe, expect, it } from 'vitest';

import { formatSubmitValidation } from './submitValidation';

describe('formatSubmitValidation', () => {
  it('substitutes args and lifts the tab name into the title', () => {
    expect(formatSubmitValidation('frep.submit.biodiversity.plot.notrees:1,av13')).toEqual({
      title: 'Plots tab',
      detail:
        'Plot 1 in stratum av13 needs trees entered in the stand table, or change the indicator.',
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
    expect(formatSubmitValidation('frep.submit.biodiversity.opening')).toEqual({
      title: 'Opening tab',
      detail: 'Enter a Location description and save the Opening tab.',
    });
  });

  it('maps the Administration-tab team lead and evaluation date submit checks', () => {
    expect(formatSubmitValidation('frep.submit.common.teamlead')).toEqual({
      title: 'Administration tab',
      detail: 'Team Lead is mandatory for submit.',
    });
    expect(formatSubmitValidation('frep.submit.common.evaluation')).toEqual({
      title: 'Administration tab',
      detail: 'Evaluation Date is mandatory for submission.',
    });
  });

  it('falls back to the raw code for unknown keys', () => {
    expect(formatSubmitValidation('frep.submit.something.else:9')).toEqual({
      title: 'Validation',
      detail: 'frep.submit.something.else:9',
    });
  });
});
