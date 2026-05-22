import type { SiteDetail } from '@/types/siteDetail';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';
import { env } from '@/env';

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

export function buildLegacySiteDetailUrl(frepSelectedSiteId: string): string {
  const base = (env.VITE_LEGACY_APP_URL ?? '/ext/frep').replace(/\/$/, '');
  const encoded = encodeURIComponent(frepSelectedSiteId);
  return `${base}/frep110SiteDetailAction.do?selected_site_id=${encoded}`;
}
