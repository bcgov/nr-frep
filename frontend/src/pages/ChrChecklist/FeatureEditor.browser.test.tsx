import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import FeatureEditor from './FeatureEditor';

import type { Feature } from '@/types/chrChecklist';

import { autofillableCount, stillAutofillable } from '@/testing/autofill';

const baseFeature = (over: Partial<Feature> = {}): Feature =>
  ({ featureLabel: 'Feature 1', ...over }) as Feature;

describe('FeatureEditor — composite-feature help', () => {
  it('renders the "What is a composite feature?" disclosure with its explanation', () => {
    render(<FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />);

    expect(screen.getByText('What is a composite feature?')).toBeTruthy();
    expect(
      screen.getByText(/group of two or more associated cultural heritage features/i),
    ).toBeTruthy();
    expect(screen.getByText(/cultural trail and an adjacent berry harvesting area/i)).toBeTruthy();
  });
});

describe('FeatureEditor — Age single-select', () => {
  it('disables the other ages once one is selected', async () => {
    const onPatch = vi.fn();
    // Feature already has Post-1846 selected.
    render(
      <FeatureEditor
        feature={baseFeature({ post1846: 'true' })}
        onPatch={onPatch}
        readOnly={false}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Age' }));

    // The selected age stays enabled; the other three are disabled until it is unchecked.
    expect((screen.getByRole('checkbox', { name: 'Post-1846' }) as HTMLInputElement).disabled).toBe(
      false,
    );
    expect((screen.getByRole('checkbox', { name: 'Pre-1846' }) as HTMLInputElement).disabled).toBe(
      true,
    );
    expect(
      (screen.getByRole('checkbox', { name: 'Age unknown' }) as HTMLInputElement).disabled,
    ).toBe(true);
    expect(
      (screen.getByRole('checkbox', { name: 'Historical use' }) as HTMLInputElement).disabled,
    ).toBe(true);
  });

  it('enables all ages when none is selected', async () => {
    const onPatch = vi.fn();
    render(<FeatureEditor feature={baseFeature()} onPatch={onPatch} readOnly={false} />);

    await userEvent.click(screen.getByRole('button', { name: 'Age' }));

    for (const name of ['Pre-1846', 'Post-1846', 'Age unknown', 'Historical use']) {
      expect((screen.getByRole('checkbox', { name }) as HTMLInputElement).disabled).toBe(false);
    }

    // Selecting one just sets that field; the others are disabled on the next render.
    await userEvent.click(screen.getByRole('checkbox', { name: 'Age unknown' }));
    expect(onPatch).toHaveBeenCalledWith({ ageUnknown: 'true' });
  });

  it('unchecking the active age clears it (re-enabling the group)', async () => {
    const onPatch = vi.fn();
    render(
      <FeatureEditor
        feature={baseFeature({ post1846: 'true' })}
        onPatch={onPatch}
        readOnly={false}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Age' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Post-1846' }));

    expect(onPatch).toHaveBeenCalledWith({ post1846: 'false' });
  });
});

describe('FeatureEditor — Summary section stays open', () => {
  const summaryButton = () => screen.getByRole('button', { name: /Summary/ });

  it('does not collapse the Summary section when the required Feature rating is filled', async () => {
    const onPatch = vi.fn();
    // A bare feature has no Feature rating → a Summary error → once errors are shown (i.e. after a
    // save attempt) the section auto-opens.
    const { rerender } = render(
      <FeatureEditor feature={baseFeature()} onPatch={onPatch} readOnly={false} showErrors />,
    );
    await waitFor(() => expect(summaryButton()).toHaveAttribute('aria-expanded', 'true'));

    // Parent applies the rating (controlled component) — the error clears.
    rerender(
      <FeatureEditor
        feature={baseFeature({ featureRating: '2' })}
        onPatch={onPatch}
        readOnly={false}
        showErrors
      />,
    );

    // The section must remain open (sticky), not snap shut now that the error is gone.
    await waitFor(() => expect(summaryButton()).toHaveAttribute('aria-expanded', 'true'));
  });

  it('leaves the Summary section closed before a save is attempted', async () => {
    // Opening a feature must not greet the user with auto-opened sections and error badges for
    // fields they have not been asked to fill yet — errors surface only on Save (showErrors).
    render(<FeatureEditor feature={baseFeature()} onPatch={vi.fn()} readOnly={false} />);

    await waitFor(() => expect(summaryButton()).toHaveAttribute('aria-expanded', 'false'));
    expect(screen.queryByText('Feature rating is required.')).toBeNull();
  });
});

describe('FeatureEditor — Composite of (sibling-label dropdown)', () => {
  it('renders "Composite of" as a dropdown of the sibling features for a composite feature', () => {
    render(
      <FeatureEditor
        feature={baseFeature({ compositeFeatureInd: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
        siblingLabels={['2', '3']}
      />,
    );

    const select = screen.getByLabelText('Composite of (feature label)');
    expect(select.tagName).toBe('SELECT'); // dropdown, not a free-text input
    expect(screen.getByRole('option', { name: 'Feature 2' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Feature 3' })).toBeTruthy();
  });

  it('excludes siblings already in a composite (uses compositeCandidateLabels)', () => {
    render(
      <FeatureEditor
        feature={baseFeature({ compositeFeatureInd: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
        siblingLabels={['2', '3']}
        compositeCandidateLabels={['2']} // Feature 3 is already in a composite → not a candidate
      />,
    );

    expect(screen.getByRole('option', { name: 'Feature 2' })).toBeTruthy();
    expect(screen.queryByRole('option', { name: 'Feature 3' })).toBeNull();
  });

  it('selecting a sibling patches compositeFeature with the raw label', async () => {
    const onPatch = vi.fn();
    render(
      <FeatureEditor
        feature={baseFeature({ compositeFeatureInd: 'true' })}
        onPatch={onPatch}
        readOnly={false}
        siblingLabels={['2', '3']}
      />,
    );

    await userEvent.selectOptions(screen.getByLabelText('Composite of (feature label)'), '3');
    expect(onPatch).toHaveBeenCalledWith({ compositeFeature: '3' });
  });

  it('hides the composite control and shows a hint when there are no other features', () => {
    render(
      <FeatureEditor
        feature={baseFeature()}
        onPatch={vi.fn()}
        readOnly={false}
        siblingLabels={[]}
      />,
    );

    expect(screen.queryByRole('checkbox', { name: 'Composite feature' })).toBeNull();
    expect(screen.getByText('Add another feature to create a composite.')).toBeTruthy();
  });

  it('keeps the composite checkbox for an already-composite feature even with no siblings', () => {
    // A stale composite (its siblings were deleted) must still be editable so it can be undone.
    render(
      <FeatureEditor
        feature={baseFeature({ compositeFeatureInd: 'true' })}
        onPatch={vi.fn()}
        readOnly={false}
        siblingLabels={[]}
      />,
    );

    expect(screen.getByRole('checkbox', { name: 'Composite feature' })).toBeTruthy();
    expect(screen.queryByText('Add another feature to create a composite.')).toBeNull();
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

    // Open every section, so collapsed fields are checked too.
    for (const button of screen.getAllByRole('button')) {
      if (button.getAttribute('aria-expanded') === 'false') await userEvent.click(button);
    }

    expect(autofillableCount()).toBeGreaterThan(5);
    expect(stillAutofillable()).toEqual([]);
  });
});
