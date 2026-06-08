import type {
  CodeOption,
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

  /** FREP checklist answer options (Yes/No/etc.) for indicator dropdowns. */
  getChecklistAnswers(exclude?: string): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/checklist-answers',
      query: exclude ? { exclude } : undefined,
    });
  }
}
