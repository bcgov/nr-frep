import { ErrorFilled, WarningFilled } from '@carbon/icons-react';
import { Airplane, UnauthorizedUserAccess } from '@carbon/pictograms-react';
import { type FC } from 'react';

import Subtitle from '@/components/core/Subtitle';
import './index.scss';

/**
 * Deliberately explicit, rather than `import * as Icons from '@carbon/icons-react'`.
 *
 * A namespace import read dynamically (`Icons[icon]`) can't be tree-shaken — Rollup can't prove
 * which members are reachable, so it keeps every icon and all 1382 pictograms. That cost about
 * 2.8 MB of the production bundle, roughly 45% of it, to render one pictogram on one page.
 *
 * Add an entry here when an empty state needs a glyph these don't cover.
 */
const ICONS = { ErrorFilled, WarningFilled };
const PICTOGRAMS = { Airplane, UnauthorizedUserAccess };

interface EmptySectionProps {
  icon?: keyof typeof ICONS;
  title: string;
  description: string | React.ReactNode;
  pictogram?: keyof typeof PICTOGRAMS;
  className?: string;
  whiteLayer?: boolean;
}

/**
 * EmptySection component used to display a placeholder or empty state.
 *
 * Supports optional Carbon icons or pictograms, a title, and a description.
 *
 * @component
 * @example
 * ```tsx
 * <EmptySection
 *   icon="WarningFilled"
 *   title="No Data Available"
 *   description="Please check back later."
 * />
 * ```
 *
 * @param {keyof typeof ICONS} [icon] - Optional Carbon icon name.
 * @param {string} title - Title text for the empty section.
 * @param {string | React.ReactNode} description - Supporting description text.
 * @param {keyof typeof PICTOGRAMS} [pictogram] - Optional Carbon pictogram name.
 * @param {string} [className] - Optional custom class names.
 * @param {boolean} [whiteLayer] - Optional flag to apply white background layer.
 * @returns {JSX.Element} A styled empty state section.
 */
const EmptySection: FC<EmptySectionProps> = ({
  icon,
  title,
  description,
  pictogram,
  whiteLayer,
  className,
}) => {
  let Img: React.ElementType | undefined;

  if (icon && ICONS[icon]) {
    Img = ICONS[icon] as React.ElementType;
  }

  if (pictogram && PICTOGRAMS[pictogram]) {
    Img = PICTOGRAMS[pictogram] as React.ElementType;
  }

  return (
    <div
      className={`${className ?? ''} empty-section-container ${whiteLayer ? 'empty-section-white-layer' : undefined}`}
    >
      {Img ? <Img className="empty-section-icon" data-testid="empty-section-icon" /> : null}
      <div className="empty-section-title">{title}</div>
      <Subtitle className="empty-section-subtitle" text={description} />
    </div>
  );
};

export default EmptySection;
