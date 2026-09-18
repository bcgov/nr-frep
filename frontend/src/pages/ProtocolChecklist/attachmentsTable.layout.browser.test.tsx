import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

// Geometry, so the real stylesheets have to be loaded — a browser spec loads none by default.
import '@/styles/index.scss';
import './protocolChecklist.scss';

/**
 * The attachments/photos grid, as both tabs render it.
 *
 * Reproduced here rather than mounting either view: the defect is in the shared
 * `.rip-field-grid--files` + `.rip-table-scroll` styling, and both SLR (RipAttachmentsView) and CHR
 * (Photos) emit exactly this markup. Mounting a view would drag in its data loading and tell us
 * nothing extra about the layout.
 */
const Grid = ({ fileName }: { fileName: string }) => (
  <div style={{ inlineSize: '420px' }} data-testid="tab">
    <div className="rip-table-scroll" data-testid="scroller">
      <table className="rip-field-grid rip-field-grid--files">
        <thead>
          <tr>
            <th scope="col">Preview</th>
            <th scope="col">File</th>
            <th scope="col">Description</th>
            <th scope="col">Type</th>
            <th scope="col">Action</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>—</td>
            <td data-testid="file">{fileName}</td>
            <td>A description of the attached evidence</td>
            <td>PDF</td>
            <td className="table-actions" data-testid="actions">
              <button type="button" className="cds--btn">Download</button>
              <button type="button" className="cds--btn">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
);

// No break opportunity anywhere — the shape that pinned the column open.
const LONG_NAME = 'CHR-evidence-photograph-northeast-corner-2026-09-18-final-v3.pdf';

describe('attachments table layout', () => {
  it('fits the table inside its tab instead of pushing the page wide', async () => {
    const { findByTestId, container } = render(<Grid fileName={LONG_NAME} />);
    const tab = await findByTestId('tab');
    const table = container.querySelector('table') as HTMLTableElement;

    // The TABLE's width is what changed — measuring the wrapper would not discriminate, since a
    // block div is its parent's width whether or not it contains the overflow. With the file name
    // wrapping and the buttons free to stack, the row's minimum width drops under the tab's 420px;
    // before, `nowrap` actions plus an unbreakable file name held it open and the table spilled out,
    // clipping the Download button mid-label.
    expect(table.scrollWidth).toBeLessThanOrEqual(tab.clientWidth);
  });

  it('scrolls inside its own box when the content genuinely cannot fit', async () => {
    const { findByTestId } = render(<Grid fileName={LONG_NAME} />);
    const scroller = await findByTestId('scroller');

    expect(getComputedStyle(scroller).overflowX).toBe('auto');
  });

  it('lets a long unbroken file name wrap rather than widen the column', async () => {
    const { findByTestId } = render(<Grid fileName={LONG_NAME} />);
    const file = await findByTestId('file');

    // `anywhere`, not `break-word`: only `anywhere` also shrinks the cell's min-content width, which
    // is what stops the file name dictating the column width to begin with.
    expect(getComputedStyle(file).overflowWrap).toBe('anywhere');
  });

  it('lets the action buttons stack instead of holding the column open', async () => {
    const { findByTestId } = render(<Grid fileName={LONG_NAME} />);
    const actions = await findByTestId('actions');

    // `.table-actions` sets `nowrap` for the CHR features table, which wants one line. The files
    // grid overrides it — two labelled buttons are wider than the room left at tablet widths.
    expect(getComputedStyle(actions).whiteSpace).toBe('normal');
  });
});
