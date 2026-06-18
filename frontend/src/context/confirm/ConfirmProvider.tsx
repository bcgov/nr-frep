import { useCallback, useRef, useState, type ReactNode } from 'react';

import { DestructiveModal } from '@/components/core/DestructiveModal';

import { ConfirmContext, type ConfirmFn, type ConfirmOptions } from './ConfirmContext';

/**
 * App-wide confirmation for destructive actions. Renders a single {@link DestructiveModal} and
 * exposes a promise-based `confirm(options)` via {@link useConfirm}: call sites just
 * `if (!(await confirm({ title, message }))) return;` before deleting — no per-component modal.
 */
export const ConfirmProvider = ({ children }: { children: ReactNode }) => {
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const resolverRef = useRef<((result: boolean) => void) | null>(null);

  const confirm = useCallback<ConfirmFn>(
    (opts) =>
      new Promise<boolean>((resolve) => {
        resolverRef.current = resolve;
        setOptions(opts);
      }),
    [],
  );

  const settle = useCallback((result: boolean) => {
    resolverRef.current?.(result);
    resolverRef.current = null;
    setOptions(null);
  }, []);

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <DestructiveModal
        open={options !== null}
        title={options?.title ?? ''}
        message={options?.message ?? ''}
        confirmButtonText={options?.confirmButtonText}
        cancelButtonText={options?.cancelButtonText}
        onConfirm={() => settle(true)}
        onCancel={() => settle(false)}
      />
    </ConfirmContext.Provider>
  );
};

export default ConfirmProvider;
