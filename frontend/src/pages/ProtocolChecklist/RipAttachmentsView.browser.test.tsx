import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RipAttachmentsView from './RipAttachmentsView';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getAttachments: vi.fn(),
      getAttachmentContent: vi.fn(),
      uploadAttachment: vi.fn(),
      deleteAttachment: vi.fn(),
    },
  },
}));

const { display } = vi.hoisted(() => ({ display: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));
vi.mock('@/context/confirm/useConfirm', () => ({
  useConfirm: () => () => Promise.resolve(true),
}));

const api = API.protocolChecklist as unknown as {
  getAttachments: ReturnType<typeof vi.fn>;
  getAttachmentContent: ReturnType<typeof vi.fn>;
  uploadAttachment: ReturnType<typeof vi.fn>;
  deleteAttachment: ReturnType<typeof vi.fn>;
};

const renderView = () =>
  render(
    <RipAttachmentsView protocol="bio" checklistId="1001" canEdit submitted={false} />,
  );

/**
 * Type a description, then pick `files` through the (hidden) file input.
 *
 * Waits for the initial list load first — the view renders a skeleton until then, so the upload card
 * (and its file input) doesn't exist yet.
 */
const uploadFiles = async (container: HTMLElement, description: string, files: File[]) => {
  const field = await screen.findByLabelText(/Description/);
  if (description) {
    await userEvent.type(field, description);
  }
  const input = container.querySelector('input[type="file"]') as HTMLInputElement;
  await userEvent.upload(input, files);
};

const pdf = (name: string, bytes = 4) =>
  new File([new Uint8Array(bytes)], name, { type: 'application/pdf' });

describe('RipAttachmentsView — batch upload', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getAttachments.mockResolvedValue({ attachments: [], totalCount: 0 });
    api.uploadAttachment.mockResolvedValue(undefined);
  });

  it('accepts multiple files and uploads them one request at a time', async () => {
    // Sequential on purpose: each upload holds its bytes in server heap for the scan and the store,
    // so several large files in flight at once is the pressure the 15 MB cap was sized against.
    const inFlight: string[] = [];
    let concurrent = 0;
    let maxConcurrent = 0;
    api.uploadAttachment.mockImplementation((_p, _c, file: File) => {
      concurrent += 1;
      maxConcurrent = Math.max(maxConcurrent, concurrent);
      inFlight.push(file.name);
      return Promise.resolve().then(() => {
        concurrent -= 1;
      });
    });

    const { container } = renderView();
    await uploadFiles(container, 'Batch of reports', [pdf('a.pdf'), pdf('b.pdf'), pdf('c.pdf')]);

    await waitFor(() => expect(api.uploadAttachment).toHaveBeenCalledTimes(3));
    expect(inFlight).toEqual(['a.pdf', 'b.pdf', 'c.pdf']);
    expect(maxConcurrent).toBe(1);
  });

  it('applies the one entered description to every file in the batch', async () => {
    const { container } = renderView();
    await uploadFiles(container, 'Shared description', [pdf('a.pdf'), pdf('b.pdf')]);

    await waitFor(() => expect(api.uploadAttachment).toHaveBeenCalledTimes(2));
    expect(api.uploadAttachment.mock.calls.map((c) => c[3])).toEqual([
      'Shared description',
      'Shared description',
    ]);
  });

  it('re-reads the list once for the whole batch, not once per file', async () => {
    const { container } = renderView();
    await waitFor(() => expect(api.getAttachments).toHaveBeenCalledTimes(1)); // initial load
    api.getAttachments.mockClear();

    await uploadFiles(container, 'Batch', [pdf('a.pdf'), pdf('b.pdf'), pdf('c.pdf')]);

    await waitFor(() => expect(api.uploadAttachment).toHaveBeenCalledTimes(3));
    expect(api.getAttachments).toHaveBeenCalledTimes(1);
  });

  it('skips an empty file and uploads the rest of the batch', async () => {
    const { container } = renderView();
    await uploadFiles(container, 'Mixed batch', [
      pdf('good.pdf'),
      new File([''], 'empty.pdf', { type: 'application/pdf' }), // zero bytes
    ]);

    await waitFor(() => expect(api.uploadAttachment).toHaveBeenCalledTimes(1));
    expect(api.uploadAttachment.mock.calls[0][2].name).toBe('good.pdf');
    expect(display.mock.calls.find(([a]) => a.kind === 'error')?.[0].subtitle).toContain(
      'empty.pdf',
    );
  });

  // NOT COVERED: rejection via drag-and-drop. The file picker filters by the input's `accept` list,
  // so an unsupported extension can only arrive by drop — and simulating a drop with a populated
  // DataTransfer did not reach React's onDrop in this harness. The shared `rejectionReason` logic is
  // exercised by the empty-file case above; only the drop entry point is unverified. Manual check.

  it('keeps the files that landed when one of the batch fails', async () => {
    // A failure part-way through must not discard the successes — the user is told which failed.
    api.uploadAttachment
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(undefined);

    const { container } = renderView();
    await uploadFiles(container, 'Batch', [pdf('a.pdf'), pdf('b.pdf'), pdf('c.pdf')]);

    await waitFor(() => expect(api.uploadAttachment).toHaveBeenCalledTimes(3));
    const warning = display.mock.calls.find(([arg]) => arg.kind === 'warning')?.[0];
    expect(warning.title).toContain('Uploaded 2');
    expect(warning.subtitle).toContain('b.pdf');
  });

  it('blocks the whole batch when no description was entered', async () => {
    const { container } = renderView();
    await uploadFiles(container, '', [pdf('a.pdf'), pdf('b.pdf')]);

    expect(api.uploadAttachment).not.toHaveBeenCalled();
  });
});
