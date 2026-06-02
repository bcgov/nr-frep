import type {
  BiodiversityOpening,
  BioPlot,
  BioPlotRow,
  BioStratum,
  BioStratumRow,
  ProtocolChecklist,
  RiparianFieldData,
  RiparianFinalComments,
  RiparianOtherIndicators,
  RiparianQuestions,
  RiparianSpecificImpacts,
  RiparianStreamOpening,
  WaterAssessment,
  WaterRange,
  WaterSampleArea,
  WaterSampleSite,
} from '@/types/protocolChecklist';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

type ProtocolBackendCode = 'bio' | 'rip' | 'wat';

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

  nextStratumNumber(): CancelablePromise<{ stratumNumber: string }> {
    return this.doRequest<{ stratumNumber: string }>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/bio/strata-next-number',
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

  getRipStreamOpening(checklistId: string): CancelablePromise<RiparianStreamOpening> {
    return this.doRequest<RiparianStreamOpening>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/rip/{checklistId}/stream-opening',
      path: { checklistId },
    });
  }

  saveRipStreamOpening(
    checklistId: string,
    opening: RiparianStreamOpening,
  ): CancelablePromise<RiparianStreamOpening> {
    return this.doRequest<RiparianStreamOpening>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/rip/{checklistId}/stream-opening',
      path: { checklistId },
      body: opening,
      mediaType: 'application/json',
    });
  }

  getRipFinalComments(checklistId: string): CancelablePromise<RiparianFinalComments> {
    return this.doRequest<RiparianFinalComments>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/rip/{checklistId}/final-comments',
      path: { checklistId },
    });
  }

  saveRipFinalComments(
    checklistId: string,
    comments: RiparianFinalComments,
  ): CancelablePromise<RiparianFinalComments> {
    return this.doRequest<RiparianFinalComments>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/rip/{checklistId}/final-comments',
      path: { checklistId },
      body: comments,
      mediaType: 'application/json',
    });
  }

  getRipFieldData(checklistId: string): CancelablePromise<RiparianFieldData> {
    return this.doRequest<RiparianFieldData>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/rip/{checklistId}/field-data',
      path: { checklistId },
    });
  }

  saveRipFieldData(
    checklistId: string,
    data: RiparianFieldData,
  ): CancelablePromise<RiparianFieldData> {
    return this.doRequest<RiparianFieldData>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/rip/{checklistId}/field-data',
      path: { checklistId },
      body: data,
      mediaType: 'application/json',
    });
  }

  getRipOtherIndicators(checklistId: string): CancelablePromise<RiparianOtherIndicators> {
    return this.doRequest<RiparianOtherIndicators>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/rip/{checklistId}/other-indicators',
      path: { checklistId },
    });
  }

  saveRipOtherIndicators(
    checklistId: string,
    data: RiparianOtherIndicators,
  ): CancelablePromise<RiparianOtherIndicators> {
    return this.doRequest<RiparianOtherIndicators>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/rip/{checklistId}/other-indicators',
      path: { checklistId },
      body: data,
      mediaType: 'application/json',
    });
  }

  getRipQuestions(checklistId: string): CancelablePromise<RiparianQuestions> {
    return this.doRequest<RiparianQuestions>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/rip/{checklistId}/questions',
      path: { checklistId },
    });
  }

  saveRipQuestions(
    checklistId: string,
    data: RiparianQuestions,
  ): CancelablePromise<RiparianQuestions> {
    return this.doRequest<RiparianQuestions>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/rip/{checklistId}/questions',
      path: { checklistId },
      body: data,
      mediaType: 'application/json',
    });
  }

  getRipSpecificImpacts(checklistId: string): CancelablePromise<RiparianSpecificImpacts> {
    return this.doRequest<RiparianSpecificImpacts>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/rip/{checklistId}/specific-impacts',
      path: { checklistId },
    });
  }

  saveRipSpecificImpacts(
    checklistId: string,
    data: RiparianSpecificImpacts,
  ): CancelablePromise<RiparianSpecificImpacts> {
    return this.doRequest<RiparianSpecificImpacts>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/rip/{checklistId}/specific-impacts',
      path: { checklistId },
      body: data,
      mediaType: 'application/json',
    });
  }

  getWaterSampleArea(checklistId: string): CancelablePromise<WaterSampleArea> {
    return this.doRequest<WaterSampleArea>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/wtr/{checklistId}/sample-area',
      path: { checklistId },
    });
  }

  saveWaterSampleArea(
    checklistId: string,
    data: WaterSampleArea,
  ): CancelablePromise<WaterSampleArea> {
    return this.doRequest<WaterSampleArea>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/wtr/{checklistId}/sample-area',
      path: { checklistId },
      body: data,
      mediaType: 'application/json',
    });
  }

  getWaterSampleSite(checklistId: string): CancelablePromise<WaterSampleSite> {
    return this.doRequest<WaterSampleSite>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/wtr/{checklistId}/sample-site',
      path: { checklistId },
    });
  }

  saveWaterSampleSite(
    checklistId: string,
    data: WaterSampleSite,
  ): CancelablePromise<WaterSampleSite> {
    return this.doRequest<WaterSampleSite>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/wtr/{checklistId}/sample-site',
      path: { checklistId },
      body: data,
      mediaType: 'application/json',
    });
  }

  getWaterAssessment(sampleSiteId: string): CancelablePromise<WaterAssessment> {
    return this.doRequest<WaterAssessment>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/wtr/site/{sampleSiteId}/assessment',
      path: { sampleSiteId },
    });
  }

  saveWaterAssessment(
    sampleSiteId: string,
    data: WaterAssessment,
  ): CancelablePromise<WaterAssessment> {
    return this.doRequest<WaterAssessment>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/wtr/site/{sampleSiteId}/assessment',
      path: { sampleSiteId },
      body: data,
      mediaType: 'application/json',
    });
  }

  getWaterRange(sampleSiteId: string): CancelablePromise<WaterRange> {
    return this.doRequest<WaterRange>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/wtr/site/{sampleSiteId}/range',
      path: { sampleSiteId },
    });
  }

  saveWaterRange(sampleSiteId: string, data: WaterRange): CancelablePromise<WaterRange> {
    return this.doRequest<WaterRange>(this.config, {
      method: 'PUT',
      url: '/v1/protocol-checklists/wtr/site/{sampleSiteId}/range',
      path: { sampleSiteId },
      body: data,
      mediaType: 'application/json',
    });
  }
}
