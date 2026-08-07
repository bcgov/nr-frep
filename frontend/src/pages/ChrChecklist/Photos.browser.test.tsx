import { render, screen } from '@testing-library/react';
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

  it('marks Description required and blocks upload without one', async () => {
    const onAdd = vi.fn();
    const { container } = render(<Photos {...baseProps} onAdd={onAdd} pictures={[]} />);
    // Required indicator: the app-wide red asterisk on the Description label.
    expect(container.querySelector('.required-asterisk')).toBeTruthy();

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(fileInput, new File(['x'], 'photo.jpg', { type: 'image/jpeg' }));

    // Empty description → inline error, no save round-trip.
    expect(await screen.findByText('Enter a description before uploading a photo.')).toBeTruthy();
    expect(onAdd).not.toHaveBeenCalled();
  });
});
