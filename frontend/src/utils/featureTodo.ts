import type { NotificationContent } from '@/context/notification/NotificationContext';

import { ApiError } from '@/config/api/types';

/**
 * Invoke a backend feature whose endpoint may still be a TODO (responds HTTP 501).
 *
 * <p>On 501 it shows a friendly "coming soon" info notification instead of a generic
 * error. Used by the Export-to-Excel / Print / Map-GIS-view buttons whose backend
 * endpoints are stubbed pending implementation.
 */
export async function runTodoFeature(
  action: () => Promise<unknown>,
  display: (content: NotificationContent) => void,
  label: string,
): Promise<void> {
  try {
    await action();
  } catch (err) {
    if (err instanceof ApiError && err.status === 501) {
      display({
        kind: 'info',
        title: `${label} — coming soon`,
        subtitle: 'This feature is not yet available in the modernized app.',
        timeout: 6000,
      });
      return;
    }
    display({
      kind: 'error',
      title: `We couldn't run ${label}`,
      subtitle: err instanceof Error ? err.message : 'Unknown error',
      timeout: 9000,
    });
  }
}
