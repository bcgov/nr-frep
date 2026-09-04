import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { page } from '@vitest/browser/context';
import { describe, expect, it, vi } from 'vitest';

// Geometry, so the real stylesheets have to be loaded — a browser spec loads none by default.
import '@/styles/index.scss';
import '@/pages/ProtocolChecklist/protocolChecklist.scss';
import './chrChecklist.scss';

import BlockSummary from './BlockSummary';

import type { CheckList } from '@/types/chrChecklist';

vi.mock('@/services/APIs', async () => {
  const { chrCodeListApi } = await import('@/testing/chrCodeListApi');
  return { default: { configuration: chrCodeListApi() } };
});

const answered = {
  q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock: 'true',
  q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues:
    'true',
  q8Comments: 'a',
  q9Comments: 'b',
} as unknown as CheckList;

describe('BlockSummary — Q8/Q9/Q10 layout', () => {
  it('puts each description under its own question, not across the page from it', async () => {
    await page.viewport(1300, 1400);
    render(<BlockSummary value={answered} onSave={vi.fn()} readOnly={false} busy={false} />);
    const edit = [...document.querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Edit'),
    );
    if (edit) await userEvent.click(edit);

    const rect = (sel: string) => document.querySelector(sel)!.getBoundingClientRect();
    const q8 = rect('#chr-q8');
    const d8 = rect('#chr-q8-comments');
    const q9 = rect('#chr-q9');

    // One column: the description follows its question and precedes the next one. These used to sit
    // side by side, with an empty cell opposite every unticked question.
    expect(d8.top).toBeGreaterThan(q8.top);
    expect(q9.top).toBeGreaterThan(d8.bottom);

    // A description belongs to the question above it, so it sits closer to its own question than
    // the questions sit to each other.
    expect(d8.top - q8.bottom).toBeLessThan(q9.top - d8.bottom);
  });

  it('starts the MRVA derivation table at the row edge, not inside a 12rem column', async () => {
    await page.viewport(1300, 1400);
    render(<BlockSummary value={answered} onSave={vi.fn()} readOnly={false} busy={false} />);
    const edit = [...document.querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Edit'),
    );
    if (edit) await userEvent.click(edit);
    (document.querySelector('.chr-mrva-help') as HTMLDetailsElement).open = true;

    const grid = document.querySelector('.rip-form__grid')!.getBoundingClientRect();
    const help = document.querySelector('.chr-mrva-help')!.getBoundingClientRect();
    const rating = document.querySelector('#chr-block-rating')!.getBoundingClientRect();

    // It holds a 32rem table and the grid caps its tracks at 12rem, so it spans the row rather than
    // sitting in the MRVA cell — where it began halfway across the page and wrapped every column.
    expect(Math.round(help.left)).toBe(Math.round(grid.left));
    // Wide enough for the 32rem table it holds — a single 12rem track is 192px.
    expect(help.width).toBeGreaterThan(32 * 16);
    expect(help.top).toBeGreaterThan(rating.bottom);
  });
});
