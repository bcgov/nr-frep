import { Button, Loading } from '@carbon/react';
import type { FC } from 'react';

/**
 * Carbon renders `renderIcon` as a component, so the spinner has to be one. `withOverlay={false}`
 * keeps it inside the button instead of dimming the page behind it.
 */
const RunningIcon: FC = () => <Loading small withOverlay={false} description="" />;

/**
 * A primary action button that says when its request is in flight.
 *
 * The spinner and the changed label are the visible half of the guard; the other half is
 * {@link FormLock}, which stops the form accepting edits that would be silently dropped because the
 * payload has already gone.
 *
 * `busy` is per-button, not per-page: a screen with several actions sharing one in-flight flag must
 * still spin only the button that was pressed.
 *
 * Matches the pattern in nr-fspts (`ReviewMilestoneEditModal`).
 */
const ActionButton: FC<{
  /** True only while *this* button's action is running. */
  busy: boolean;
  onClick: () => void;
  /** Resting label. */
  children?: string;
  /** Shown while running. */
  busyLabel?: string;
  disabled?: boolean;
  kind?: 'primary' | 'tertiary' | 'danger' | 'danger--tertiary';
  /** Modal footers size their buttons `md`; a section's actions are `lg`. */
  size?: 'md' | 'lg';
}> = ({
  busy,
  onClick,
  children = 'Save',
  busyLabel = 'Saving…',
  disabled = false,
  kind = 'primary',
  size = 'lg',
}) => (
  <Button
    kind={kind}
    size={size}
    disabled={busy || disabled}
    renderIcon={busy ? RunningIcon : undefined}
    onClick={onClick}
  >
    {busy ? busyLabel : children}
  </Button>
);

export default ActionButton;
