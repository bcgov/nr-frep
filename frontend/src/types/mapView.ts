/** Response from `GET /v1/openings/{openingId}/map-view`. */
export type MapViewResponse = {
  /** External GIS map-viewer URL to open. Empty when no viewer is configured for the environment. */
  url: string;
};
