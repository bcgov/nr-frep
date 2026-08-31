import { render, waitFor } from '@testing-library/react';
import { page } from '@vitest/browser/context';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// Geometry, so the real stylesheets have to be loaded — a browser spec loads none by default, and
// without them every measurement reports the unstyled layout. Kept in its own file for the same
// reason as the SLR plots layout spec.
import '@/styles/index.scss';
import '@/pages/ProtocolChecklist/protocolChecklist.scss';
import './chrChecklist.scss';

import FeatureEditor from './FeatureEditor';
import { clearCodeListCache } from './useCodeList';

import type { Feature } from '@/types/chrChecklist';

// The dropdowns come from the code tables now, so anything mounting a form needs them stubbed.
vi.mock('@/services/APIs', async () => {
  const { chrCodeListApi } = await import('@/testing/chrCodeListApi');
  return { default: { configuration: chrCodeListApi() } };
});

beforeEach(() => clearCodeListCache());

const withStrategy = (): Feature =>
  ({
    featureLabel: 'Feature 1',
    managementStrategyFN: 'true',
    managementStrategySP: 'true',
    sitePermitIssued: 'true',
    otherPlannedManagementStrategy: [
      { otherStrategy: '', fnInd: 'false', aiaInd: 'false', spInd: 'false' },
    ],
  }) as Feature;

const midY = (el: Element): number => {
  const r = el.getBoundingClientRect();
  return Math.round(r.top + r.height / 2);
};

describe('FeatureEditor — additional strategies row layout', () => {
  it('sits the name, its sources and Delete on one line', async () => {
    await page.viewport(1250, 700);
    render(<FeatureEditor feature={withStrategy()} onPatch={vi.fn()} readOnly={false} />);

    const row = document.querySelector('.chr-checklist__additional tbody tr');
    expect(row).not.toBeNull();

    const input = row!.querySelector('input[type="text"]')!;
    const checkbox = row!.querySelector('.cds--checkbox-wrapper')!;
    const del = row!.querySelector('button')!;

    // The regression this guards: making the source cell itself `display: flex` stops the td
    // stretching to the row's height, so centring inside it had only the checkboxes' own height to
    // work with and they rode ~9px above the input. The flex row has to live in a child of the cell.
    expect(midY(checkbox)).toBe(midY(input));
    expect(midY(del)).toBe(midY(input));
  });

  it('keeps the sources at their own width rather than spread across the column', async () => {
    await page.viewport(1250, 700);
    render(<FeatureEditor feature={withStrategy()} onPatch={vi.fn()} readOnly={false} />);

    // Carbon's checkbox wrapper is also a `.cds--form-item`, which carries `flex: 1 1 auto` — left
    // alone the three sources grow to fill the cell and drift apart.
    const widths = Array.from(
      document.querySelectorAll('.chr-checklist__additional-sources .cds--checkbox-wrapper'),
    ).map((el) => Math.round(el.getBoundingClientRect().width));
    expect(widths.every((w) => w < 120)).toBe(true);
  });

  it('sizes the table to its contents rather than the panel', async () => {
    await page.viewport(1250, 700);
    render(<FeatureEditor feature={withStrategy()} onPatch={vi.fn()} readOnly={false} />);

    // Stretched to full width, the slack all fell into Source and left a gap between the last
    // checkbox and Action wide enough to read as a missing column.
    const boxes = document.querySelectorAll(
      '.chr-checklist__additional-sources .cds--checkbox-wrapper',
    );
    const last = boxes[boxes.length - 1].getBoundingClientRect();
    const del = document.querySelector('.chr-checklist__additional tbody button')!;
    expect(del.getBoundingClientRect().left - last.right).toBeLessThan(80);
  });
});

describe('FeatureEditor — section layout', () => {
  it('lays every section out on the page, ruled apart', async () => {
    await page.viewport(1300, 900);
    render(<FeatureEditor feature={withStrategy()} onPatch={vi.fn()} readOnly={false} />);

    const sections = Array.from(document.querySelectorAll('.feature-section'));
    expect(sections).toHaveLength(10);

    // Nothing to expand: the accordion is gone, so a reader no longer has to open ten panels to
    // see what a feature says.
    expect(document.querySelector('.cds--accordion')).toBeNull();

    // The rule goes *between* sections — the first has nothing above it to be separated from.
    const borderOf = (el: Element) => getComputedStyle(el).borderTopWidth;
    expect(borderOf(sections[0])).toBe('0px');
    expect(sections.slice(1).every((el) => borderOf(el) === '1px')).toBe(true);
  });
});

describe('FeatureEditor — heading', () => {
  it('names the feature and explains the asterisk', async () => {
    await page.viewport(900, 500);
    render(
      <FeatureEditor
        feature={withStrategy()}
        onPatch={vi.fn()}
        readOnly={false}
        title="Feature 5"
      />,
    );

    expect(document.querySelector('.feature-sections__title')?.textContent).toBe('Feature 5');
    expect(document.querySelector('.protocol-checklist__required-legend')?.textContent).toContain(
      'Required fields',
    );
    // The Description section heading labels these fields; the fieldset used to repeat it.
    expect(
      Array.from(document.querySelectorAll('legend')).some(
        (el) => el.textContent === 'Feature description',
      ),
    ).toBe(false);
  });
});

describe('FeatureEditor — required legend visibility', () => {
  it('keeps the key out of a read-only feature', async () => {
    await page.viewport(900, 500);
    render(<FeatureEditor feature={withStrategy()} onPatch={vi.fn()} readOnly title="Feature 5" />);

    // The key explains the asterisk; on a form nothing can be filled into, it explains nothing.
    expect(document.querySelector('.protocol-checklist__required-legend')).toBeNull();
    expect(document.querySelector('.feature-sections__title')?.textContent).toBe('Feature 5');
  });
});

describe('FeatureEditor — Description row', () => {
  it('keeps the invalid icon inside the field it belongs to', async () => {
    await page.viewport(1150, 400);
    render(
      <FeatureEditor
        feature={{ featureLabel: '2', compositeFeatureInd: 'false' } as never}
        onPatch={vi.fn()}
        readOnly={false}
        takenLabels={['2']}
      />,
    );

    const input = document.getElementById('feat-label')!.getBoundingClientRect();
    const icon = document.querySelector('.cds--text-input__invalid-icon')!.getBoundingClientRect();

    // Carbon pins the icon to `.cds--text-input__field-wrapper`, not to the input — capping the bare
    // input left the wrapper full width and the exclamation floated clear of its field.
    expect(icon.right).toBeLessThanOrEqual(input.right);
    expect(icon.x).toBeGreaterThan(input.x);
  });

  it('sits the label beside the two selects, not stranded before them', async () => {
    await page.viewport(1150, 400);
    render(
      <FeatureEditor
        feature={{ featureLabel: '2', compositeFeatureInd: 'false' } as never}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    const label = document.getElementById('feat-label')!.getBoundingClientRect();
    const cls = document.getElementById('feat-class')!.getBoundingClientRect();

    // Same row, and the gap between them is spacing rather than the remains of an over-wide column.
    expect(Math.round(label.top)).toBe(Math.round(cls.top));
    expect(cls.x - label.right).toBeLessThan(200);
  });
});

describe('FeatureEditor — feature type follow-ups', () => {
  it('puts a type’s follow-up field in the same cell as its checkbox', async () => {
    await page.viewport(1250, 900);
    render(
      <FeatureEditor
        feature={{ ofCMTs: 'true', compositeFeatureInd: 'false' } as never}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    const field = document.getElementById('feat-cmt-num')!;
    const cell = field.closest('.chr-checklist__type-cell');
    expect(cell).not.toBeNull();

    // Rendered as a sibling it took the next column slot, so it appeared beside an unrelated
    // checkbox and shunted the rest of the grid along.
    const box = cell!.querySelector('input[type="checkbox"]')!;
    expect(box.id).toBe('feat-ofCMTs');
    expect(field.getBoundingClientRect().top).toBeGreaterThan(box.getBoundingClientRect().bottom);
  });
});

describe('FeatureEditor — feature types stay put when a box is ticked', () => {
  const columnOf = (): Record<string, number> => {
    const out: Record<string, number> = {};
    document
      .querySelectorAll('.chr-checklist__type-columns input[type="checkbox"]')
      .forEach((el) => {
        out[el.id] = Math.round(el.getBoundingClientRect().x);
      });
    return out;
  };

  it('does not move a checkbox to another column when a neighbour opens a field', async () => {
    await page.viewport(1250, 900);

    const { unmount } = render(
      <FeatureEditor
        feature={{ compositeFeatureInd: 'false' } as never}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    const before = columnOf();
    unmount();

    render(
      <FeatureEditor
        feature={{ compositeFeatureInd: 'false', ofCMTs: 'true' } as never}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );
    const after = columnOf();

    // CSS multi-column balances its content, so opening a field re-flowed items between columns and
    // boxes jumped as they were ticked. The columns are dealt in markup, so every box keeps its own.
    expect(Object.keys(after)).toEqual(Object.keys(before));
    Object.keys(before).forEach((id) => expect(after[id]).toBe(before[id]));
  });

  it('leaves no gap beside a checkbox whose neighbour has opened a field', async () => {
    await page.viewport(1250, 900);
    render(
      <FeatureEditor
        feature={{ compositeFeatureInd: 'false', ofCMTs: 'true' } as never}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    const grid = document.querySelector('.chr-checklist__type-columns')!;
    const boxes = Array.from(grid.querySelectorAll('input[type="checkbox"]')).map((el) =>
      el.getBoundingClientRect(),
    );

    // On a grid the row was as tall as its tallest cell, so an expanded "CMTs" two columns over
    // left three lines of empty space beside every checkbox sharing its row.
    const firstColumnX = Math.min(...boxes.map((b) => Math.round(b.x)));
    const column = boxes.filter((b) => Math.round(b.x) === firstColumnX).sort((a, b) => a.y - b.y);

    expect(column.length).toBeGreaterThan(3);
    const gaps = column.slice(1).map((b, i) => b.top - column[i].bottom);
    expect(Math.max(...gaps)).toBeLessThan(40);
  });
});

describe('FeatureEditor — registered-site follow-up', () => {
  it('puts the Borden field under the answer that reveals it', async () => {
    await page.viewport(900, 500);
    render(
      <FeatureEditor
        feature={{ compositeFeatureInd: 'false', chrRegisteredSite: 'true' } as never}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    const question = Array.from(document.querySelectorAll('legend')).find((el) =>
      el.textContent?.startsWith('Is this a registered'),
    )!;
    const yes = document.getElementById('feat-registered-yes')!.getBoundingClientRect();
    const borden = document.getElementById('feat-borden')!.getBoundingClientRect();

    // Beside the radios it read as a separate question rather than a follow-up to this one.
    expect(borden.top).toBeGreaterThan(yes.bottom);
    // Left-aligned with the question it belongs to. (Compared against the question, not the radio's
    // own <input>, which Carbon insets within its wrapper.)
    expect(Math.round(borden.x)).toBe(Math.round(question.getBoundingClientRect().x));
  });
});

describe('FeatureEditor — Damage layout once Q1 is ticked', () => {
  const damaged = (): Feature =>
    ({
      featureLabel: 'Feature 1',
      q1Isthereevidenceofdamagetothesiteorfeature: 'true',
    }) as Feature;

  const rect = (selector: string): DOMRect => {
    const el = document.querySelector(selector);
    expect(el, selector).not.toBeNull();
    return el!.getBoundingClientRect();
  };

  it('stacks the description, Q2 and Q3 in one column', async () => {
    await page.viewport(1250, 900);
    render(<FeatureEditor feature={damaged()} onPatch={vi.fn()} readOnly={false} />);

    await waitFor(() => expect(document.querySelector('#feat-q3-Y')).not.toBeNull());
    const description = rect('#feat-damage-desc');
    const cause = rect('#feat-q2-cause');
    const q3 = rect('#feat-q3-Y');

    // One column: each control starts at the same left edge and sits below the one before it.
    expect(Math.round(cause.left)).toBe(Math.round(description.left));
    expect(cause.top).toBeGreaterThan(description.bottom);
    expect(q3.top).toBeGreaterThan(cause.bottom);
  });

  it('keeps the cause picker well short of the description text area', async () => {
    await page.viewport(1250, 900);
    render(<FeatureEditor feature={damaged()} onPatch={vi.fn()} readOnly={false} />);

    const description = rect('#feat-damage-desc');
    const picker = rect('.chr-checklist__damage-cause .cds--list-box');

    // The mockup's proportion: a one-line question's answer, not a second paragraph field.
    expect(picker.width).toBeLessThan(description.width * 0.6);
    expect(picker.width).toBeGreaterThan(200);
  });

  it('lays the three Q3 answers out on one line', async () => {
    await page.viewport(1250, 900);
    render(<FeatureEditor feature={damaged()} onPatch={vi.fn()} readOnly={false} />);

    await waitFor(() => expect(document.querySelector('#feat-q3-D')).not.toBeNull());
    const [yes, no, dunno] = ['Y', 'N', 'D'].map((code) => rect(`#feat-q3-${code}`));

    expect(midY(document.querySelector('#feat-q3-N')!)).toBe(
      midY(document.querySelector('#feat-q3-Y')!),
    );
    expect(no.left).toBeGreaterThan(yes.left);
    expect(dunno.left).toBeGreaterThan(no.left);
  });
});

describe('FeatureEditor — Windthrow and Trail features', () => {
  const sectionTitles = (): string[] =>
    [...document.querySelectorAll('.feature-section__title')].map((n) => n.textContent ?? '');

  it('gives trail features its own section rather than a column beside windthrow', async () => {
    await page.viewport(1250, 900);
    render(
      <FeatureEditor
        feature={{ featureLabel: 'F1' } as Feature}
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    const titles = sectionTitles();
    expect(titles).toContain('Windthrow');
    expect(titles).toContain('Trail features');
    // Separate sections stack; the pair used to share one two-column row.
    const [windthrow, trail] = ['Windthrow', 'Trail features'].map(
      (t) =>
        [...document.querySelectorAll('.feature-section')].find(
          (n) => n.querySelector('.feature-section__title')?.textContent === t,
        )!,
    );
    expect(trail.getBoundingClientRect().top).toBeGreaterThan(
      windthrow.getBoundingClientRect().bottom - 1,
    );
  });

  it('indents what each checkbox reveals under the box that revealed it', async () => {
    await page.viewport(1250, 1200);
    render(
      <FeatureEditor
        feature={
          {
            featureLabel: 'F1',
            windthrowManagement: 'true',
            trailfeatures: 'true',
            isthereevidenceofdamage: 'true',
          } as Feature
        }
        onPatch={vi.fn()}
        readOnly={false}
      />,
    );

    const parent = document.querySelector('#feat-windthrowManagement')!.getBoundingClientRect();
    const reveals = [...document.querySelectorAll('.chr-checklist__reveal')];
    expect(reveals.length).toBe(2);
    reveals.forEach((r) => {
      expect(r.getBoundingClientRect().left).toBeGreaterThan(parent.left);
    });

    // A percentage answer, not a column-width field.
    const field = document
      .querySelector('#feat-trail-len')!
      .closest('.cds--text-input__field-wrapper')!
      .getBoundingClientRect();
    expect(field.width).toBeLessThanOrEqual(14 * 16 + 1);
  });
});

describe('FeatureEditor — Summary layout', () => {
  const answered = (): Feature =>
    ({
      featureLabel: 'F1',
      q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature: 'true',
      q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective:
        'true',
      q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature:
        'true',
    }) as Feature;

  it('keeps each description under the question that asked for it', async () => {
    await page.viewport(1250, 1800);
    render(<FeatureEditor feature={answered()} onPatch={vi.fn()} readOnly={false} />);

    const box = (sel: string) => document.querySelector(sel)!.getBoundingClientRect();
    const q4 = box(
      '#feat-q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature',
    );
    const d4 = box('#feat-q4-desc');
    const d5 = box('#feat-q5-desc');
    const d6 = box('#feat-q6-desc');

    // One column, in order — the three used to sit in a two-column grid beside their checkboxes.
    expect(d4.top).toBeGreaterThan(q4.top);
    expect(d5.top).toBeGreaterThan(d4.bottom);
    expect(d6.top).toBeGreaterThan(d5.bottom);
    expect(Math.round(d5.left)).toBe(Math.round(d4.left));
  });

  it('sizes the rating by its options and the rationale by the column', async () => {
    await page.viewport(1250, 1800);
    render(<FeatureEditor feature={answered()} onPatch={vi.fn()} readOnly={false} />);

    const rating = document.querySelector('#feat-rating')!.getBoundingClientRect();
    const rationale = document.querySelector('#feat-rating-rationale')!.getBoundingClientRect();

    expect(rating.width).toBeLessThan(rationale.width * 0.5);
  });
});

describe('FeatureEditor — inline errors', () => {
  it('keeps an inline error in flow rather than printing it over the next label', async () => {
    await page.viewport(1250, 1600);
    render(
      <FeatureEditor
        feature={{ featureLabel: 'F1' } as Feature}
        onPatch={vi.fn()}
        readOnly={false}
        showErrors
      />,
    );

    const error = document.querySelector('.cds--form-requirement')!;
    const next = [...document.querySelectorAll('.cds--label')].find(
      (n) => n.textContent === 'Feature rating rationale',
    )!;

    // Carbon positions the error absolutely, so it takes no height and "A rating is required."
    // landed on top of "Feature rating rationale".
    expect(error.getBoundingClientRect().bottom).toBeLessThanOrEqual(
      next.getBoundingClientRect().top,
    );
  });
});
