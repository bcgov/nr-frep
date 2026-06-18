import { useContext } from 'react';

import { ConfirmContext, type ConfirmFn } from './ConfirmContext';

/** Auto-confirm used when no provider is mounted (isolated component tests / storybook). */
const autoConfirm: ConfirmFn = () => Promise.resolve(true);

/**
 * Returns the app-wide `confirm(options)`. Falls back to {@link autoConfirm} when there is no
 * {@link ConfirmProvider} (the provider is mounted once at the app root, so this fallback only
 * applies to components rendered in isolation, e.g. tests) — so delete call sites never crash.
 */
export const useConfirm = (): ConfirmFn => useContext(ConfirmContext) ?? autoConfirm;
