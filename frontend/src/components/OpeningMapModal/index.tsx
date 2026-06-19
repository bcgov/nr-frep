import { Modal, InlineLoading, InlineNotification } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import OpeningMap from '@/components/OpeningMap';

import type { FeatureCollection } from 'geojson';

import API from '@/services/APIs';

type Props = {
  /** Opening ID to map; null closes the modal. */
  openingId: string | null;
  onClose: () => void;
};

/**
 * Modal hosting the in-app Leaflet map for a single opening. Fetches the opening's polygon GeoJSON
 * (DataBC WFS via the backend) and renders it over the BC base/WMS layers. Replaces the legacy
 * external map-viewer deep-link on the Random List / Accepted Sites screens.
 */
const OpeningMapModal: FC<Props> = ({ openingId, onClose }) => {
  const [polygon, setPolygon] = useState<FeatureCollection | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!openingId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    setPolygon(null);
    API.acceptedSites
      .getOpeningPolygon(openingId)
      .then((fc) => {
        if (!cancelled) setPolygon(fc);
      })
      .catch((err: unknown) => {
        if (!cancelled)
          setError(err instanceof Error ? err.message : 'Failed to load the opening map.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [openingId]);

  const noPolygon = !loading && !error && polygon !== null && polygon.features.length === 0;

  return (
    <Modal
      open={openingId !== null}
      passiveModal
      modalHeading={`Opening ${openingId ?? ''} — map`}
      size="lg"
      onRequestClose={onClose}
    >
      {loading && <InlineLoading description="Loading opening map…" />}
      {error && (
        <InlineNotification
          kind="error"
          title="Map unavailable"
          subtitle={error}
          hideCloseButton
          lowContrast
        />
      )}
      {noPolygon && (
        <InlineNotification
          kind="info"
          title="No mapped polygon"
          subtitle="This opening has no spatial polygon in the RESULTS dataset."
          hideCloseButton
          lowContrast
        />
      )}
      {!error && (polygon === null || polygon.features.length > 0) && (
        <OpeningMap polygon={polygon} />
      )}
    </Modal>
  );
};

export default OpeningMapModal;
