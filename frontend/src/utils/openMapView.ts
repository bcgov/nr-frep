import type { NotificationContent } from '@/context/notification/NotificationContext';

import API from '@/services/APIs';

/**
 * Open the external GIS map viewer for an opening's bounding box in a new tab (legacy per-row "Map"
 * action on Random List / Accepted Sites). The backend composes the viewer URL from the opening's
 * extent; here we just open it and surface the edge cases:
 *  - empty URL  → no viewer configured for this environment
 *  - null popup → the browser blocked the new tab
 */
export async function openOpeningMapView(
  openingId: string,
  display: (content: NotificationContent) => void,
): Promise<void> {
  try {
    const { url } = await API.acceptedSites.getOpeningMapView(openingId);
    if (!url) {
      display({
        kind: 'info',
        title: 'Map View unavailable',
        subtitle: 'No map viewer is configured for this environment.',
        timeout: 6000,
      });
      return;
    }
    const opened = window.open(url, '_blank', 'noopener,noreferrer');
    if (!opened) {
      display({
        kind: 'warning',
        title: 'Pop-up blocked',
        subtitle: 'Allow pop-ups for this site to open the map view.',
        timeout: 7000,
      });
    }
  } catch (err) {
    display({
      kind: 'error',
      title: "We couldn't open the map view",
      subtitle: err instanceof Error ? err.message : 'Unknown error',
      timeout: 9000,
    });
  }
}
