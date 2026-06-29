import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import Photos from './Photos';

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
});
