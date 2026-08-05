import { fetchAuthSession } from 'aws-amplify/auth';

import { ensureSessionFresh } from '@/context/auth/refreshSession';
import { BackendApiConfig } from '@/services/APIs';

/**
 * Report-generation client. Models the nr-fspts `services/reports.ts`: POST a
 * JSON request to `/api/v1/reports/{reportId}`, receive the rendered binary, and
 * download it (CSV) or open it in a new tab (PDF). The filename comes from the
 * backend `Content-Disposition` header.
 *
 * <p>Auth + base URL reuse the shared {@link BackendApiConfig} (Cognito bearer
 * token + the same `/api` base every service uses), so this stays consistent
 * with the `doRequest`-based JSON services without forcing a blob response
 * through that pipeline.</p>
 */

export type ReportFormat = 'pdf' | 'csv';

/** Mirrors the backend `ReportRequest` record. All fields optional. */
export interface ReportRequestPayload {
  startDate?: string | null; // yyyy-MM-dd
  endDate?: string | null; // yyyy-MM-dd
  orgUnitNo?: string | null;
  orgUnitCode?: string | null; // '*' = all
  masterListYear?: string | null; // '*' = all
  resourceValueStatus?: string | null; // '*' = all
  checklistStatus?: string | null; // '*' = all
  clientNumber?: string | null;
  licenceNumber?: string | null;
  openingId?: string | null;
  sortColumn?: string | null;
  format?: ReportFormat | null;
}

export interface ReportResponse {
  blob: Blob;
  filename: string;
  contentType: string;
}

// Resolve the Cognito access token the same way APIs.ts does — refresh a
// near-expiry session first, then read the access token. Returns '' when there's
// no session so the request fires unauthenticated and the backend returns 401.
const resolveToken = async (): Promise<string> => {
  try {
    await ensureSessionFresh();
    const { tokens } = (await fetchAuthSession()) ?? {};
    return tokens?.accessToken?.toString() ?? '';
  } catch {
    return '';
  }
};

/** Drops null/undefined/blank fields so the backend treats them as "no filter". */
const sanitize = (payload: ReportRequestPayload): Record<string, string> => {
  const clean: Record<string, string> = {};
  Object.entries(payload).forEach(([key, value]) => {
    if (value == null) return;
    const trimmed = String(value).trim();
    if (trimmed !== '') clean[key] = trimmed;
  });
  return clean;
};

const fallbackFilename = (reportId: string, format: ReportFormat) => `report-${reportId}.${format}`;

/** Parses RFC 5987 (`filename*=UTF-8''…`) then the simple `filename="…"` form. */
const extractFilename = (response: Response, reportId: string, format: ReportFormat): string => {
  const disposition = response.headers.get('content-disposition') ?? '';
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1]);
    } catch {
      /* fall through */
    }
  }
  const simple = /filename="?([^";]+)"?/i.exec(disposition);
  return simple?.[1] ?? fallbackFilename(reportId, format);
};

const postReport = async (
  url: string,
  reportId: string,
  payload: ReportRequestPayload,
  fallbackFormat: ReportFormat,
): Promise<ReportResponse> => {
  const token = await resolveToken();
  const response = await fetch(url, {
    method: 'POST',
    credentials: BackendApiConfig.WITH_CREDENTIALS ? 'include' : 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/octet-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(sanitize(payload)),
  });

  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new Error(`Report request failed (${response.status})${detail ? `: ${detail}` : ''}`);
  }

  return {
    blob: await response.blob(),
    filename: extractFilename(response, reportId, fallbackFormat),
    contentType: response.headers.get('content-type') ?? 'application/octet-stream',
  };
};

/** Authenticated GET for a report file (binary), parsing the filename from `Content-Disposition`. */
const getReportFile = async (
  url: string,
  reportId: string,
  format: ReportFormat,
): Promise<ReportResponse> => {
  const token = await resolveToken();
  const response = await fetch(url, {
    method: 'GET',
    credentials: BackendApiConfig.WITH_CREDENTIALS ? 'include' : 'same-origin',
    headers: {
      Accept: 'application/octet-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });

  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new Error(`Report request failed (${response.status})${detail ? `: ${detail}` : ''}`);
  }

  return {
    blob: await response.blob(),
    filename: extractFilename(response, reportId, format),
    contentType: response.headers.get('content-type') ?? 'application/octet-stream',
  };
};

/** FREP100 District Random List CSV export: `GET /api/v1/reports/random-list/csv`. */
export const requestRandomListCsv = (
  effectiveYear: string,
  orgUnit?: string,
): Promise<ReportResponse> => {
  const params = new URLSearchParams({ effectiveYear });
  if (orgUnit) params.set('orgUnit', orgUnit);
  return getReportFile(
    `${BackendApiConfig.BASE}/v1/reports/random-list/csv?${params.toString()}`,
    'random-list',
    'csv',
  );
};

/**
 * FREP400 Checklist Search CSV export: `GET /api/v1/reports/checklist-search/csv`. Pass the same
 * filter object used for the search; blank/undefined values are dropped.
 */
export const requestChecklistSearchCsv = (
  filters: Record<string, string | undefined | null>,
): Promise<ReportResponse> => {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value != null && String(value).trim() !== '') params.set(key, String(value).trim());
  });
  const query = params.toString();
  return getReportFile(
    `${BackendApiConfig.BASE}/v1/reports/checklist-search/csv${query ? `?${query}` : ''}`,
    'checklist-search',
    'csv',
  );
};

/** Jasper template report (PDF/CSV): `POST /api/v1/reports/{reportId}`. */
export const requestReport = (
  reportId: string,
  payload: ReportRequestPayload,
): Promise<ReportResponse> =>
  postReport(
    `${BackendApiConfig.BASE}/v1/reports/${encodeURIComponent(reportId)}`,
    reportId,
    payload,
    payload.format ?? 'pdf',
  );

/** CSV data-extract report: `POST /api/v1/reports/csv/{reportName}` (always returns CSV). */
export const requestCsvReport = (
  reportName: string,
  payload: ReportRequestPayload,
): Promise<ReportResponse> =>
  postReport(
    `${BackendApiConfig.BASE}/v1/reports/csv/${encodeURIComponent(reportName)}`,
    reportName,
    { ...payload, format: 'csv' },
    'csv',
  );

/** Saves a blob to disk via a temporary anchor (used for CSV). */
export const triggerBrowserDownload = (blob: Blob, filename: string): void => {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
};

/** Opens a blob in a new tab (used for PDF). The object URL is left for the tab to read. */
export const openBlobInNewTab = (blob: Blob): void => {
  const url = window.URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener,noreferrer');
};
