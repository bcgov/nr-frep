import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

// Geometry, so the real stylesheets have to be loaded — a browser spec loads none by default.
import '@/styles/index.scss';
import '@/pages/ProtocolChecklist/protocolChecklist.scss';
import './chrChecklist.scss';

import Photos from './Photos';

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

const baseProps = {
  readOnly: false,
  busy: false,
  active: false,
  onAdd: vi.fn(),
  onDelete: vi.fn(),
  fetchContent: vi.fn(),
  page: 0,
  pageSize: 10,
  totalCount: 1,
  onPageChange: vi.fn(),
};

// Long, and unbreakable as far as wrapping is concerned: neither `_` nor `.` is a break
// opportunity, which is what made this overflow rather than wrap.
const LONG_NAME = '2025_FREP_checklist_rejection_reason.PDF';

describe('Photos — the no-thumbnail placeholder', () => {
  it('keeps a long file name inside its box instead of spilling across the next column', () => {
    render(
      <Photos
        {...baseProps}
        pictures={[
          { id: '11', fileName: LONG_NAME, mimeTypeCode: 'image/pdf', description: 'A permit' },
        ]}
      />,
    );

    const box = document.querySelector('.chr-checklist__thumb--placeholder') as HTMLElement;
    expect(box).toBeTruthy();
    expect(box.textContent).toContain(LONG_NAME);

    // The text's own geometry, not the box's: the box kept its 120px either way, while the line of
    // text ran well past both its edges and over the Description column beside it.
    const range = document.createRange();
    range.selectNodeContents(box);
    const text = range.getBoundingClientRect();
    const rect = box.getBoundingClientRect();

    expect(text.left).toBeGreaterThanOrEqual(rect.left - 1);
    expect(text.right).toBeLessThanOrEqual(rect.right + 1);
  });

  it('leaves the whole name reachable even when the box clips it', () => {
    render(
      <Photos
        {...baseProps}
        pictures={[
          { id: '12', fileName: LONG_NAME, mimeTypeCode: 'image/pdf', description: 'A permit' },
        ]}
      />,
    );

    const box = document.querySelector('.chr-checklist__thumb--placeholder') as HTMLElement;
    expect(box.getAttribute('title')).toBe(LONG_NAME);
  });
});

describe('Photos — column widths with a long description', () => {
  // The SLR attachments table had a short code (Type) collapse to one letter per line beside a long
  // description, because every cell may break `anywhere`. Date is CHR's short column in that slot.
  const lines = (cell: Element): number => {
    const range = document.createRange();
    range.selectNodeContents(cell);
    // One rect per line box the text occupies.
    return new Set([...range.getClientRects()].map((r) => Math.round(r.top))).size;
  };

  it.each([['1200px'], ['420px']])('keeps the date readable in a %s tab', (width) => {
    render(
      <div style={{ inlineSize: width }}>
        <Photos
          {...baseProps}
          pictures={[
            {
              id: '11',
              fileName: 'site.jpg',
              mimeTypeCode: 'image/jpeg',
              description: 'Long description text '.repeat(40),
              date: '2026-09-18',
            },
          ]}
        />
      </div>,
    );

    const dateCell = document.querySelectorAll('tbody td')[2];
    expect(lines(dateCell)).toBe(1);
  });
});
