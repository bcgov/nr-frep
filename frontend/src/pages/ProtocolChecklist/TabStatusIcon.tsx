import type { TabStatus } from './tabStatus';
import type { FC, ReactNode } from 'react';

/**
 * Per-tab completion indicator, shown before the tab label in place of the old decorative section
 * icons:
 *
 * - `empty`    — green outline only: nothing has been saved on the tab yet.
 * - `partial`  — half filled green: some values are saved, but the tab would still block submit.
 * - `complete` — filled green with a white check: every submit rule this tab owns passes.
 * - `errors`   — filled red with a count: that many required fields are still outstanding.
 *
 * Opening info uses `errors`/`complete`; the other tabs use the three green states. Drawn inline
 * rather than composed from Carbon icons because neither the half-filled state nor the count badge
 * has a Carbon equivalent, and mixing an icon font with hand-drawn shapes makes the sizes disagree.
 */

const GREEN = 'var(--cds-support-success, #24a148)';
const RED = 'var(--cds-support-error, #da1e28)';

const LABELS: Record<TabStatus, string> = {
  empty: 'Not started',
  partial: 'In progress',
  complete: 'Complete',
  errors: 'Required fields missing',
};

type Props = {
  status: TabStatus;
  /** Tab title, so the accessible name says which section the state belongs to. */
  section?: string;
  /** How many required fields are outstanding — rendered inside the dot when `status` is `errors`. */
  count?: number;
};

/** Filled red with the outstanding count. */
const errorMark = (count: number): ReactNode => (
  <>
    <circle cx="8" cy="8" r="7" fill={RED} />
    {/* Two digits still fit at 16px once the type drops a step; beyond 99 the shape would lose
        either the count or the circle, so the count caps and the label carries the exact number
        for anyone who needs it. */}
    <text
      x="8"
      y="8"
      fill="#fff"
      fontSize={count > 9 ? 8 : 10}
      fontWeight="600"
      textAnchor="middle"
      dominantBaseline="central"
    >
      {count > 99 ? '99+' : count}
    </text>
  </>
);

/** Filled green with a white check. */
const completeMark = (): ReactNode => (
  <>
    <circle cx="8" cy="8" r="7" fill={GREEN} />
    <path
      d="M4.6 8.2 6.9 10.5 11.4 6"
      fill="none"
      stroke="#fff"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </>
);

/** Green outline, half filled while the tab is in progress. */
const outlineMark = (half: boolean): ReactNode => (
  <>
    {/* Right half filled — a static "half done" mark, not a percentage of the real rules. */}
    {half && <path d="M8 1.75A6.25 6.25 0 0 1 8 14.25Z" fill={GREEN} />}
    <circle cx="8" cy="8" r="6.25" fill="none" stroke={GREEN} strokeWidth="1.5" />
  </>
);

/** Which of the three marks this state draws. */
const mark = (status: TabStatus, count: number): ReactNode => {
  if (status === 'errors') return errorMark(count);
  if (status === 'complete') return completeMark();
  return outlineMark(status === 'partial');
};

const TabStatusIcon: FC<Props> = ({ status, section, count = 0 }) => {
  const noun = count === 1 ? 'field' : 'fields';
  const state = status === 'errors' ? `${count} required ${noun} missing` : LABELS[status];
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
      {mark(status, count)}
    </svg>
  );
};

export default TabStatusIcon;
