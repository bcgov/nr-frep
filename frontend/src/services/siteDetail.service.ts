import type { SiteDetail } from '@/types/siteDetail';

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
}
