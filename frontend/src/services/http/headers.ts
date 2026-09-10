import { getUserManager } from '@/services/keycloak';

export type HeaderRecord = Record<string, string>;

const normalizeHeaders = (headers?: HeadersInit): HeaderRecord => {
  if (!headers) return {};

  if (headers instanceof Headers) {
    const normalized: HeaderRecord = {};
    headers.forEach((value, key) => {
      normalized[key] = value;
    });
    return normalized;
  }

  if (Array.isArray(headers)) {
    return headers.reduce<HeaderRecord>((acc, [key, value]) => {
      acc[key] = value;
      return acc;
    }, {});
  }

  return Object.entries(headers).reduce<HeaderRecord>((acc, [key, value]) => {
    if (typeof value === 'undefined' || value === null) return acc;
    acc[key] = String(value);
    return acc;
  }, {});
};

const getAccessToken = async (): Promise<string | undefined> => {
  try {
    const current = await getUserManager().getUser();
    return current?.access_token;
  } catch {
    return undefined;
  }
};

/**
 * Build a header map for an authorized backend call. Layers, in order:
 *   1. Bearer access token (if a session exists).
 *   2. Any per-call header overrides supplied by the caller.
 *
 * <p>No CSRF header. The API authenticates solely on the Bearer token and holds no session, so a
 * cross-site request carries no ambient credential and is rejected as unauthenticated — see
 * `SecurityConfiguration` on the backend.
 */
export const buildAuthorizedHeaders = async (
  ...headerSets: Array<HeadersInit | undefined>
): Promise<HeaderRecord> => {
  const baseHeaders: HeaderRecord = {};

  const accessToken = await getAccessToken();
  if (accessToken) {
    baseHeaders.Authorization = `Bearer ${accessToken}`;
  }

  return headerSets.reduce<HeaderRecord>((acc, headerSet) => {
    return { ...acc, ...normalizeHeaders(headerSet) };
  }, baseHeaders);
};
