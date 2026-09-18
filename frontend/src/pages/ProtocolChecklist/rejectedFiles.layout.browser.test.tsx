import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

// Geometry, so the real stylesheets have to be loaded — a browser spec loads none by default.
import '@/styles/index.scss';
import './protocolChecklist.scss';

/**
 * The refused-files panel.
 *
 * Two goes at this. It first shipped with NO styles — a bare `<ul>` under the notification, reading
 * as stray text. Giving the list its own surface then made it look like a SECOND banner stacked
 * under the first. It is one block: the files are bullets inside the warning.
 *
 * It is not a Carbon `InlineNotification`, because that component refuses interactive children and
 * each row carries a Discard.
 */
const Panel = ({ fileName }: { fileName: string }) => (
  <div style={{ inlineSize: '420px' }} data-testid="tab">
    <section className="protocol-checklist__refused" data-testid="panel">
      <svg className="protocol-checklist__refused-icon" width="20" height="20" />
      <div>
        <p className="protocol-checklist__refused-lead">
          <strong>Some files were refused</strong> The server refused these files.
        </p>
        <ul className="protocol-checklist__rejected" data-testid="list">
          <li className="protocol-checklist__rejected-row" data-testid="row">
            <strong>{fileName}</strong>
            <span className="protocol-checklist__rejected-reason">
              Upload rejected: a virus was detected (Eicar-Test-Signature).
            </span>
            <button type="button" className="cds--btn">
              Discard
            </button>
          </li>
        </ul>
      </div>
    </section>
  </div>
);

describe('refused files list', () => {
  it('is ONE block — the files sit inside the warning, not in a second banner below it', async () => {
    const { findByTestId } = render(<Panel fileName="eicar.com.txt" />);
    const panel = await findByTestId('panel');
    const list = await findByTestId('list');

    // The panel owns the surface; the list must not draw its own, or it reads as a second banner.
    expect(getComputedStyle(panel).backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
    expect(getComputedStyle(list).backgroundColor).toBe('rgba(0, 0, 0, 0)');
    expect(panel.contains(list)).toBe(true);
  });

  it('shows the files as bullets indented under the sentence', async () => {
    const { findByTestId } = render(<Panel fileName="eicar.com.txt" />);
    const list = await findByTestId('list');
    const style = getComputedStyle(list);

    expect(style.listStyleType).toBe('disc');
    expect(parseFloat(style.paddingLeft)).toBeGreaterThan(0);
  });

  it('keeps the row inside the tab rather than pushing it wide', async () => {
    // A device-named file plus a reason plus a button does not fit a tablet on one line.
    const { findByTestId } = render(
      <Panel fileName="IMG_20260918_084512_evidence_northeast_corner.jpeg" />,
    );
    const tab = await findByTestId('tab');
    const row = await findByTestId('row');

    expect(row.scrollWidth).toBeLessThanOrEqual(tab.clientWidth);
  });

  it('lets a long unbroken file name wrap', async () => {
    const { findByTestId } = render(
      <Panel fileName="IMG_20260918_084512_evidence_northeast_corner.jpeg" />,
    );
    const row = await findByTestId('row');

    expect(getComputedStyle(row).overflowWrap).toBe('anywhere');
  });
});
