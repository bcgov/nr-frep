import { InlineNotification } from '@carbon/react';

import type { FC } from 'react';

/**
 * The "you can save this, but you can't submit it yet" banner, shared by every tab that allows an
 * incomplete save.
 *
 * It is persistent and not dismissible: it is the answer to "why can't I submit?", so it has to
 * survive leaving and re-entering the tab, and it clears itself when the last item is resolved.
 * Callers hold it back until the tab has been saved at least once — telling someone what they have
 * not filled in before they have opened the form is nagging, not helping.
 */

type Props = {
  /** One line per outstanding rule. No banner is rendered when this is empty. */
  items: string[];
  /** True once a save has landed in this session, so the title can lead with it. */
  saved: boolean;
  /** What was saved, e.g. "Opening" — used only in the saved title. */
  sectionLabel: string;
  /** How the items read in the summary line: "required field" for a form, "item" for a list tab. */
  noun?: string;
};

const TabIncompleteBanner: FC<Props> = ({ items, saved, sectionLabel, noun = 'item' }) => {
  if (items.length === 0) return null;
  const plural = items.length === 1 ? noun : `${noun}s`;
  return (
    <InlineNotification
      className="protocol-checklist__incomplete"
      kind="warning"
      lowContrast
      hideCloseButton
      title={saved ? `${sectionLabel} saved — required fields missing` : 'Required fields missing'}
      subtitle={`${items.length} ${plural} to resolve before this checklist can be submitted:`}
    >
      {/* Carbon types `subtitle` as a string, so the list goes in the children slot — which renders
          in the same content column, directly under the subtitle. */}
      <ul className="protocol-checklist__incomplete-list">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </InlineNotification>
  );
};

export default TabIncompleteBanner;
