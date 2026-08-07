import type {
  AttachmentContent,
  AttachmentPageResponse,
  BiodiversityOpening,
  BioPlot,
  BioPlotRow,
  BioStratum,
  BioStratumRow,
  ProtocolChecklist,
  RiparianNotes,
  StratumComputed,
} from '@/types/protocolChecklist';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

// Riparian (rip) / Water (wat) are out of scope; the shared {protocol} segment is bio-only now.
type ProtocolBackendCode = 'bio';

export class ProtocolChecklistService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getChecklist(
    protocolBackendCode: ProtocolBackendCode,
    checklistId: string,
  ): CancelablePromise<ProtocolChecklist> {
    return this.doRequest<ProtocolChecklist>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/{protocolType}/{checklistId}',
      path: { protocolType: protocolBackendCode, checklistId },
    });
  }

  /** Submit a checklist; the API responds 400 with `{ validationErrors: string[] }` on failure. */
  submit(protocolBackendCode: ProtocolBackendCode, checklistId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'POST',
      url: '/v1/protocol-checklists/{protocolType}/{checklistId}/submit',
      path: { protocolType: protocolBackendCode, checklistId },
    });
  }

  unsubmit(protocolBackendCode: ProtocolBackendCode, checklistId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'POST',
      url: '/v1/protocol-checklists/{protocolType}/{checklistId}/unsubmit',
      path: { protocolType: protocolBackendCode, checklistId },
    });
  }

  getBiodiversityOpening(checklistId: string): CancelablePromise<BiodiversityOpening> {
    return this.doRequest<BiodiversityOpening>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/bio/{checklistId}/opening',
      path: { checklistId },
    });
  }

  saveBiodiversityOpening(
    checklistId: string,
    opening: BiodiversityOpening,
  ): CancelablePromise<BiodiversityOpening> {
    return this.doRequest<BiodiversityOpening>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/bio/{checklistId}/opening',
      path: { checklistId },
      body: opening,
      mediaType: 'application/json',
    });
  }

  listBioStrata(checklistId: string): CancelablePromise<BioStratumRow[]> {
    return this.doRequest<BioStratumRow[]>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/bio/{checklistId}/strata',
      path: { checklistId },
    });
  }

  getBioStratum(stratumId: string): CancelablePromise<BioStratum> {
    return this.doRequest<BioStratum>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/bio/strata/{stratumId}',
      path: { stratumId },
    });
  }

  /** Read-only NAR + plots-completed for a stratum (FREP211 header). */
  getStratumComputed(stratumId: string): CancelablePromise<StratumComputed> {
    return this.doRequest<StratumComputed>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/bio/strata/{stratumId}/computed',
      path: { stratumId },
    });
  }

  getNewStratumComputed(checklistId: string): CancelablePromise<StratumComputed> {
    return this.doRequest<StratumComputed>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/bio/{checklistId}/new-stratum-computed',
      path: { checklistId },
    });
  }

  saveBioStratum(checklistId: string, stratum: BioStratum): CancelablePromise<BioStratum> {
    return this.doRequest<BioStratum>(this.config, {
      method: 'POST',
      url: '/v1/protocol-checklists/bio/{checklistId}/strata',
      path: { checklistId },
      body: stratum,
      mediaType: 'application/json',
    });
  }

  deleteBioStratum(stratumId: string, revisionCount: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/protocol-checklists/bio/strata/{stratumId}',
      path: { stratumId },
      query: { revisionCount },
    });
  }

  listBioPlots(stratumId: string): CancelablePromise<BioPlotRow[]> {
    return this.doRequest<BioPlotRow[]>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/bio/strata/{stratumId}/plots',
      path: { stratumId },
    });
  }

  getBioPlot(plotId: string): CancelablePromise<BioPlot> {
    return this.doRequest<BioPlot>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/bio/plots/{plotId}',
      path: { plotId },
    });
  }

  saveBioPlot(stratumId: string, plot: BioPlot): CancelablePromise<BioPlot> {
    return this.doRequest<BioPlot>(this.config, {
      method: 'POST',
      url: '/v1/protocol-checklists/bio/strata/{stratumId}/plots',
      path: { stratumId },
      body: plot,
      mediaType: 'application/json',
    });
  }

  deleteBioPlot(plotId: string, revisionCount: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/protocol-checklists/bio/plots/{plotId}',
      path: { plotId },
      query: { revisionCount },
    });
  }

  getNotes(protocol: string, checklistId: string): CancelablePromise<RiparianNotes> {
    return this.doRequest<RiparianNotes>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/{protocol}/{checklistId}/notes',
      path: { protocol, checklistId },
    });
  }

  saveNotes(
    protocol: string,
    checklistId: string,
    notes: RiparianNotes,
  ): CancelablePromise<RiparianNotes> {
    return this.doRequest<RiparianNotes>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/{protocol}/{checklistId}/notes',
      path: { protocol, checklistId },
      body: notes,
      mediaType: 'application/json',
    });
  }

  /** One page of attachment metadata. Sizes come from object storage, filled in server-side. */
  getAttachments(
    protocol: string,
    checklistId: string,
    page = 0,
    size = 10,
  ): CancelablePromise<AttachmentPageResponse> {
    return this.doRequest<AttachmentPageResponse>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/{protocol}/{checklistId}/attachments',
      path: { protocol, checklistId },
      query: { page, size },
    });
  }

  getAttachmentContent(
    protocol: string,
    checklistId: string,
    attachmentId: string,
  ): CancelablePromise<AttachmentContent> {
    return this.doRequest<AttachmentContent>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/{protocol}/{checklistId}/attachments/{attachmentId}/content',
      path: { protocol, checklistId, attachmentId },
    });
  }

  /**
   * Upload one attachment as `multipart/form-data`: the raw `File` goes on the wire, with no base64
   * step. `mediaType` is deliberately unset — the browser must supply `multipart/form-data` with its
   * own boundary (see `getHeaders` in `config/api/request.ts`). The description rides in the body
   * rather than the query string because it is free text up to 2000 chars.
   *
   * Resolves to `void` (204): the caller re-fetches the list.
   */
  uploadAttachment(
    protocol: string,
    checklistId: string,
    file: File,
    description?: string,
  ): CancelablePromise<void> {
    const body = new FormData();
    body.append('file', file);
    if (description && description.trim()) {
      body.append('description', description.trim());
    }
    return this.doRequest<void>(this.config, {
      method: 'POST',
      url: '/v1/protocol-checklists/{protocol}/{checklistId}/attachments',
      path: { protocol, checklistId },
      body,
    });
  }

  /** Delete an attachment. Resolves to `void` (204); the caller re-fetches the list. */
  deleteAttachment(
    protocol: string,
    checklistId: string,
    attachmentId: string,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/protocol-checklists/{protocol}/{checklistId}/attachments/{attachmentId}',
      path: { protocol, checklistId, attachmentId },
    });
  }
}
