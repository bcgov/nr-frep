import type { GenerateMasterListRequest, MasterListAdmin } from '@/types/masterListAdmin';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

export class MasterListAdminService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getMasterList(effectiveYear: string): CancelablePromise<MasterListAdmin> {
    return this.doRequest<MasterListAdmin>(this.config, {
      method: 'GET',
      url: '/v1/admin/master-list',
      query: { effectiveYear },
    });
  }

  generate(request: GenerateMasterListRequest): CancelablePromise<MasterListAdmin> {
    return this.doRequest<MasterListAdmin>(this.config, {
      method: 'POST',
      url: '/v1/admin/master-list/generate',
      body: request,
      mediaType: 'application/json',
    });
  }

  saveComments(effectiveYear: string, comments: string): CancelablePromise<MasterListAdmin> {
    return this.doRequest<MasterListAdmin>(this.config, {
      method: 'POST',
      url: '/v1/admin/master-list/comments',
      body: { effectiveYear, comments },
      mediaType: 'application/json',
    });
  }

  deleteMasterList(effectiveYear: string): CancelablePromise<MasterListAdmin> {
    return this.doRequest<MasterListAdmin>(this.config, {
      method: 'DELETE',
      url: '/v1/admin/master-list',
      query: { effectiveYear },
    });
  }
}
