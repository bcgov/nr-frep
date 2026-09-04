import { InlineNotification } from '@carbon/react';

import type { FC } from 'react';

/**
 * The page-level tally above the tab strip: how much the checklist still owes, and across how many
 * tabs.
 *
 * It has two tones, and only ever one banner is on the page. Before a submit has been refused it is
 * informational and worded to say so: everything it counts is ordinary unfinished work, the
 * checklist saves perfectly well in this state, and the banner's job is to point at the tab strip
 * and set the expectation for submit rather than report a fault. Once a submit *has* been refused
 * the same items have stopped being "not finished yet" and become the reason the checklist would not
 * go, so the banner turns red and says that instead — and the tab counts and each tab's disclosure
 * turn with it, which is why the copy sends the reader to them rather than repeating the list.
 *
 * Nothing renders once the count reaches zero, so clearing the last item clears the banner in either
 * tone. A refusal that leaves nothing outstanding came from the server rather than from these rules,
 * and the page's own validation panel reports it.
 */

type Props = {
  /** Total outstanding items across every tab. */
  total: number;
  /** How many tabs carry at least one of them. */
  tabs: number;
  /** True once a submit has been refused for these items. */
  refused?: boolean;
};

const OutstandingSummary: FC<Props> = ({ total, tabs, refused = false }) => {
  if (total === 0) return null;
  if (refused) {
    return (
      <InlineNotification
        className="protocol-checklist__outstanding-summary"
        kind="error"
        lowContrast
        hideCloseButton
        title="Checklist not submitted"
        subtitle="All required items must be complete before it can be submitted. The tab counts show what is still outstanding."
      />
    );
  }
  const items = total === 1 ? 'required item' : 'required items';
  const where = tabs === 1 ? '1 tab' : `${tabs} tabs`;
  return (
    <InlineNotification
      className="protocol-checklist__outstanding-summary"
      kind="info"
      lowContrast
      hideCloseButton
      title={`${total} ${items} outstanding across ${where}`}
      subtitle="You can save at any time. The checklist can be submitted once these are complete."
    />
  );
};

export default OutstandingSummary;
