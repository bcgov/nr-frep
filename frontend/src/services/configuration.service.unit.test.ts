import { describe, expect, it, vi } from 'vitest';

import { ConfigurationService } from './configuration.service';

import type { APIConfig } from '@/config/api/types';

const config: APIConfig = {
  BASE: '/api',
  VERSION: '0',
  WITH_CREDENTIALS: true,
  CREDENTIALS: 'include',
  TOKEN: undefined,
  USERNAME: undefined,
  PASSWORD: undefined,
  HEADERS: undefined,
  ENCODE_PATH: undefined,
};

function mockResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: {},
  };
}

describe('ConfigurationService', () => {
  it('hits the master-list-years endpoint', async () => {
    const service = new ConfigurationService(config);
    const request = vi.fn().mockResolvedValue(mockResponse([]));
    service.axiosInstance.request = request;

    await service.getMasterListYears();

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/api/v1/configuration/master-list-years', method: 'GET' }),
    );
  });

  it('hits the org-units endpoint', async () => {
    const service = new ConfigurationService(config);
    const request = vi.fn().mockResolvedValue(mockResponse([]));
    service.axiosInstance.request = request;

    await service.getOrgUnits();

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/api/v1/configuration/org-units', method: 'GET' }),
    );
  });

  it('hits the protocols endpoint', async () => {
    const service = new ConfigurationService(config);
    const request = vi.fn().mockResolvedValue(mockResponse([]));
    service.axiosInstance.request = request;

    await service.getProtocols();

    expect(request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/api/v1/configuration/protocols', method: 'GET' }),
    );
  });
});
