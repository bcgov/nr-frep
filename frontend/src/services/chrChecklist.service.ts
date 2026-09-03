import type {
  PhotoPageResponse,
  CheckList,
  Feature,
  FeatureSaveResponse,
} from '@/types/chrChecklist';

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
  /**
   * `featureId` is last rather than in the server's parameter order (`date`, `featureId`,
   * `deviceCheckoutGuid`) deliberately: both are optional strings, so slotting it before the guid
   * would let an existing five-argument call pass the guid as the feature id and still compile.
   */
  addPhoto(
    checklistId: string,
    file: File,
    description: string,
    date?: string,
    deviceCheckoutGuid?: string,
    featureId?: string,
  ): CancelablePromise<void> {
    const body = new FormData();
    body.append('file', file);
    body.append('description', description);
    if (date) body.append('date', date);
    // Only needed while the checklist is checked out (RDO): the offline check-in flush sends it to
    // prove it holds the checkout, since the RDO → ACT flip happens later in the document save.
    if (deviceCheckoutGuid) body.append('deviceCheckoutGuid', deviceCheckoutGuid);
    // Which feature the photo documents. Write-once, at upload — there is no metadata-edit flow.
    if (featureId) body.append('featureId', featureId);
    return this.doRequest<void>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/photos',
      path: { checklistId },
      body,
    });
  }

  /**
   * Create a composite over two or more features.
   *
   * The anchor does not exist until this call, so it is described rather than addressed. Members
   * arrive two ways in the same request because the dialog is one gesture: `memberIds` for features
   * that already exist, `newMembers` for the ones typed into the dialog.
   */
  createComposite(
    checklistId: string,
    revisionCount: string,
    anchor: Feature,
    memberIds: string[],
    newMembers: Feature[],
  ): CancelablePromise<FeatureSaveResponse> {
    return this.doRequest<FeatureSaveResponse>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/composites',
      path: { checklistId },
      body: { revisionCount, anchor, memberIds, newMembers },
    });
  }

  /**
   * Dissolve a composite. The anchor row goes either way and its members are released;
   * `deleteMemberIds` names the ones to delete instead — empty keeps them all.
   */
  ungroupComposite(
    checklistId: string,
    anchorId: string,
    revisionCount: string,
    deleteMemberIds: string[],
  ): CancelablePromise<FeatureSaveResponse> {
    return this.doRequest<FeatureSaveResponse>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/composites/{anchorId}/ungroup',
      path: { checklistId, anchorId },
      body: { revisionCount, deleteMemberIds },
    });
  }

  /**
   * Re-point an existing composite at a new set of members, and update its class and source.
   *
   * `memberIds` is the complete set: a feature that was under this composite and is absent is
   * released. It may name a feature currently under a different composite — moving one across is
   * what the members dialog is for.
   */
  updateComposite(
    checklistId: string,
    anchorId: string,
    revisionCount: string,
    featureDescriptionCode: string | undefined,
    featureInfoSourceCode: string | undefined,
    memberIds: string[],
    newMembers: Feature[],
  ): CancelablePromise<FeatureSaveResponse> {
    return this.doRequest<FeatureSaveResponse>(this.config, {
      method: 'PUT',
      url: '/v1/chr/checklists/{checklistId}/composites/{anchorId}',
      path: { checklistId, anchorId },
      body: {
        revisionCount,
        featureDescriptionCode,
        featureInfoSourceCode,
        memberIds,
        newMembers,
      },
    });
  }

  /**
   * Add one standalone feature — the editor's Save on a feature the server has never seen.
   *
   * `/new` rather than a plain POST to the collection because `POST …/features` is already the
   * whole-section save, which cannot move: cached bundles still call it, and it is the fallback
   * every per-feature write uses on an offline copy.
   */
  createFeature(
    checklistId: string,
    revisionCount: string,
    feature: Feature,
  ): CancelablePromise<FeatureSaveResponse> {
    return this.doRequest<FeatureSaveResponse>(this.config, {
      method: 'POST',
      url: '/v1/chr/checklists/{checklistId}/features/new',
      path: { checklistId },
      body: { revisionCount, feature },
    });
  }

  /**
   * Save one feature's own fields — the editor's Save, rather than resending every feature.
   *
   * Relationships are not sent and are not touched: associations have their own endpoint, and
   * composite membership stays as stored (the editor cannot change it).
   */
  saveFeature(
    checklistId: string,
    featureId: string,
    revisionCount: string,
    feature: Feature,
  ): CancelablePromise<FeatureSaveResponse> {
    return this.doRequest<FeatureSaveResponse>(this.config, {
      method: 'PUT',
      url: '/v1/chr/checklists/{checklistId}/features/{featureId}',
      path: { checklistId, featureId },
      body: { revisionCount, feature },
    });
  }

  /**
   * Replace the set of features one feature is associated with.
   *
   * `featureIds` is the complete set, not a delta — whatever is absent is unlinked. Targets are
   * given by id, not by feature label: the label is a display value, and resolving references
   * through it is what silently dropped links when a feature was renamed.
   *
   * The server writes both directions, so the response names the partners as well as the subject.
   */
  saveFeatureAssociations(
    checklistId: string,
    featureId: string,
    revisionCount: string,
    featureIds: string[],
  ): CancelablePromise<FeatureSaveResponse> {
    return this.doRequest<FeatureSaveResponse>(this.config, {
      method: 'PUT',
      url: '/v1/chr/checklists/{checklistId}/features/{featureId}/associations',
      path: { checklistId, featureId },
      body: { revisionCount, featureIds },
    });
  }

  /**
   * Remove one feature, with everything that hangs off it. Resolves to `void` (204).
   *
   * `revisionCount` is the checklist's optimistic-lock token, as a query parameter because DELETE
   * carries no body — the same shape the BIO stratum and plot deletes use. A stale token is rejected
   * exactly as it would be on a section save.
   */
  deleteFeature(
    checklistId: string,
    featureId: string,
    revisionCount: string,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/chr/checklists/{checklistId}/features/{featureId}',
      path: { checklistId, featureId },
      query: { revisionCount },
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
