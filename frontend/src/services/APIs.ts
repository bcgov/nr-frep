import { fetchAuthSession } from 'aws-amplify/auth';

import type { APIConfig } from '@/config/api/types';

import { env } from '@/env';
import { AcceptedSitesService } from '@/services/acceptedSites.service';
import { ConfigurationService } from '@/services/configuration.service';
import { MasterListAdminService } from '@/services/masterListAdmin.service';
import { ProtocolChecklistService } from '@/services/protocolChecklist.service';
import { RandomListService } from '@/services/randomList.service';
import { SearchService } from '@/services/search.service';
import { SiteDetailService } from '@/services/siteDetail.service';
import { UserService } from '@/services/users.service';

const basePath = (env.VITE_BASE_PATH ?? '').replace(/\/$/, '');

export const BackendApiConfig: APIConfig = {
  BASE: env.VITE_BACKEND_URL || `${basePath}/api`,
  VERSION: '0',
  WITH_CREDENTIALS: true,
  CREDENTIALS: 'include',
  TOKEN: undefined,
  USERNAME: undefined,
  PASSWORD: undefined,
  HEADERS: undefined,
  ENCODE_PATH: undefined,
};

/**
 * Resolve the Cognito access token for the current session. Returns an empty
 * string when no session exists so the request fires unauthenticated and the
 * backend can respond with 401.
 */
BackendApiConfig.TOKEN = async () => {
  try {
    const { tokens } = (await fetchAuthSession()) ?? {};
    return tokens?.accessToken?.toString() ?? '';
  } catch {
    return '';
  }
};

const serviceConstructors = {
  user: new UserService(BackendApiConfig),
  acceptedSites: new AcceptedSitesService(BackendApiConfig),
  configuration: new ConfigurationService(BackendApiConfig),
  randomList: new RandomListService(BackendApiConfig),
  siteDetail: new SiteDetailService(BackendApiConfig),
  protocolChecklist: new ProtocolChecklistService(BackendApiConfig),
  search: new SearchService(BackendApiConfig),
  masterListAdmin: new MasterListAdminService(BackendApiConfig),
} as const;

type ExternalApiType = {
  [K in keyof typeof serviceConstructors]: (typeof serviceConstructors)[K];
};

const API: ExternalApiType = serviceConstructors;

export default API;
