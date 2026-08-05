import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import Photos from './Photos';

vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));

describe('Photos display', () => {
  it('renders server photos (raw base64) by prepending a data-URL prefix from mimeTypeCode', () => {
    render(
      <Photos
        readOnly={false}
        busy={false}
        onSave={vi.fn()}
        active={false}
        pictures={[{ id: '7', code: 'QUJD', mimeTypeCode: 'image/jpg', description: 'site' }]}
      />,
    );
    const img = screen.getByAltText('site') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('data:image/jpg;base64,QUJD');
  });

  it('passes through a photo that already carries a data URL', () => {
    render(
      <Photos
        readOnly={false}
        busy={false}
        onSave={vi.fn()}
        active={false}
        pictures={[{ id: '8', code: 'data:image/png;base64,XYZ', description: 'cam' }]}
      />,
    );
    const img = screen.getByAltText('cam') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('data:image/png;base64,XYZ');
  });

  it('marks Description required and blocks upload without one', async () => {
    const onSave = vi.fn();
    const { container } = render(
      <Photos readOnly={false} busy={false} onSave={onSave} active={false} pictures={[]} />,
    );
    // Required indicator: the app-wide red asterisk on the Description label.
    expect(container.querySelector('.required-asterisk')).toBeTruthy();

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(fileInput, new File(['x'], 'photo.jpg', { type: 'image/jpeg' }));

    // Empty description → inline error, no save round-trip.
    expect(await screen.findByText('Enter a description before uploading a photo.')).toBeTruthy();
    expect(onSave).not.toHaveBeenCalled();
  });
});
