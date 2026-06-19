import type { AcceptedSite, AcceptedSitesQuery } from '@/types/acceptedSite';
import type { MapViewResponse } from '@/types/mapView';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

export class AcceptedSitesService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getAcceptedSites(query: AcceptedSitesQuery): CancelablePromise<AcceptedSite[]> {
    const { effectiveYear, orgUnit, protocolType } = query;

    return this.doRequest<AcceptedSite[]>(this.config, {
      method: 'GET',
      url: '/v1/accepted-sites',
      query: {
        effectiveYear,
        orgUnit,
        ...(protocolType ? { protocolType } : {}),
      },
    });
  }

  /** Printable accepted-sites view. Backend endpoint is a TODO (responds 501). */
  printAcceptedSites(): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'GET',
      url: '/v1/accepted-sites/print',
    });
  }

  /**
   * Resolve the external GIS map-viewer URL for an opening's bounding box. The backend composes the
   * URL from `frep_map_bounding_values` + the configured viewer base; `url` is empty when no viewer
   * is configured for the environment.
   */
  getOpeningMapView(openingId: string): CancelablePromise<MapViewResponse> {
    return this.doRequest<MapViewResponse>(this.config, {
      method: 'GET',
      url: '/v1/openings/{openingId}/map-view',
      path: { openingId },
    });
  }
}
