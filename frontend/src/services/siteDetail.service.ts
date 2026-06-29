import type { SiteDetail, SiteResourceSave } from '@/types/siteDetail';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

export class SiteDetailService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getSiteDetail(frepSelectedSiteId: string): CancelablePromise<SiteDetail> {
    return this.doRequest<SiteDetail>(this.config, {
      method: 'GET',
      url: '/v1/sites/{frepSelectedSiteId}',
      path: { frepSelectedSiteId },
    });
  }

  /**
   * Site-detail context for a brand-new targeted opening (FREP200 "Add Target Site") — the opening
   * header plus a blank row per protocol type to evaluate, before any selected site exists.
   */
  getSiteDetailByOpening(openingId: string, effectiveYear: string): CancelablePromise<SiteDetail> {
    return this.doRequest<SiteDetail>(this.config, {
      method: 'GET',
      url: '/v1/sites/by-opening',
      query: { openingId, effectiveYear },
    });
  }

  /** Create a targeted site for an opening (FREP_110_SITE_DETAILS.SAVE; spawns checklists). */
  createTargetedSite(request: {
    openingId: string;
    orgUnit: string;
    effectiveYear: string;
    resources: SiteResourceSave[];
  }): CancelablePromise<SiteDetail> {
    return this.doRequest<SiteDetail>(this.config, {
      method: 'POST',
      url: '/v1/sites/targeted',
      body: request,
      mediaType: 'application/json',
    });
  }

  /** Save accept/reject/target evaluations (FREP_110_SITE_DETAILS.SAVE; spawns checklists). */
  saveResources(
    frepSelectedSiteId: string,
    resources: SiteResourceSave[],
  ): CancelablePromise<SiteDetail> {
    return this.doRequest<SiteDetail>(this.config, {
      method: 'PUT',
      url: '/v1/sites/{frepSelectedSiteId}/resources',
      path: { frepSelectedSiteId },
      body: resources,
      mediaType: 'application/json',
    });
  }
}
