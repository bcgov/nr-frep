import type { APIConfig } from '@/config/api/types';

import { ensureSessionFresh } from '@/context/auth/refreshSession';
import { env } from '@/env';
import { AcceptedSitesService } from '@/services/acceptedSites.service';
import { ChrChecklistService } from '@/services/chrChecklist.service';
import { ConfigurationService } from '@/services/configuration.service';
import { ensureFreshUser } from '@/services/keycloak';
import { MasterListAdminService } from '@/services/masterListAdmin.service';
import { withBioReferenceCache } from '@/services/offline/bioConfigurationFacade';
import { withBioOffline } from '@/services/offline/bioFacade';
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
 * Resolve the access token for the current session. Renews a near-expiry token
 * first (via {@link ensureSessionFresh}) so a write fired after the form has
 * been open a while doesn't race a 401; if the session is fully expired,
 * {@link ensureSessionFresh} signs out and redirects to login. Returns an empty
 * string when no session exists so the request fires unauthenticated and the
 * backend can respond with 401.
 */
BackendApiConfig.TOKEN = async () => {
  try {
    await ensureSessionFresh();
    const current = await ensureFreshUser();
    return current?.access_token ?? '';
  } catch {
    return '';
  }
};

/**
 * The SLR client WITHOUT the offline facade.
 *
 * For the check-in, and only the check-in. The facade routes a write to the local copy whenever one
 * exists — which is exactly the situation during a check-in, since the copy is removed only at the
 * very end. So the flush handed its queued attachment ops back to the facade, which dutifully
 * queued them again and returned; the op was then marked synced, nothing reached the server, and a
 * fresh pending op was left behind. A file deleted offline stayed on the server, and one added
 * offline never arrived.
 *
 * The check-in is the one caller that means "talk to the server", never "serve this from the
 * device". Everything else should keep using the facaded `API.protocolChecklist`.
 */
export const protocolChecklistDirect = new ProtocolChecklistService(BackendApiConfig);

const serviceConstructors = {
  user: new UserService(BackendApiConfig),
  acceptedSites: new AcceptedSitesService(BackendApiConfig),
  // Wrapped so the Bio reference dropdowns (species, decay classes, strata types, BEC) keep
  // working with no connectivity. Network first, cache as the fallback.
  configuration: withBioReferenceCache(new ConfigurationService(BackendApiConfig)),
  randomList: new RandomListService(BackendApiConfig),
  siteDetail: new SiteDetailService(BackendApiConfig),
  // Wrapped so the Bio views work unchanged whether or not the checklist is checked out to this
  // device: reads and writes are served from the local copy when one exists, and pass straight
  // through to the real client otherwise.
  protocolChecklist: withBioOffline(protocolChecklistDirect),
  chrChecklist: new ChrChecklistService(BackendApiConfig),
  search: new SearchService(BackendApiConfig),
  masterListAdmin: new MasterListAdminService(BackendApiConfig),
} as const;

type ExternalApiType = {
  [K in keyof typeof serviceConstructors]: (typeof serviceConstructors)[K];
};

const API: ExternalApiType = serviceConstructors;

export default API;
