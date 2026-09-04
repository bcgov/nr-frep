import type { TabStatus } from './tabStatus';
import type { FC } from 'react';

/**
 * Per-tab outstanding-count badge, shown after the tab label in place of the old decorative section
 * icons.
 *
 * Only `errors` draws anything: a dark badge carrying the number of items the tab still owes. The
 * other three states — `complete`, `none` and `empty` — draw nothing at all. The strip is read at a
 * glance to answer one question, "where is there work left?", and a row of ticks answers it by
 * making the reader check each tab in turn. Absence says "nothing here" faster than a glyph can, and
 * it leaves the badges as the only marks on the strip, so they carry the eye on their own.
 *
 * The distinction between the silent states is still meaningful upstream even though it does not
 * show here: `complete` means every rule this tab owns passes, `none` that it owns no rules at all,
 * and `empty` that the read has not landed yet.
 *
 * The badge starts neutral and turns red only once a submit has been refused. Every one of these is
 * ordinary unfinished work on a part-filled checklist, and a strip of red on first open reads as a
 * page full of faults — but after a refusal the same counts are the reason the checklist would not
 * go, and red is then the accurate colour. The neutral tone uses Carbon's `inverse` pair so the
 * badge stays legible under the dark theme, where it inverts to a light chip.
 *
 * Drawn inline rather than composed from Carbon icons because the count badge has no Carbon
 * equivalent, and mixing an icon font with hand-drawn shapes makes the sizes disagree.
 */

const BADGE = 'var(--cds-background-inverse, #393939)';
const BADGE_TEXT = 'var(--cds-text-inverse, #ffffff)';
const BADGE_ERROR = 'var(--cds-support-error, #da1e28)';

type Props = {
  status: TabStatus;
  /** Tab title, so the accessible name says which section the count belongs to. */
  section?: string;
  /** How many items are outstanding — rendered inside the badge. */
  count?: number;
  /** `error` once a submit has been refused for these items. */
  tone?: 'neutral' | 'error';
};

const TabStatusIcon: FC<Props> = ({ status, section, count = 0, tone = 'neutral' }) => {
  if (status !== 'errors') return null;
  const noun = count === 1 ? 'item' : 'items';
  // The tone is carried in the accessible name too: the colour is the only thing that changes
  // visually, and a screen reader would otherwise not hear that the submit had been refused.
  const state = `${count} ${noun} outstanding${tone === 'error' ? ', blocking submit' : ''}`;
  const label = section ? `${section}: ${state}` : state;
  return (
    <svg
      className="protocol-checklist__tab-status"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      role="img"
      aria-label={label}
      focusable="false"
    >
      <title>{label}</title>
      <circle cx="8" cy="8" r="8" fill={tone === 'error' ? BADGE_ERROR : BADGE} />
      {/* Two digits still fit at 16px once the type drops a step; beyond 99 the shape would lose
          either the count or the circle, so the count caps and the label carries the exact number
          for anyone who needs it. */}
      <text
        x="8"
        y="8"
        fill={tone === 'error' ? '#fff' : BADGE_TEXT}
        fontSize={count > 9 ? 8 : 10}
        fontWeight="600"
        textAnchor="middle"
        dominantBaseline="central"
      >
        {count > 99 ? '99+' : count}
      </text>
    </svg>
  );
};

export default TabStatusIcon;
