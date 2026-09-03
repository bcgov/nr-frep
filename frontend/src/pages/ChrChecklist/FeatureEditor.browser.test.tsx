import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import FeatureEditor from './FeatureEditor';
import { clearCodeListCache } from './useCodeList';

import type { Feature } from '@/types/chrChecklist';

import { autofillableCount, stillAutofillable } from '@/testing/autofill';

// The dropdowns come from the code tables now, so anything mounting a form needs them stubbed.
vi.mock('@/services/APIs', async () => {
  const { chrCodeListApi } = await import('@/testing/chrCodeListApi');
  return { default: { configuration: chrCodeListApi() } };
});

beforeEach(() => clearCodeListCache());

const baseFeature = (over: Partial<Feature> = {}): Feature =>
  ({ featureLabel: 'Feature 1', ...over }) as Feature;

describe('FeatureEditor — Age single-select', () => {
  it('shows the age as one question rather than four boxes', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ post1846: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    // A feature has one age, so it is asked once. The four checkboxes this replaced had to disable
    // each other, and switching age meant unticking the old one first.
    expect(screen.getByText('Select age for this feature')).toBeTruthy();
    expect(screen.queryByRole('checkbox', { name: 'Pre-1846' })).toBeNull();
    expect((screen.getByRole('radio', { name: 'Post-1846' }) as HTMLInputElement).checked).toBe(
      true,
    );
    for (const name of ['Pre-1846', 'Age unknown', 'Historical use']) {
      expect((screen.getByRole('radio', { name }) as HTMLInputElement).disabled).toBe(false);
    }
  });

  it('moves the selection in one step, clearing the age it replaces', async () => {
    const onPatch = vi.fn();
    render(
      <FeatureEditor
        feature={baseFeature({ post1846: 'true' })}
        onPatch={onPatch}
        readOnly={false}
      />,
    );

    await userEvent.click(screen.getByRole('radio', { name: 'Age unknown' }));

    // Every indicator is written, so the old one cannot be left behind as a second true.
    expect(onPatch).toHaveBeenCalledWith({
      pre1846: 'false',
      post1846: 'false',
      ageUnknown: 'true',
      historicalUse: 'false',
    });
  });

  it('starts with no age chosen', async () => {
    render(<FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />);

    for (const name of ['Pre-1846', 'Post-1846', 'Age unknown', 'Historical use']) {
      expect((screen.getByRole('radio', { name }) as HTMLInputElement).checked).toBe(false);
    }
  });
});

describe('FeatureEditor — composites', () => {
  it('does not offer to make a feature a composite', () => {
    // Composites are built, edited and dissolved from the feature table. Leaving a second way in
    // here would let a feature be flagged a composite with no members, which fails submit against
    // itself ("must include at least two features").
    render(<FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />);

    expect(screen.queryByRole('checkbox', { name: 'Composite feature' })).toBeNull();
    expect(screen.queryByLabelText('Composite of (feature label)')).toBeNull();
  });

  it('does not explain composites here — that belongs where they are created', () => {
    render(<FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />);
    expect(screen.queryByText('What is a composite feature?')).toBeNull();
    expect(screen.queryByText(/culturally, spatially, or functionally connected/)).toBeNull();
  });
});

describe('FeatureEditor — browser autofill', () => {
  /**
   * Every feature field keeps a stable `id`, so without this the browser treats the next feature's
   * field as one it has seen before and offers what was typed on the last one — and accepting a
   * single suggestion cascades into the rest of the group it infers. These are per-feature
   * evaluation values, so a repeat of the previous feature is always wrong.
   *
   * Covers the shared builders in fields.tsx, which is where every CHR field is rendered.
   */
  it('leaves no field for the browser to autofill from the previous feature', async () => {
    render(<FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />);

    // Every section is on the page at once, so there is nothing to expand before checking.
    expect(autofillableCount()).toBeGreaterThan(5);
    expect(stillAutofillable()).toEqual([]);
  });
});

describe('FeatureEditor — required markers', () => {
  /** The asterisk `requiredLabel` appends, read off the rendered label. */
  const marked = (label: string): boolean =>
    Array.from(document.querySelectorAll('label, .cds--label')).some(
      (el) => el.textContent?.startsWith(label) && el.querySelector('.required-asterisk') != null,
    );

  it('marks the fields the submit rules ask a plain feature for', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ compositeFeatureInd: 'false' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    await waitFor(() => expect(marked('Feature label')).toBe(true));
    expect(marked('Feature class')).toBe(true);
    expect(marked('Information source')).toBe(true);
    // Neither of these is owed at submit, so neither is marked: a description has only a length
    // limit, and Borden is only format-checked once it has a value.
    expect(marked('Feature description')).toBe(false);
    expect(marked('Borden number')).toBe(false);
  });

  it('marks the type group rather than any one box, and says what it wants', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ compositeFeatureInd: 'false' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    // Submit asks for "at least one type of feature" — a requirement on the group, which no single
    // checkbox can satisfy or fail, so the mark belongs on the legend.
    const legend = await waitFor(() =>
      Array.from(document.querySelectorAll('legend')).find((l) =>
        l.textContent?.startsWith('Type of feature(s)'),
      ),
    );
    expect(legend).toBeTruthy();
    expect(legend!.querySelector('.required-asterisk')).toBeTruthy();
    expect(screen.getByText('Select at least one.')).toBeTruthy();
  });

  it('does not ask a composite for the two codes it is never validated on', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ compositeFeatureInd: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    // A composite is described through its members; the submit rule skips both codes, so the form
    // must not promise an error that never comes.
    await waitFor(() => expect(marked('Feature label')).toBe(true));
    expect(marked('Feature class')).toBe(false);
    expect(marked('Information source')).toBe(false);
  });
});

describe('FeatureEditor — Planning sources', () => {
  const headers = (): (string | null)[] =>
    Array.from(document.querySelectorAll('.chr-checklist__planning th')).map(
      (th) => th.textContent,
    );

  it('shows no strategy table until a source is named', async () => {
    render(<FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />);

    // The table asks "who recommended this?", which has no answer before a box is ticked.
    expect(document.querySelector('.chr-checklist__planning')).toBeNull();
    expect(screen.getByText('Applies to this feature')).toBeTruthy();
  });

  it('carries a column only for the sources that apply', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ managementStrategyFN: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    // AIA used to stand on every feature regardless, asking for strategies from an assessment that
    // may never have happened.
    expect(headers()).toEqual(['Strategy', 'FN']);
  });

  it('adds each source’s column as it is named', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({
          managementStrategyFN: 'true',
          managementStrategySP: 'true',
          sitePermitIssued: 'true',
        })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    expect(headers()).toEqual(['Strategy', 'FN', 'AIA/SAP', 'Site plan']);
  });

  it('clears everything recorded under a source when it is unticked', async () => {
    const onPatch = vi.fn();
    render(
      <FeatureEditor
        feature={baseFeature({
          managementStrategyFN: 'true',
          modifyBlockBoundaryFN: 'true',
          retainBufferFN: 'true',
          bufferLengthFN: '25',
          otherPlannedManagementStrategy: [
            { otherStrategy: 'Fenced', fnInd: 'true', aiaInd: 'true', spInd: 'false' },
          ],
        })}
        onPatch={onPatch}
        readOnly={false}
      />,
    );

    await userEvent.click(
      screen.getByRole('checkbox', { name: 'FN management recommendations provided' }),
    );

    // The column goes, so what it held has to go with it — otherwise the values stay stored and
    // counted with nothing on the form able to show or clear them.
    const patch = onPatch.mock.calls.at(-1)?.[0] as Record<string, unknown>;
    expect(patch.managementStrategyFN).toBe('false');
    expect(patch.modifyBlockBoundaryFN).toBe('false');
    expect(patch.retainBufferFN).toBe('false');
    expect(patch.bufferLengthFN).toBe('');
    // The free-text strategies are shared across columns: the row survives, this column's tick does not.
    expect(patch.otherPlannedManagementStrategy).toEqual([
      { otherStrategy: 'Fenced', fnInd: 'false', aiaInd: 'true', spInd: 'false' },
    ]);
  });

  it('clears the permit number when the AIA source is unticked', async () => {
    const onPatch = vi.fn();
    render(
      <FeatureEditor
        feature={baseFeature({ sitePermitIssued: 'true', permit: 'ABC-123' })}
        onPatch={onPatch}
        readOnly={false}
      />,
    );

    await userEvent.click(
      screen.getByRole('checkbox', { name: 'AIA / site-alteration permit issued' }),
    );

    const patch = onPatch.mock.calls.at(-1)?.[0] as Record<string, unknown>;
    expect(patch.sitePermitIssued).toBe('false');
    expect(patch.permit).toBe('');
  });

  it('asks for a permit number, marked required, once the AIA box is ticked', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ sitePermitIssued: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    const label = Array.from(document.querySelectorAll('label')).find((el) =>
      el.textContent?.startsWith('Permit number'),
    );
    expect(label).toBeTruthy();
    expect(label?.querySelector('.required-asterisk')).not.toBeNull();
  });
});

describe('FeatureEditor — Additional management strategies', () => {
  const oneRow = (over: Partial<Feature> = {}): Partial<Feature> => ({
    otherPlannedManagementStrategy: [
      { otherStrategy: '', fnInd: 'false', aiaInd: 'false', spInd: 'false' },
    ],
    ...over,
  });

  const headers = (): (string | null)[] =>
    Array.from(document.querySelectorAll('.chr-checklist__additional th')).map(
      (th) => th.textContent,
    );

  const sources = (): (string | null)[] =>
    Array.from(
      document.querySelectorAll('.chr-checklist__additional-sources .cds--checkbox-label-text'),
    ).map((el) => el.textContent);

  it('lists a strategy as Name, Source and Action', async () => {
    render(
      <FeatureEditor
        feature={baseFeature(oneRow({ sitePermitIssued: 'true' }))}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    // The sources are one answer about the strategy, not a column each — with three columns, two
    // sat empty on most features.
    expect(headers()).toEqual(['Name *', 'Source *', 'Action']);
  });

  it('offers only the sources that apply to the feature', async () => {
    render(
      <FeatureEditor
        feature={baseFeature(oneRow({ sitePermitIssued: 'true' }))}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    expect(sources()).toEqual(['AIA/SAP']);
  });

  it('offers all three once all three sources apply', async () => {
    render(
      <FeatureEditor
        feature={baseFeature(
          oneRow({
            managementStrategyFN: 'true',
            managementStrategySP: 'true',
            sitePermitIssued: 'true',
          }),
        )}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    expect(sources()).toEqual(['FN', 'AIA/SAP', 'Site plan']);
  });

  it('says what belongs here while empty, and states the rule once it is not', async () => {
    const { rerender } = render(
      <FeatureEditor
        feature={baseFeature({ sitePermitIssued: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    expect(screen.getByText(/None added\. Add any management strategy/)).toBeTruthy();

    rerender(
      <FeatureEditor
        feature={baseFeature(oneRow({ sitePermitIssued: 'true' }))}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    expect(screen.getByText('Select at least one source for each strategy.')).toBeTruthy();
  });
});

describe('FeatureEditor — duplicate feature label', () => {
  it('flags a label another feature already uses', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ featureLabel: '2' })}
        onPatch={vi.fn()}
        readOnly={false}
        showErrors
        takenLabels={['1', '2', '3']}
      />,
    );

    // CHFID_UK is UNIQUE (CHR_CHECKLIST_ID, FEATURE_LABEL): without this the save round-trips and
    // comes back as a failed save, after the rest of the feature has been filled in.
    expect(await screen.findByText('Already used by another feature.')).toBeTruthy();
  });

  it('flags the clash without waiting for a save attempt', async () => {
    // Every other field error waits for Save (showErrors). A duplicate label is a clash with
    // another record, and the user is typing the very value that clashes.
    render(
      <FeatureEditor
        feature={baseFeature({ featureLabel: '2' })}
        onPatch={vi.fn()}
        readOnly={false}
        takenLabels={['2']}
      />,
    );

    expect(await screen.findByText('Already used by another feature.')).toBeTruthy();
  });

  it('does not flag a feature against its own label', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ featureLabel: '2' })}
        onPatch={vi.fn()}
        readOnly={false}
        showErrors
        takenLabels={['1', '3']}
      />,
    );

    expect(screen.queryByText('Already used by another feature.')).toBeNull();
  });

  it('treats labels differing only in case as the same', async () => {
    // Stricter than the constraint on purpose: Oracle would allow "A" and "a", but composite
    // membership matches labels case-insensitively, so the pair would be indistinguishable.
    render(
      <FeatureEditor
        feature={baseFeature({ featureLabel: 'a' })}
        onPatch={vi.fn()}
        readOnly={false}
        showErrors
        takenLabels={['A']}
      />,
    );

    expect(await screen.findByText('Already used by another feature.')).toBeTruthy();
  });
});

describe('FeatureEditor — windthrow treatment', () => {
  const withWindthrow = (over: Partial<Feature> = {}) =>
    baseFeature({ windthrowManagement: 'true', ...over });

  it('clears and closes the other techniques when None is ticked', async () => {
    const user = userEvent.setup();
    const onPatch = vi.fn();
    render(
      <FeatureEditor
        feature={withWindthrow({ windthrowTechniquePruning: 'true', otherTechnique: 'true' })}
        onPatch={onPatch}
        readOnly={false}
      />,
    );

    await user.click(screen.getByRole('checkbox', { name: 'None' }));

    // A saved feature must not say both "no treatment" and "pruning", so ticking None wipes what
    // was recorded — the free-text description included, since the box that owned it is gone too.
    const patch = onPatch.mock.calls.at(-1)?.[0] as Record<string, string>;
    expect(patch.windthrowTechniqueNone).toBe('true');
    expect(patch.windthrowTechniquePruning).toBe('false');
    expect(patch.otherTechnique).toBe('false');
    expect(patch.ifotherpleasedescribe).toBe('');
  });

  it('holds the other techniques closed while None is ticked', async () => {
    render(
      <FeatureEditor
        feature={withWindthrow({ windthrowTechniqueNone: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    expect(
      (screen.getByRole('checkbox', { name: 'Pruning' }) as HTMLInputElement).disabled,
    ).toBe(true);
    expect(
      (screen.getByRole('checkbox', { name: 'Other technique' }) as HTMLInputElement).disabled,
    ).toBe(true);
    // None stays live, or there would be no way back out of the choice.
    expect((screen.getByRole('checkbox', { name: 'None' }) as HTMLInputElement).disabled).toBe(
      false,
    );
  });

  it('leaves the techniques open when None is not ticked', () => {
    render(<FeatureEditor feature={withWindthrow()} onPatch={vi.fn()} readOnly={false} />);
    expect(
      (screen.getByRole('checkbox', { name: 'Pruning' }) as HTMLInputElement).disabled,
    ).toBe(false);
  });
});

describe('FeatureEditor — questions that reveal their fields', () => {
  it('asks the registered-site question with an explicit No, and reveals Borden only on Yes', async () => {
    const onPatch = vi.fn();
    render(<FeatureEditor feature={baseFeature()} onPatch={onPatch} readOnly={false} />);

    // Unanswered: neither radio is chosen and nothing is revealed. An unticked box could not say
    // whether the site is unregistered or the question was skipped.
    expect(screen.getByRole('radio', { name: 'Yes' })).toBeTruthy();
    expect((screen.getByRole('radio', { name: 'No' }) as HTMLInputElement).checked).toBe(false);
    expect(screen.queryByLabelText(/Borden number/)).toBeNull();

    await userEvent.click(screen.getByRole('radio', { name: 'Yes' }));
    expect(onPatch).toHaveBeenCalledWith({ chrRegisteredSite: 'true' });
  });

  it('shows the Borden field once the answer is Yes', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ chrRegisteredSite: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    expect(screen.getByLabelText(/Borden number/)).toBeTruthy();
  });

  it('opens no size fields until the unit has been chosen', async () => {
    render(<FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />);

    // Defaulting to metres put an empty Width and Length on every new feature, which reads as two
    // fields owed rather than a question not yet answered.
    expect(screen.queryByLabelText('Width (m)')).toBeNull();
    expect(screen.queryByLabelText('Length (m)')).toBeNull();
    expect(screen.queryByLabelText('Area (ha)')).toBeNull();

    await userEvent.click(screen.getByRole('radio', { name: 'Metres (width × length)' }));
    expect(screen.getByLabelText('Width (m)')).toBeTruthy();
    expect(screen.getByLabelText('Length (m)')).toBeTruthy();
  });

  it('opens the hectares field for a feature already recorded in hectares', async () => {
    render(
      <FeatureEditor
        feature={baseFeature({ areaofFeature: '2.5' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    expect(screen.getByLabelText('Area (ha)')).toBeTruthy();
    expect(screen.queryByLabelText('Width (m)')).toBeNull();
  });
});

describe('FeatureEditor — Effectiveness follow-ups', () => {
  it('asks for a reserve type only once its strategy is ticked', async () => {
    const { rerender } = render(
      <FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />,
    );
    expect(screen.queryByLabelText(/Full temporary reserve type/)).toBeNull();

    rerender(
      <FeatureEditor
        feature={baseFeature({ fullyconservedintemporaryreserve: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    expect(screen.getByLabelText(/Full temporary reserve type/)).toBeTruthy();
  });

  it('treats "Other activities" as a tick over its own free text', async () => {
    const onPatch = vi.fn();
    render(
      <FeatureEditor
        feature={baseFeature({ otherActivities: 'Fenced' })}
        onPatch={onPatch}
        readOnly={false}
      />,
    );

    // There is no indicator column for it — the strategy is stored as an OTH row carrying the
    // description, so text present is the tick.
    const box = screen.getByRole('checkbox', { name: 'Other activities' }) as HTMLInputElement;
    expect(box.checked).toBe(true);
    expect(screen.getByLabelText(/Description/)).toBeTruthy();

    // Unticking clears the text, which is what removes the row.
    await userEvent.click(box);
    expect(onPatch).toHaveBeenCalledWith({ otherActivities: '' });
  });
});

describe('FeatureEditor — numbers the column can hold', () => {
  /**
   * Every one of these used to be reported by the database rather than the form. The percentages are
   * `NUMBER(3)` columns parsed to a Short on save, the size-of-area fields `NUMBER(8,2)`/`NUMBER(10,4)`
   * parsed to a BigDecimal; a value neither could take came back as a failed save carrying the JVM's
   * own message — `For input string: "tset"` — with nothing marked on the field that caused it.
   */
  const showing = (over: Partial<Feature>) =>
    render(
      <FeatureEditor feature={baseFeature(over)} onPatch={vi.fn()} readOnly={false} showErrors />,
    );

  it('names a non-numeric windthrow estimate on the field', () => {
    showing({ windthrowManagement: 'true', windthrow: 'false', estwindthrow: 'lots' });

    expect(screen.getByText('Estimated windthrow (%) must be a whole number.')).toBeTruthy();
  });

  it('holds a percentage to 0–100', () => {
    // The column would take three digits; a percentage still cannot be 500.
    showing({ trailfeatures: 'true', isthereevidenceofdamage: 'true', trailLength: '500' });

    expect(screen.getByText('Estimated trail damage (%) must be at most 100.')).toBeTruthy();
  });

  it('keeps an area inside the precision of its column', () => {
    showing({ areaofFeature: '12345678.5' });

    expect(
      screen.getByText('Area (ha) must have at most 6 digits before the decimal point.'),
    ).toBeTruthy();
  });

  it('leaves a decimal the column does carry alone', () => {
    showing({ areaofFeature: '2.5' });

    expect(screen.queryByText(/^Area \(ha\) must/)).toBeNull();
  });
});

describe('FeatureEditor — before a save has been attempted', () => {
  /**
   * The editor opens quiet, but not silent: a value the column cannot hold is wrong the moment it is
   * typed, and saying so only at Save sends the user back to a field they had finished with. What
   * still waits is everything a correct entry passes through — blanks, minimums, half-typed patterns.
   */
  const quiet = (over: Partial<Feature>) =>
    render(<FeatureEditor feature={baseFeature(over)} onPatch={vi.fn()} readOnly={false} />);

  it('marks a value the column cannot hold straight away', () => {
    quiet({ trailfeatures: 'true', isthereevidenceofdamage: 'true', trailLength: 'tset' });

    expect(screen.getByText('Estimated trail damage (%) must be a whole number.')).toBeTruthy();
  });

  it('says nothing about a required field left blank', () => {
    quiet({ trailfeatures: 'true', isthereevidenceofdamage: 'true', trailLength: '' });

    // The label still reads "Estimated trail damage (%)" — it is the message after it that must not
    // be there yet.
    expect(screen.queryByText(/Estimated trail damage \(%\) (?:must|is)/)).toBeNull();
    expect(screen.queryByText('A rating is required.')).toBeNull();
  });
});

describe('FeatureEditor — errors when a field is left', () => {
  /**
   * Borden is the CHR rule a correct value trips on its way in — "Aa", "AaBb", "AaBb-" are all
   * steps towards a valid number — so it is judged once the field has been left rather than on
   * every keystroke. See utils/validation.ts.
   */
  /** The editor is controlled, so typing only reaches it through a harness that holds the feature. */
  const Editing = ({ initial }: { initial: Partial<Feature> }) => {
    const [feature, setFeature] = useState<Feature>(baseFeature(initial));
    return (
      <FeatureEditor
        feature={feature}
        onPatch={(patch) => setFeature((prev) => ({ ...prev, ...patch }))}
        readOnly={false}
      />
    );
  };

  it('holds a half-typed Borden number until the field is left', async () => {
    render(<Editing initial={{ chrRegisteredSite: 'true' }} />);

    const borden = screen.getByLabelText(/Borden number/);
    await userEvent.type(borden, 'AaBb');
    expect(screen.queryByText('Must match the Borden format, e.g. AaBb-0000.')).toBeNull();

    await userEvent.tab();
    expect(await screen.findByText('Must match the Borden format, e.g. AaBb-0000.')).toBeTruthy();
  });

  it('says nothing about a Borden field left blank', async () => {
    render(<Editing initial={{ chrRegisteredSite: 'true' }} />);

    await userEvent.click(screen.getByLabelText(/Borden number/));
    await userEvent.tab();

    expect(screen.queryByText('Must match the Borden format, e.g. AaBb-0000.')).toBeNull();
  });
});
