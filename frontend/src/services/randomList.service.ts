import type { RandomListQuery, RandomListResponse } from '@/types/randomList';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

export class RandomListService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getRandomList(query: RandomListQuery): CancelablePromise<RandomListResponse> {
    const { effectiveYear, orgUnit } = query;

    return this.doRequest<RandomListResponse>(this.config, {
      method: 'GET',
      url: '/v1/random-list',
      query: {
        effectiveYear,
        ...(orgUnit ? { orgUnit } : {}),
      },
    });
  }

  // CSV export is handled by services/reports.ts (requestRandomListCsv → GET
  // /v1/reports/random-list/csv), which streams the file as a blob for download.
}
