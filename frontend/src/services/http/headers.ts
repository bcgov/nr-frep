import { fetchAuthSession } from 'aws-amplify/auth';

import { getCookie } from '@/context/auth/authUtils';

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

const getCsrfToken = (): string | null => {
  const raw = getCookie('XSRF-TOKEN');
  return raw ? decodeURIComponent(raw) : null;
};

const getAccessToken = async (): Promise<string | undefined> => {
  try {
    const session = await fetchAuthSession();
    return session.tokens?.accessToken?.toString();
  } catch {
    return undefined;
  }
};

/**
 * Build a header map for an authorized backend call. Layers, in order:
 *   1. Bearer access token (if a Cognito session exists).
 *   2. X-XSRF-TOKEN echoed back from the XSRF cookie set by the backend.
 *   3. Any per-call header overrides supplied by the caller.
 */
export const buildAuthorizedHeaders = async (
  ...headerSets: Array<HeadersInit | undefined>
): Promise<HeaderRecord> => {
  const baseHeaders: HeaderRecord = {};

  const accessToken = await getAccessToken();
  if (accessToken) {
    baseHeaders.Authorization = `Bearer ${accessToken}`;
  }

  const csrfToken = getCsrfToken();
  if (csrfToken) {
    baseHeaders['X-XSRF-TOKEN'] = csrfToken;
  }

  return headerSets.reduce<HeaderRecord>((acc, headerSet) => {
    return { ...acc, ...normalizeHeaders(headerSet) };
  }, baseHeaders);
};
