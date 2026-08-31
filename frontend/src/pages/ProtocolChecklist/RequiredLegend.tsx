import type { FC } from 'react';

/**
 * The key for the red asterisk: "* Required fields".
 *
 * Shown once at the top of any tab or form that marks fields required. The asterisk is a convention
 * the form assumes the reader already knows; stating it costs one line and means the marker is never
 * unexplained. Rendered only where something is actually marked — on a tab with no required fields
 * it would be a legend for a symbol that never appears.
 */
const RequiredLegend: FC = () => (
  <p className="protocol-checklist__required-legend">
    <span className="required-asterisk" aria-hidden="true">
      *
    </span>{' '}
    Required fields
  </p>
);

export default RequiredLegend;
