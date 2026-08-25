import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import FeatureEditor from './FeatureEditor';

import type { Feature } from '@/types/chrChecklist';

const baseFeature = (over: Partial<Feature> = {}): Feature =>
  ({ featureLabel: 'Feature 1', ...over }) as Feature;

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
