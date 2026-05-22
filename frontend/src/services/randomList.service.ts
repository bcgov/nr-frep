import type { RandomListQuery, RandomListSite } from '@/types/randomList';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';
import { env } from '@/env';

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

export function buildLegacyRandomListUrl(): string {
  const base = (env.VITE_LEGACY_APP_URL ?? '/ext/frep').replace(/\/$/, '');
  return `${base}/frep100RandomListAction.do?isMenuPick=true`;
}
