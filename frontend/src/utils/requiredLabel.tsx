import type { ReactNode } from 'react';

/**
 * Appends a red asterisk to a field label when the field is required — the app-wide pattern for
 * marking required inputs. Pass it to any Carbon input's `labelText`:
 *
 * ```tsx
 * <TextInput labelText={requiredLabel('Organization unit', true)} … />
 * ```
 *
 * The `.required-asterisk` style is defined globally in `styles/_overrides.scss`.
 */
export const requiredLabel = (label: ReactNode, required = false): ReactNode =>
  required ? (
    <>
      {label}
      <span className="required-asterisk" aria-hidden="true">
        {' '}
        *
      </span>
    </>
  ) : (
    label
  );
