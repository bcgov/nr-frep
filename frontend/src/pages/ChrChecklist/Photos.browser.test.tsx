import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import Photos from './Photos';

/**
 * Props every render needs. Photos is paged and fetches bytes per photo now, so the pager state and
 * the content fetcher are required — the fixtures below carry `code`, so `fetchContent` is only
 * called for photos that don't.
 */
const baseProps = {
  readOnly: false,
  busy: false,
  active: false,
  onAdd: vi.fn(),
  onDelete: vi.fn(),
  fetchContent: vi.fn(),
  page: 0,
  pageSize: 10,
  totalCount: 0,
  onPageChange: vi.fn(),
};

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

describe('Photos display', () => {
  it('renders server photos (raw base64) by prepending a data-URL prefix from mimeTypeCode', () => {
    render(
      <Photos
        {...baseProps}
        totalCount={1}
        pictures={[{ id: '7', code: 'QUJD', mimeTypeCode: 'image/jpg', description: 'site' }]}
      />,
    );
    const img = screen.getByAltText('site') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('data:image/jpg;base64,QUJD');
  });

  it('passes through a photo that already carries a data URL', () => {
    render(
      <Photos
        {...baseProps}
        totalCount={1}
        pictures={[{ id: '8', code: 'data:image/png;base64,XYZ', description: 'cam' }]}
      />,
    );
    const img = screen.getByAltText('cam') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('data:image/png;base64,XYZ');
  });

  // ── The per-photo content fetch ──────────────────────────────────────
  //
  // These are the largest responses in the app, so a duplicate round is real bytes. The guard has to
  // be a ref: `fetched` state only lands after the response, so anything that re-renders during the
  // in-flight window would see the photo as un-fetched and request it again.

  const serverPhoto = (id: string) => ({ id, description: `photo ${id}`, mimeTypeCode: 'image/png' });

  it('fetches each photo exactly once', async () => {
    const fetchContent = vi.fn().mockResolvedValue(new Blob(['x'], { type: 'image/png' }));

    render(
      <Photos
        {...baseProps}
        fetchContent={fetchContent}
        totalCount={2}
        pictures={[serverPhoto('91'), serverPhoto('92')]}
      />,
    );

    await waitFor(() => expect(fetchContent).toHaveBeenCalledTimes(2));
    expect(fetchContent.mock.calls.map((c) => c[0]).sort()).toEqual(['91', '92']);
  });

  it('does not re-request in-flight photos when the parent re-renders', async () => {
    // The real trigger: `fetchContent` was an inline arrow in the parent's JSX, so every render gave
    // the effect a new dependency identity and started another round before the first had resolved.
    let release: (blob: Blob) => void = () => {};
    const spy = vi
      .fn()
      .mockReturnValue(new Promise<Blob>((resolve) => { release = resolve; }));
    // Wrapped in a fresh arrow each render, exactly as the parent's JSX used to do: the identity
    // changes every time, but every call funnels to the one spy so a refetch is visible.
    const { rerender } = render(
      <Photos {...baseProps} fetchContent={(pid) => spy(pid)} totalCount={1} pictures={[serverPhoto('91')]} />,
    );
    await waitFor(() => expect(spy).toHaveBeenCalledTimes(1));

    rerender(
      <Photos {...baseProps} fetchContent={(pid) => spy(pid)} totalCount={1} pictures={[serverPhoto('91')]} />,
    );
    release(new Blob(['x'], { type: 'image/png' }));

    // The in-flight result must still land — the request is already paid for.
    await waitFor(() => expect(screen.getByAltText('photo 91')).toBeTruthy());
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('retries a photo whose fetch failed', async () => {
    // The in-flight guard must not become a permanent block: a failed photo has no bytes to show,
    // so a later render has to be able to ask again.
    const fetchContent = vi
      .fn()
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValue(new Blob(['x'], { type: 'image/png' }));

    const { rerender } = render(
      <Photos {...baseProps} fetchContent={fetchContent} totalCount={1} pictures={[serverPhoto('91')]} />,
    );
    await waitFor(() => expect(fetchContent).toHaveBeenCalledTimes(1));

    rerender(
      <Photos {...baseProps} fetchContent={fetchContent} totalCount={1} pictures={[serverPhoto('91')]} />,
    );

    await waitFor(() => expect(fetchContent).toHaveBeenCalledTimes(2));
  });

  it('marks Description required and blocks upload without one', async () => {
    const onAdd = vi.fn();
    const { container } = render(<Photos {...baseProps} onAdd={onAdd} pictures={[]} />);
    // Required indicator: the app-wide red asterisk on the Description label.
    expect(container.querySelector('.required-asterisk')).toBeTruthy();

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(fileInput, new File(['x'], 'photo.jpg', { type: 'image/jpeg' }));

    // Empty description → inline error, no save round-trip.
    expect(await screen.findByText('Enter a description before uploading a file.')).toBeTruthy();
    expect(onAdd).not.toHaveBeenCalled();
  });
});
