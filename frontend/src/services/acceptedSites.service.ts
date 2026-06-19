import type { AcceptedSite, AcceptedSitesQuery } from '@/types/acceptedSite';
import type { FeatureCollection } from 'geojson';

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
   * Opening polygon as a GeoJSON FeatureCollection, proxied from the DataBC WFS by the backend
   * (`OPENING_ID`). Drives the in-app Leaflet map; an empty FeatureCollection means no mapped polygon.
   */
  getOpeningPolygon(openingId: string): CancelablePromise<FeatureCollection> {
    return this.doRequest<FeatureCollection>(this.config, {
      method: 'GET',
      url: '/v1/openings/{openingId}/polygon',
      path: { openingId },
    });
  }
}
