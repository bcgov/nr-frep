import type {
  ChecklistSearchQuery,
  ChecklistSearchResult,
  ClientSearchQuery,
  ClientSearchResult,
} from '@/types/search';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

function stripBlank(query: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(query).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  );
}

export class SearchService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  searchChecklists(query: ChecklistSearchQuery): CancelablePromise<ChecklistSearchResult[]> {
    return this.doRequest<ChecklistSearchResult[]>(this.config, {
      method: 'GET',
      url: '/v1/search/checklists',
      query: stripBlank(query),
    });
  }

  searchClients(query: ClientSearchQuery): CancelablePromise<ClientSearchResult[]> {
    return this.doRequest<ClientSearchResult[]>(this.config, {
      method: 'GET',
      url: '/v1/search/clients',
      query: stripBlank(query),
    });
  }

  // CSV export is handled by services/reports.ts (requestChecklistSearchCsv → GET
  // /v1/reports/checklist-search/csv), which streams the file as a blob for download.
}
