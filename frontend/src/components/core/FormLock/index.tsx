import type { FC, ReactNode } from 'react';

/**
 * Holds a section's controls still while its save is in flight.
 *
 * A real `fieldset[disabled]` rather than CSS: `pointer-events: none` stops the mouse but not the
 * keyboard, so a field that already had focus when Save was pressed would keep accepting typing —
 * and that edit is silently dropped, because the payload has already gone. Disabling the fieldset
 * takes every descendant control out of the tab order and stops it accepting input, which is the
 * behaviour actually being asked for.
 *
 * Wraps the whole tab, Save and Cancel included: both are already disabled for the same flag, and
 * doing it here means a section cannot forget to.
 */
const FormLock: FC<{ busy: boolean; children: ReactNode }> = ({ busy, children }) => (
  <fieldset className="form-lock" disabled={busy}>
    {children}
  </fieldset>
);

export default FormLock;
