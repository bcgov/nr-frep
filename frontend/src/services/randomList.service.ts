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
}
