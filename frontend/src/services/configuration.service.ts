import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';

import type { MasterListYear, OrgUnit, Protocol } from '@/types/configuration';

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
}
