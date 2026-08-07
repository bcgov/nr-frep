import { describe, it, expect, beforeEach, vi } from 'vitest';

import { ChrChecklistService } from './chrChecklist.service';

/**
 * The multipart field names are a contract with the backend's `@RequestParam` names, and every one
 * of them is optional there — a typo drops the value silently rather than failing. These assert the
 * body the service actually builds, which no test on either side of that boundary covers.
 */
describe('ChrChecklistService.addPhoto', () => {
  let service: ChrChecklistService;
  let sent: FormData;

  const file = new File([new Uint8Array([1, 2, 3])], 'site.jpg', { type: 'image/jpeg' });

  beforeEach(() => {
    service = new ChrChecklistService({ baseURL: 'http://localhost' } as never);
    vi.spyOn(service as never, 'doRequest').mockImplementation((_config, options) => {
      sent = (options as { body: FormData }).body;
      return Promise.resolve();
    });
  });

  it('sends the file and description', async () => {
    await service.addPhoto('1001', file, 'A description');

    expect(sent.get('file')).toBeInstanceOf(File);
    expect(sent.get('description')).toBe('A description');
  });

  it('sends the feature id when the photo documents one', async () => {
    await service.addPhoto('1001', file, 'A description', '2026-05-01', 'guid', '5001');

    expect(sent.get('featureId')).toBe('5001');
    expect(sent.get('date')).toBe('2026-05-01');
    expect(sent.get('deviceCheckoutGuid')).toBe('guid');
  });

  it('omits the optional fields when they are not supplied', async () => {
    // The server treats an absent field and a blank one differently — blank would fail Long binding.
    await service.addPhoto('1001', file, 'A description');

    expect(sent.has('featureId')).toBe(false);
    expect(sent.has('date')).toBe(false);
    expect(sent.has('deviceCheckoutGuid')).toBe(false);
  });

  it('does not mistake the checkout token for a feature id', async () => {
    // featureId sits after deviceCheckoutGuid precisely so a five-argument call can't shift it.
    await service.addPhoto('1001', file, 'A description', undefined, 'guid');

    expect(sent.get('deviceCheckoutGuid')).toBe('guid');
    expect(sent.has('featureId')).toBe(false);
  });
});
