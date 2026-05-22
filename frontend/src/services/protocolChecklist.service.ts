import type { ProtocolChecklist } from '@/types/protocolChecklist';

import { CancelablePromise } from '@/config/api/CancelablePromise';
import { HttpClient, type APIConfig } from '@/config/api/types';
import { env } from '@/env';

export class ProtocolChecklistService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  getChecklist(
    protocolBackendCode: 'bio' | 'rip' | 'wat',
    checklistId: string,
  ): CancelablePromise<ProtocolChecklist> {
    return this.doRequest<ProtocolChecklist>(this.config, {
      method: 'GET',
      url: '/v1/protocol-checklists/{protocolType}/{checklistId}',
      path: { protocolType: protocolBackendCode, checklistId },
    });
  }
}

const LEGACY_PATH_BY_PROTOCOL: Record<'bio' | 'rip' | 'wat', string> = {
  bio: 'frep210BIOOpeningAction.do',
  rip: 'frep230RIPStreamOpenAction.do',
  wat: 'frep250WtrSampleAreaAction.do',
};

export function buildLegacyChecklistUrl(
  protocolBackendCode: 'bio' | 'rip' | 'wat',
  checklistId: string,
): string {
  const base = (env.VITE_LEGACY_APP_URL ?? '/ext/frep').replace(/\/$/, '');
  return `${base}/${LEGACY_PATH_BY_PROTOCOL[protocolBackendCode]}?checklistId=${encodeURIComponent(checklistId)}`;
}
