import 'leaflet/dist/leaflet.css';

import L, { type LatLngExpression } from 'leaflet';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import { useEffect, type FC } from 'react';
import {
  GeoJSON,
  LayersControl,
  MapContainer,
  TileLayer,
  useMap,
  WMSTileLayer,
  ZoomControl,
} from 'react-leaflet';

import { DEFAULT_CENTER, DEFAULT_ZOOM, WMS_BASE_URL, WMS_LAYERS } from './mapLayers';

import type { FeatureCollection } from 'geojson';

import './styles.scss';

// Vite serves the bundled marker images as URLs; Leaflet's default icon paths point at the
// (missing) /dist images, so wire the imported asset URLs back in. Without this, markers 404.
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

const OPENING_STYLE = { color: '#005CB8', weight: 2, fillColor: '#4A90E2', fillOpacity: 0.4 };

/** Recenters/zooms the map to the opening polygon's bounds (or the BC default when empty). */
const FitBounds: FC<{ polygon: FeatureCollection | null }> = ({ polygon }) => {
  const map = useMap();
  useEffect(() => {
    if (polygon && polygon.features.length > 0) {
      const bounds = L.geoJSON(polygon).getBounds();
      if (bounds.isValid()) {
        map.fitBounds(bounds, { padding: [24, 24] });
        return;
      }
    }
    map.setView(DEFAULT_CENTER as LatLngExpression, DEFAULT_ZOOM);
  }, [polygon, map]);
  return null;
};

/** Invalidates map size after mount/resize (needed when the map lives inside a modal). */
const Resizer: FC = () => {
  const map = useMap();
  useEffect(() => {
    const t = setTimeout(() => map.invalidateSize(), 120);
    return () => clearTimeout(t);
  }, [map]);
  return null;
};

type Props = {
  /** Opening polygon GeoJSON (from the WFS proxy). Null while loading; empty collection if unmapped. */
  polygon: FeatureCollection | null;
  height?: number;
};

/**
 * In-app Leaflet map for a single opening — base layers + DataBC WMS overlays + the opening's polygon,
 * fit to the polygon's bounds. Mirrors nr-silva's OpeningsMap (adapted to a single FeatureCollection).
 */
const OpeningMap: FC<Props> = ({ polygon, height = 460 }) => (
  <MapContainer
    center={DEFAULT_CENTER as LatLngExpression}
    zoom={DEFAULT_ZOOM}
    zoomControl={false}
    style={{ height, width: '100%' }}
    className="opening-map"
  >
    <ZoomControl position="bottomright" />
    {polygon && polygon.features.length > 0 && (
      <GeoJSON
        key={JSON.stringify(polygon.features.map((f) => f.id))}
        data={polygon}
        style={() => OPENING_STYLE}
      />
    )}
    <FitBounds polygon={polygon} />
    <Resizer />
    <LayersControl position="topright">
      <LayersControl.BaseLayer checked name="ESRI Topography">
        <TileLayer
          url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}"
          attribution="Tiles &copy; Esri"
        />
      </LayersControl.BaseLayer>
      <LayersControl.BaseLayer name="ESRI Satellite">
        <TileLayer
          url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
          attribution="Tiles &copy; Esri"
        />
      </LayersControl.BaseLayer>
      <LayersControl.BaseLayer name="OpenStreetMap">
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />
      </LayersControl.BaseLayer>
      {WMS_LAYERS.map((layer) => (
        <LayersControl.Overlay key={layer.name} name={layer.name}>
          <WMSTileLayer
            url={WMS_BASE_URL}
            params={{
              format: layer.format,
              layers: layer.layers,
              transparent: layer.transparent,
              styles: layer.styles,
            }}
          />
        </LayersControl.Overlay>
      ))}
    </LayersControl>
  </MapContainer>
);

export default OpeningMap;
