import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import ClientCombo from './index';

vi.mock('@/services/APIs', () => ({
  default: { search: { searchClients: vi.fn().mockResolvedValue([]) } },
}));

/**
 * These assertions mirror the Playwright locators in e2e/checklist-search.spec.ts and
 * e2e/add-target-site.spec.ts — `getByRole('combobox', { name })` and `getByLabel(name)`. Pinning
 * them here means a change to the label or role is caught in CI rather than by a deployed e2e run.
 */
describe('ClientCombo', () => {
  it('exposes a combobox with its label as the accessible name', () => {
    render(
      <ClientCombo
        id="checklist-search-client"
        titleText="Client name"
        selectedLabel=""
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByRole('combobox', { name: 'Client name' })).toBeTruthy();
    // Carbon labels BOTH the input and the listbox with the same element, so a label-based query
    // matches two nodes — which is a strict-mode violation in Playwright. The e2e specs therefore
    // locate this field by role, not by label; this assertion records why.
    expect(screen.getAllByLabelText('Client name')).toHaveLength(2);
  });

  it('renders the helper text the e2e spec asserts on', () => {
    render(<ClientCombo id="c" titleText="Client" selectedLabel="" onSelect={vi.fn()} />);

    expect(
      screen.getByText('Enter name, acronym, or client number (min. 3 characters)'),
    ).toBeTruthy();
  });

  it('shows the selected client rather than an empty field', () => {
    // The picked client is rarely still in the suggestion list, so the component feeds Carbon a
    // stand-in item carrying the label — without it the field blanks after selection.
    render(
      <ClientCombo
        id="c"
        titleText="Client"
        selectedLabel="ACME LOGGING LTD (ABC) · 00066838"
        onSelect={vi.fn()}
      />,
    );

    expect((screen.getByRole('combobox') as HTMLInputElement).value).toBe(
      'ACME LOGGING LTD (ABC) · 00066838',
    );
  });
});
