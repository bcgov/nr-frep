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

  /**
   * Per-section saves (mirroring the Biodiversity per-tab save). Each posts the full checklist but
   * the backend persists only that section, so e.g. saving Opening info does not re-sync photos.
   * The response is the freshly re-read checklist (new revision count + any server-assigned ids).
   */
  private saveSection(section: string, checklistId: string, checkList: CheckList) {
    return this.doRequest<CheckList>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/{section}',
      path: { checklistId, section },
      body: checkList,
      mediaType: 'application/json',
    });
  }

  saveOpening(checklistId: string, checkList: CheckList): CancelablePromise<CheckList> {
    return this.saveSection('opening', checklistId, checkList);
  }

  saveBlockSummary(checklistId: string, checkList: CheckList): CancelablePromise<CheckList> {
    return this.saveSection('block-summary', checklistId, checkList);
  }

  saveContacts(checklistId: string, checkList: CheckList): CancelablePromise<CheckList> {
    return this.saveSection('contacts', checklistId, checkList);
  }

  saveFeatures(checklistId: string, checkList: CheckList): CancelablePromise<CheckList> {
    return this.saveSection('features', checklistId, checkList);
  }

  savePhotos(checklistId: string, checkList: CheckList): CancelablePromise<CheckList> {
    return this.saveSection('photos', checklistId, checkList);
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
