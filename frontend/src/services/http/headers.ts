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

// LOCAL DEV: Cognito bearer token and CSRF headers disabled.
export const buildAuthorizedHeaders = async (
  ...headerSets: Array<HeadersInit | undefined>
): Promise<HeaderRecord> => {
  return headerSets.reduce<HeaderRecord>((acc, headerSet) => {
    return { ...acc, ...normalizeHeaders(headerSet) };
  }, {});
};

/*
 * --- Cognito / CSRF headers (re-enable before deploying) ---
 *
 * import { fetchAuthSession } from 'aws-amplify/auth';
 *
 * const getCsrfToken = (): string | null => { ... };
 * const getAccessToken = async (): Promise<string | undefined> => { ... };
 * ... attach Authorization and X-XSRF-TOKEN in buildAuthorizedHeaders ...
 */
