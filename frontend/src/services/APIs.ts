import { env } from '@/env';
import { UserService } from '@/services/users.service';

import type { APIConfig } from '@/config/api/types';

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

// LOCAL DEV: Cognito token injection disabled.
BackendApiConfig.TOKEN = async () => '';

/*
 * --- Cognito token for API calls (re-enable before deploying) ---
 *
 * import { fetchAuthSession } from 'aws-amplify/auth';
 *
 * BackendApiConfig.TOKEN = async () => {
 *   const { tokens } = (await fetchAuthSession()) ?? {};
 *   return tokens?.accessToken?.toString() ?? '';
 * };
 */

const serviceConstructors = {
  user: new UserService(BackendApiConfig),
} as const;

type ExternalApiType = {
  [K in keyof typeof serviceConstructors]: (typeof serviceConstructors)[K];
};

const API: ExternalApiType = serviceConstructors;

export default API;
