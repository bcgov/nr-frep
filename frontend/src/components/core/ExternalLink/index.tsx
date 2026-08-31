import { Launch } from '@carbon/icons-react';

import type { FC, ReactNode } from 'react';

import './index.scss';

/**
 * A link that leaves FREP for another application (SILVA, and anything else that opens in its own
 * tab).
 *
 * The trailing icon is the convention users read as "this goes somewhere else" — without it a link
 * that steals a tab looks identical to one that navigates in place. It is `aria-hidden` because a
 * decorative glyph announced as "launch" tells a screen-reader user nothing; the same information is
 * carried by the visually-hidden suffix instead, so both audiences learn the link opens elsewhere.
 *
 * `rel="noopener noreferrer"` is not optional here: without `noopener` the opened page gets a handle
 * on this window through `window.opener` and can navigate it.
 */
export const ExternalLink: FC<{
  href: string;
  children: ReactNode;
  className?: string;
}> = ({ href, children, className }) => (
  <a
    href={href}
    target="_blank"
    rel="noopener noreferrer"
    className={['external-link', className].filter(Boolean).join(' ')}
  >
    {children}
    <Launch size={16} className="external-link__icon" aria-hidden="true" />
    <span className="cds--visually-hidden"> (opens in a new tab)</span>
  </a>
);

export default ExternalLink;
