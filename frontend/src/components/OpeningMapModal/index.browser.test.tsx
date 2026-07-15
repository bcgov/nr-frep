import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import OpeningMapModal from './index';

import API from '@/services/APIs';

// Stub the Leaflet map so the test stays lightweight and we can assert purely on mount/gating.
vi.mock('@/components/OpeningMap', () => ({
  default: () => <div data-testid="opening-map" />,
}));

vi.mock('@/services/APIs', () => ({
  default: { acceptedSites: { getOpeningPolygon: vi.fn() } },
}));

const api = API.acceptedSites as unknown as { getOpeningPolygon: ReturnType<typeof vi.fn> };

describe('OpeningMapModal', () => {
  afterEach(() => vi.clearAllMocks());

  it('does not mount the map while the modal is closed', () => {
    // Guards the hidden-mount bug: a Leaflet map initialized in a hidden (0-size) modal renders broken.
    render(<OpeningMapModal openingId={null} onClose={vi.fn()} />);
    expect(screen.queryByTestId('opening-map')).toBeNull();
    expect(api.getOpeningPolygon).not.toHaveBeenCalled();
  });

  it('mounts the map when the modal is opened', async () => {
    api.getOpeningPolygon.mockResolvedValue({
      type: 'FeatureCollection',
      features: [
        { type: 'Feature', geometry: { type: 'Point', coordinates: [0, 0] }, properties: {} },
      ],
    });

    render(<OpeningMapModal openingId="123" onClose={vi.fn()} />);

    expect(await screen.findByTestId('opening-map')).toBeTruthy();
    expect(api.getOpeningPolygon).toHaveBeenCalledWith('123');
  });
});
