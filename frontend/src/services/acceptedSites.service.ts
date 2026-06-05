import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

import type { AcceptedSite, AcceptedSitesQuery } from '@/types/acceptedSite';

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

  /** GIS map-view URL for an opening's extent. Backend endpoint is a TODO (responds 501). */
  getOpeningMapView(openingId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'GET',
      url: '/v1/openings/{openingId}/map-view',
      path: { openingId },
    });
  }
}
