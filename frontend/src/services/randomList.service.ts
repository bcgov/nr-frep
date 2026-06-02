import type { RandomListQuery, RandomListSite } from '@/types/randomList';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

export class RandomListService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getRandomList(query: RandomListQuery): CancelablePromise<RandomListSite[]> {
    const { effectiveYear, orgUnit } = query;

    return this.doRequest<RandomListSite[]>(this.config, {
      method: 'GET',
      url: '/v1/random-list',
      query: {
        effectiveYear,
        ...(orgUnit ? { orgUnit } : {}),
      },
    });
  }

  /** Export the district random list to Excel. Backend endpoint is a TODO (responds 501). */
  exportRandomList(query: RandomListQuery): CancelablePromise<void> {
    const { effectiveYear, orgUnit } = query;

    return this.doRequest<void>(this.config, {
      method: 'GET',
      url: '/v1/random-list/export',
      query: {
        effectiveYear,
        ...(orgUnit ? { orgUnit } : {}),
      },
    });
  }
}
