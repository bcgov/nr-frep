import type {
  AcceptedSite,
  AcceptedSitesQuery,
  OpeningSearchQuery,
  OpeningSearchResult,
  TargetedSiteValidationResponse,
} from '@/types/acceptedSite';
import type { PagedResponse } from '@/types/search';
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

  /**
   * One page of openings for the "Add Target Site" picker (legacy SIL56 Opening Tenure Search). The
   * backend caps the page size at 100 and returns the true total so the picker can page through all
   * matches.
   */
  searchOpenings(query: OpeningSearchQuery): CancelablePromise<PagedResponse<OpeningSearchResult>> {
    // Forward only the filters that are actually set (blank/undefined are dropped).
    const params: Record<string, unknown> = {};
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params[key] = value;
      }
    });
    return this.doRequest<PagedResponse<OpeningSearchResult>>(this.config, {
      method: 'GET',
      url: '/v1/openings/search',
      query: params,
    });
  }

  /**
   * Validate an opening for targeting (FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE). A valid result
   * means the opening can be targeted by this district; otherwise `messages` explains why not.
   */
  validateTargetedSite(
    openingId: string,
    orgUnit: string,
  ): CancelablePromise<TargetedSiteValidationResponse> {
    return this.doRequest<TargetedSiteValidationResponse>(this.config, {
      method: 'POST',
      url: '/v1/accepted-sites/targeted',
      body: { openingId, orgUnit },
      mediaType: 'application/json',
    });
  }
}
