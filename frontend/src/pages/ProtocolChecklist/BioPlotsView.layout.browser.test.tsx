import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { page } from '@vitest/browser/context';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// Geometry, so the real stylesheets have to be loaded. Kept in its own file: with Carbon's CSS
// applied, elements the other specs query by role are laid out (and hidden) differently.
import '@/styles/index.scss';
import './protocolChecklist.scss';

import BioPlotsView from './BioPlotsView';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      listBioStrata: vi.fn().mockResolvedValue([{ stratumId: 'S1', stratumNumber: '1' }]),
      listBioPlots: vi.fn().mockResolvedValue([{ plotId: 'P1', plotNumber: '1', stratumId: 'S1' }]),
      getBioPlot: vi.fn(),
      saveBioPlot: vi.fn(),
      deleteBioPlot: vi.fn(),
      getBiodiversityOpening: vi.fn().mockResolvedValue({ teamLeadNameId: 'JDOE' }),
    },
    configuration: {
      getSpecies: vi.fn().mockResolvedValue([]),
      getWildlifeTreeDecay: vi.fn().mockResolvedValue([]),
      getCwdDecay: vi.fn().mockResolvedValue([]),
      getStrataTypes: vi.fn().mockResolvedValue([{ code: 'DO', description: 'DO - Dispersed' }]),
    },
  },
}));

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { userName: 'TESTER', provider: 'idir' } }),
}));

describe('BioPlotsView — inline error layout', () => {
  beforeEach(async () => {
    document.documentElement.setAttribute('data-carbon-theme', 'white');
    // Narrow enough that the message wraps and the grid stacks — the reported case.
    await page.viewport(760, 900);
  });

  /**
   * Carbon positions `.cds--form-requirement` absolutely, so an inline error adds no height and the
   * field's row never grows for it. A one-line message hides in the grid's row gap; a wrapped one
   * printed its second line over the next row's label. protocolChecklist.scss puts it back in flow.
   *
   * Asserted on geometry rather than on a class name: the markup is identical either way.
   */
  it('does not print a wrapped error over the next row', async () => {
    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.type(await screen.findByLabelText('Plot #', { exact: false }), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    const error = await screen.findByText(/already exists in this stratum/);
    const box = error.getBoundingClientRect();
    // It has to actually be the wrapped case, or this proves nothing.
    expect(box.height).toBeGreaterThan(20);

    const collisions = Array.from(document.querySelectorAll('label'))
      .filter((label) => {
        const l = label.getBoundingClientRect();
        return l.top < box.bottom && l.bottom > box.top && l.left < box.right && l.right > box.left;
      })
      .map((label) => label.textContent);
    expect(collisions).toEqual([]);

    // The field grew to hold the message rather than overflowing it.
    const item = error.closest('.cds--form-item') as HTMLElement;
    expect(item.scrollHeight).toBe(item.clientHeight);
    void API;
  });

  /**
   * The grid used to be `align-items: end`. That was invisible while the inline errors were
   * absolutely positioned — every cell was then the same height — but once they took real height,
   * one field's two-line error dragged its neighbours' labels and inputs down the row.
   */
  it('keeps every label in a row on the same line while one field shows a wrapped error', async () => {
    await page.viewport(1500, 900);
    render(<BioPlotsView checklistId="9001" canEdit submitted={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Add plot' }));
    await userEvent.type(await screen.findByLabelText('Plot #', { exact: false }), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    const error = await screen.findByText(/already exists in this stratum/);
    expect(error.getBoundingClientRect().height).toBeGreaterThan(20);

    const grid = document.getElementById('plot-plotNumber')?.closest('.rip-form__grid');
    const cells = Array.from(grid?.children ?? []) as HTMLElement[];
    const firstRowTop = Math.round(cells[0].getBoundingClientRect().top);
    const labelTops = cells
      .filter((cell) => Math.abs(Math.round(cell.getBoundingClientRect().top) - firstRowTop) < 60)
      .map((cell) => cell.querySelector('label, .protocol-checklist__label'))
      .filter((label): label is HTMLElement => label != null)
      .map((label) => Math.round(label.getBoundingClientRect().top));

    // Plot # shares its row with Evaluated by; the coordinates moved to their own row under the
    // "No UTM signal available" box. Two cells is still the case this guards — a wrapped error in
    // one must not push its neighbour's label down.
    expect(labelTops.length).toBeGreaterThanOrEqual(2);
    expect(new Set(labelTops).size).toBe(1);
  });
});
