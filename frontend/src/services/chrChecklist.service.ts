import type { CheckList } from '@/types/chrChecklist';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

/**
 * Client for the CHR checklist API (backend ChrChecklistController, base /api/v1/chr).
 * The full {@link CheckList} is the request and response payload for save/submit.
 */
export class ChrChecklistService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getChecklist(checklistId: string): CancelablePromise<CheckList> {
    return this.doRequest<CheckList>(this.config, {
      method: 'GET',
      url: '/v1/chr/checklists/{checklistId}',
      path: { checklistId },
    });
  }

  /** Save a draft. Backend routes to upload when the checklist is in offline (RDO) status. */
  save(checkList: CheckList): CancelablePromise<CheckList> {
    return this.doRequest<CheckList>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists',
      body: checkList,
      mediaType: 'application/json',
    });
  }

  /** Submit for review. On validation failure the API responds 400 with a ValidationError[] body. */
  submit(checklistId: string, checkList: CheckList): CancelablePromise<CheckList> {
    return this.doRequest<CheckList>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/submit',
      path: { checklistId },
      body: checkList,
      mediaType: 'application/json',
    });
  }

  activate(checklistId: string): CancelablePromise<CheckList> {
    return this.doRequest<CheckList>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/activate',
      path: { checklistId },
    });
  }

  /** Take offline: backend sets status RDO and a deviceCheckoutGuid, returned on the checklist. */
  takeOffline(checklistId: string): CancelablePromise<CheckList> {
    return this.doRequest<CheckList>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/offline',
      path: { checklistId },
    });
  }

  unsubmit(checklistId: string): CancelablePromise<CheckList> {
    return this.doRequest<CheckList>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/unsubmit',
      path: { checklistId },
    });
  }
}
