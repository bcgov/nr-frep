import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { page } from '@vitest/browser/context';
import { describe, expect, it, vi } from 'vitest';

// Geometry, so the real stylesheets have to be loaded — a browser spec loads none by default.
import '@/styles/index.scss';
import '@/pages/ProtocolChecklist/protocolChecklist.scss';
import './chrChecklist.scss';

import OpeningInformation from './OpeningInformation';

import type { CheckList } from '@/types/chrChecklist';

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ user: { userName: 'IDIR\\TESTER' } }),
}));

describe('OpeningInformation — Targeted site alignment', () => {
  it('sits the checkbox on the inputs’ line, not on their labels’', async () => {
    await page.viewport(1400, 700);
    render(
      <OpeningInformation
        value={{ evaluationDate: '2026-08-26' } as CheckList}
        onSave={vi.fn()}
        readOnly={false}
        busy={false}
      />,
    );
    const edit = [...document.querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Edit'),
    );
    if (edit) await userEvent.click(edit);

    const centre = (el: Element) => {
      const r = el.getBoundingClientRect();
      return Math.round(r.top + r.height / 2);
    };
    const input = document.querySelector('#chr-first-nation')!;
    const checkbox = document.querySelector('#chr-targeted')!.closest('.cds--checkbox-wrapper')!;

    // A checkbox carries no label above it, so on this top-aligned grid it floated a label's height
    // clear of the fields beside it — level with "First Nations' Place Name" rather than its box.
    expect(centre(checkbox)).toBe(centre(input));
  });
});
