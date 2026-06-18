import { createContext } from 'react';

import type { ReactNode } from 'react';

export type ConfirmOptions = {
  /** Modal heading, e.g. "Delete contact?". */
  title: string;
  /** Body text describing the action + consequence. */
  message: string | ReactNode;
  /** Primary (destructive) button label. @default "Delete" */
  confirmButtonText?: string;
  /** Secondary (cancel) button label. @default "Cancel" */
  cancelButtonText?: string;
};

/** Opens a destructive-action confirmation and resolves true when confirmed, false when cancelled. */
export type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>;

export const ConfirmContext = createContext<ConfirmFn | null>(null);
