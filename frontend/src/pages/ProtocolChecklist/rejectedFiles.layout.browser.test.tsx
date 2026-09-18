import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

// Geometry, so the real stylesheets have to be loaded — a browser spec loads none by default.
import '@/styles/index.scss';
import './protocolChecklist.scss';

/**
 * The refused-files list under its warning banner.
 *
 * It shipped with NO styles at all: a bare `<ul>` with browser bullets and its own indent, sitting
 * unattached under the notification, with Discard floating mid-sentence. It asks the user to destroy
 * bytes that exist nowhere else, so it has to read as part of the warning.
 */
const List = ({ fileName }: { fileName: string }) => (
  <div style={{ inlineSize: '420px' }} data-testid="tab">
    <ul className="protocol-checklist__rejected" data-testid="list">
      <li className="protocol-checklist__rejected-row" data-testid="row">
        <span className="protocol-checklist__rejected-file" data-testid="file">
          <strong>{fileName}</strong>
          <span className="protocol-checklist__rejected-reason">
            Upload rejected: a virus was detected (Eicar-Test-Signature).
          </span>
        </span>
        <button type="button" className="cds--btn">Discard</button>
      </li>
    </ul>
  </div>
);

describe('refused files list', () => {
  it('is a bounded block attached to the banner, not bare text on the page', async () => {
    // Not bullets/indent — Carbon's reset already zeroes those on a `ul`, so asserting them proves
    // nothing. What was missing is the surface: no border, no tint, so the list had no visual
    // relationship to the warning above it and read as debris dropped on the page.
    const { findByTestId } = render(<List fileName="eicar.com.txt" />);
    const list = await findByTestId('list');
    const style = getComputedStyle(list);

    expect(style.borderBottomStyle).toBe('solid');
    expect(style.backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
  });

  it('keeps the row inside the tab rather than pushing it wide', async () => {
    // A device-named file plus a reason plus a button does not fit a tablet on one line.
    const { findByTestId } = render(
      <List fileName="IMG_20260918_084512_evidence_northeast_corner.jpeg" />,
    );
    const tab = await findByTestId('tab');
    const row = await findByTestId('row');

    expect(row.scrollWidth).toBeLessThanOrEqual(tab.clientWidth);
  });

  it('lets a long unbroken file name wrap', async () => {
    const { findByTestId } = render(
      <List fileName="IMG_20260918_084512_evidence_northeast_corner.jpeg" />,
    );
    const file = await findByTestId('file');

    expect(getComputedStyle(file).overflowWrap).toBe('anywhere');
  });
});
