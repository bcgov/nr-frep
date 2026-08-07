import type { PhotoPageResponse, CheckList } from '@/types/chrChecklist';

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

  /** One page of photo metadata — no bytes. */
  getPhotos(checklistId: string, page = 0, size = 10): CancelablePromise<PhotoPageResponse> {
    return this.doRequest<PhotoPageResponse>(this.config, {
      method: 'GET',
      url: '/v1/chr/checklists/{checklistId}/photos',
      path: { checklistId },
      query: { page, size },
    });
  }

  /**
   * One photo's stored bytes, as a binary blob. Replaces the base64 that used to ride inside the
   * checklist GET, so a checklist with many photos no longer builds one enormous response.
   */
  getPhotoContent(checklistId: string, photoId: string): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/chr/checklists/{checklistId}/photos/{photoId}/content',
      path: { checklistId, photoId },
      responseType: 'blob',
    });
  }

  /**
   * Attach one photo as `multipart/form-data`. A leaf operation, not a section save: it writes only
   * the photo, never the checklist, so it does not bump `revisionCount` and cannot conflict with an
   * in-flight edit on another tab. `mediaType` is deliberately unset so the browser supplies the
   * multipart boundary. Resolves to `void` (204).
   */
  addPhoto(
    checklistId: string,
    file: File,
    description: string,
    date?: string,
    deviceCheckoutGuid?: string,
  ): CancelablePromise<void> {
    const body = new FormData();
    body.append('file', file);
    body.append('description', description);
    if (date) body.append('date', date);
    // Only needed while the checklist is checked out (RDO): the offline check-in flush sends it to
    // prove it holds the checkout, since the RDO → ACT flip happens later in the document save.
    if (deviceCheckoutGuid) body.append('deviceCheckoutGuid', deviceCheckoutGuid);
    return this.doRequest<void>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/photos',
      path: { checklistId },
      body,
    });
  }

  /** Remove one photo (row + stored bytes). Resolves to `void` (204). */
  deletePhoto(
    checklistId: string,
    photoId: string,
    deviceCheckoutGuid?: string,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/chr/checklists/{checklistId}/photos/{photoId}',
      path: { checklistId, photoId },
      query: deviceCheckoutGuid ? { deviceCheckoutGuid } : undefined,
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

  /**
   * Release an offline checkout (RDO → ACT) held by this device, so the online copy is editable again.
   * The deviceCheckoutGuid proves ownership; the backend no-ops if it doesn't match / isn't checked out.
   */
  release(checklistId: string, deviceCheckoutGuid: string): CancelablePromise<CheckList> {
    return this.doRequest<CheckList>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/release',
      path: { checklistId },
      body: { deviceCheckoutGuid },
      mediaType: 'application/json',
    });
  }
}
