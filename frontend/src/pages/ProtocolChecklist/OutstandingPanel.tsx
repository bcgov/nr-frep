import { ChevronDown, ChevronUp, ErrorFilled } from '@carbon/icons-react';
import { useId, useState } from 'react';

import type { OutstandingGroup } from './tabStatus';
import type { FC } from 'react';

/**
 * The per-tab list of everything still outstanding, behind a disclosure at the top of the tab.
 *
 * This replaces the warning banner that used to sit here. The banner said the same thing, but it
 * said it at full width in notification chrome every time the tab was opened, which put a page of
 * alarm in front of work that is simply not finished yet — and on the Features tab, where every
 * feature contributes its own rules, it could run longer than the form beneath it. A disclosure
 * keeps the list one click away and lets it be folded once it has been read.
 *
 * It opens by default: the list is the answer to "why can't I submit?", so it has to be readable
 * without first knowing to look for it. Collapsing is a per-visit choice and is deliberately not
 * remembered — the count in the tab strip is the persistent signal, and this is its detail.
 *
 * Once a submit has been refused the disclosure turns red and takes an error icon, in step with the
 * tab counts and the page banner. Only the summary line changes: the items below it are the same
 * work in either tone, and colouring the list as well would leave nothing on the tab that was not
 * red.
 */

type Props = {
  /**
   * Outstanding items, already grouped by the record they belong to.
   *
   * Nothing renders when there is nothing to list. That is counted in items rather than in groups:
   * a caller with a single form has one group to give whether or not it holds anything, so testing
   * the group count alone left the disclosure sitting there opening onto an empty list.
   */
  groups: OutstandingGroup[];
  /** `error` once a submit has been refused for these items. */
  tone?: 'neutral' | 'error';
};

const OutstandingPanel: FC<Props> = ({ groups, tone = 'neutral' }) => {
  const [open, setOpen] = useState(true);
  const contentId = useId();

  const shown = groups.filter((group) => group.items.length > 0);
  const total = shown.reduce((sum, group) => sum + group.items.length, 0);
  if (total === 0) return null;
  const noun = total === 1 ? 'item' : 'items';

  return (
    <div className="protocol-checklist__outstanding">
      <button
        type="button"
        className={`protocol-checklist__outstanding-toggle${
          tone === 'error' ? ' protocol-checklist__outstanding-toggle--error' : ''
        }`}
        aria-expanded={open}
        aria-controls={contentId}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        {tone === 'error' && <ErrorFilled size={16} />}
        Outstanding in this tab
        {/* The count lives in the accessible name rather than the visible label: the tab strip
            already carries it, and repeating it here read as two different numbers at a glance. */}
        <span className="cds--visually-hidden">
          {` (${total} ${noun}${tone === 'error' ? ', blocking submit' : ''})`}
        </span>
        {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>
      {open && (
        <div id={contentId} className="protocol-checklist__outstanding-body">
          {shown.map((group) => (
            <div
              key={group.title ?? '__ungrouped'}
              className="protocol-checklist__outstanding-group"
            >
              {group.title && (
                <p className="protocol-checklist__outstanding-title">{group.title}</p>
              )}
              <ul className="protocol-checklist__outstanding-list">
                {group.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default OutstandingPanel;
