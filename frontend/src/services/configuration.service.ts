import type {
  BecRow,
  CodeOption,
  EvaluatorSearchParams,
  EvaluatorSearchResult,
  MasterListYear,
  OrgUnit,
  Protocol,
  RejectionReason,
} from '@/types/configuration';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

/**
 * Reference-data API client for filter dropdowns.
 *
 * <p>Backed by `/api/v1/configuration/*` endpoints.
 */
export class ConfigurationService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getMasterListYears(): CancelablePromise<MasterListYear[]> {
    return this.doRequest<MasterListYear[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/master-list-years',
    });
  }

  getOrgUnits(): CancelablePromise<OrgUnit[]> {
    return this.doRequest<OrgUnit[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/org-units',
    });
  }

  getProtocols(): CancelablePromise<Protocol[]> {
    return this.doRequest<Protocol[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/protocols',
    });
  }

  getRejectionReasons(): CancelablePromise<RejectionReason[]> {
    return this.doRequest<RejectionReason[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/rejection-reasons',
    });
  }

  /** Riparian stream RMA class options for the FREP230 stream-class dropdowns. */
  getStreamClasses(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/stream-classes',
    });
  }

  /** Site-evaluation (rating) options for the FREP210 Opening "Rating" dropdown. */
  getSiteEvaluationCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/site-evaluation-codes',
    });
  }

  /** Biodiversity stratum-type options for the FREP211 "Stratum type" dropdown. */
  getStrataTypes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/strata-types',
    });
  }

  /** Resource-value status options for the biodiversity data-extract report filter. */
  getResourceValueStatusCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/resource-value-status-codes',
    });
  }

  /** Checklist-status options for the CHR data-extract report filter (FREPRPT022). */
  getChecklistStatusCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/checklist-status-codes',
    });
  }

  /** BEC catalogue search for the FREP211 BEC picker (all criteria optional). */
  searchBec(criteria: Partial<Record<string, string>>): CancelablePromise<BecRow[]> {
    return this.doRequest<BecRow[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/bec-search',
      query: criteria,
    });
  }

  /** FREP checklist answer options (Yes/No/etc.) for indicator dropdowns. */
  getChecklistAnswers(exclude?: string): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/checklist-answers',
      query: exclude ? { exclude } : undefined,
    });
  }

  /** Tree-species options for the FREP212 Stand / CWD "Spp." dropdowns. */
  getSpecies(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/species',
    });
  }

  /** Wildlife-tree decay-class options for the FREP212 Stand "WT Class" dropdown. */
  getWildlifeTreeDecay(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/wildlife-tree-decay',
    });
  }

  /** CWD decay-class options for the FREP212 Coarse Woody Debris "Decay Class" dropdown. */
  getCwdDecay(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/cwd-decay',
    });
  }

  /** Site-access options for the FREP301 Administration "Access type" dropdown. */
  getSiteAccessCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/site-access-codes',
    });
  }

  /** Evaluator options (the checklist's team) for the FREP212 "Evaluated By" dropdown. */
  getEvaluators(checklistId: string, protocol = 'SLB'): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/evaluators',
      query: { checklistId, protocol },
    });
  }

  /**
   * Searches IDIR users holding the FREP editor role (via FAM) for the Administration "Add
   * evaluator" modal — optional userId/first/last filters, paginated (FAM caps size at 100). Not
   * district-scoped (FAM has no district dimension); empty when the FAM lookup isn't configured.
   */
  searchEvaluators(params: EvaluatorSearchParams): CancelablePromise<EvaluatorSearchResult> {
    return this.doRequest<EvaluatorSearchResult>(this.config, {
      method: 'GET',
      url: '/v1/configuration/evaluator-search',
      query: {
        userId: params.userId,
        firstName: params.firstName,
        lastName: params.lastName,
        page: params.page,
        size: params.size,
      },
    });
  }
}
