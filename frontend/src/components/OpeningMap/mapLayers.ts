/**
 * DataBC public WMS overlay layers for the opening map, ported from nr-silva's OpeningsMap config.
 * Rendered as Leaflet WMSTileLayer overlays from https://openmaps.gov.bc.ca/geo/ows.
 */
export type MapWmsLayer = {
  name: string;
  format: string;
  layers: string;
  transparent: boolean;
  styles: string;
};

export const WMS_BASE_URL = 'https://openmaps.gov.bc.ca/geo/ows';

export const WMS_LAYERS: MapWmsLayer[] = [
  {
    name: 'RESULTS - Openings svw',
    format: 'image/png',
    layers: 'WHSE_FOREST_VEGETATION.RSLT_OPENING_SVW',
    transparent: true,
    styles: '2941_2942',
  },
  {
    name: 'RESULTS - Standards Units',
    format: 'image/png',
    layers: 'WHSE_FOREST_VEGETATION.RSLT_STANDARDS_UNIT_SVW',
    transparent: true,
    styles: '2945_2946',
  },
  {
    name: 'RESULTS - Activity Treatment Units',
    format: 'image/png',
    layers: 'WHSE_FOREST_VEGETATION.RSLT_ACTIVITY_TREATMENT_SVW',
    transparent: true,
    styles: '2937_2938',
  },
  {
    name: 'RESULTS - Forest Cover Inventory',
    format: 'image/png',
    layers: 'WHSE_FOREST_VEGETATION.RSLT_FOREST_COVER_INV_SVW',
    transparent: true,
    styles: '4284',
  },
  {
    name: 'RESULTS - Forest Cover Silviculture',
    format: 'image/png',
    layers: 'WHSE_FOREST_VEGETATION.RSLT_FOREST_COVER_SILV_SVW',
    transparent: true,
    styles: '4403',
  },
  {
    name: 'VRI - Forest Vegetation Composite Rank 1 (R1)',
    format: 'image/png',
    layers: 'WHSE_FOREST_VEGETATION.VEG_COMP_LYR_R1_POLY',
    transparent: true,
    styles: '5324',
  },
  {
    name: 'Fire Burn Severity - Same Year',
    format: 'image/png',
    layers: 'WHSE_FOREST_VEGETATION.VEG_BURN_SEVERITY_SAME_YR_SP',
    transparent: true,
    styles: '9792',
  },
  {
    name: 'BC Wildfire Fire Perimeters - Historical',
    format: 'image/png',
    layers: 'WHSE_LAND_AND_NATURAL_RESOURCE.PROT_HISTORICAL_FIRE_POLYS_SP',
    transparent: true,
    styles: '1758',
  },
  {
    name: 'BEC Map',
    format: 'image/png',
    layers: 'WHSE_FOREST_VEGETATION.BEC_BIOGEOCLIMATIC_POLY',
    transparent: true,
    styles: '1410',
  },
  {
    name: 'Natural Resource (NR) Districts',
    format: 'image/png',
    layers: 'WHSE_ADMIN_BOUNDARIES.ADM_NR_DISTRICTS_SPG',
    transparent: true,
    styles: '365',
  },
  {
    name: 'FADM - BC Timber Sales Area',
    format: 'image/png',
    layers: 'WHSE_ADMIN_BOUNDARIES.FADM_BCTS_AREA_SP',
    transparent: true,
    styles: '6',
  },
  {
    name: 'FADM - Tree Farm License (TFL)',
    format: 'image/png',
    layers: 'WHSE_ADMIN_BOUNDARIES.FADM_TFL_ALL_SP',
    transparent: true,
    styles: '6980',
  },
  {
    name: 'Forest Tenure Managed Licence',
    format: 'image/png',
    layers: 'WHSE_FOREST_TENURE.FTEN_MANAGED_LICENCE_POLY_SVW',
    transparent: true,
    styles: '2891',
  },
  {
    name: 'ParcelMap BC Parcel Fabric',
    format: 'image/png',
    layers: 'WHSE_CADASTRE.PMBC_PARCEL_FABRIC_POLY_SVW',
    transparent: true,
    styles: '5162',
  },
  {
    name: 'Forest Tenure Road Section Lines',
    format: 'image/png',
    layers: 'WHSE_FOREST_TENURE.FTEN_ROAD_SECTION_LINES_SVW',
    transparent: true,
    styles: '2864',
  },
  {
    name: 'Digital Road Atlas (DRA)',
    format: 'image/png',
    layers: 'WHSE_BASEMAPPING.DRA_DGTL_ROAD_ATLAS_MPAR_SP',
    transparent: true,
    styles: '3241_4489',
  },
  {
    name: 'Waterbodies - TRIM EBM',
    format: 'image/png',
    layers: 'WHSE_BASEMAPPING.TRIM_EBM_WATERBODIES',
    transparent: true,
    styles: '3370',
  },
  {
    name: 'Watercourses - TRIM EBM',
    format: 'image/png',
    layers: 'WHSE_BASEMAPPING.TRIM_EBM_WATERCOURSES',
    transparent: true,
    styles: '3372',
  },
  {
    name: 'Freshwater Atlas Stream Network',
    format: 'image/png',
    layers: 'WHSE_BASEMAPPING.FWA_STREAM_NETWORKS_SP',
    transparent: true,
    styles: '699',
  },
  {
    name: 'Freshwater Atlas Rivers',
    format: 'image/png',
    layers: 'WHSE_BASEMAPPING.FWA_RIVERS_POLY',
    transparent: true,
    styles: '704',
  },
  {
    name: 'Forest Tenure Cutblock Polygons (FTA 4.0)',
    format: 'image/png',
    layers: 'WHSE_FOREST_TENURE.FTEN_CUT_BLOCK_POLY_SVW',
    transparent: true,
    styles: '2841',
  },
];

/** Default map centre (BC), used when an opening has no polygon. Ported from nr-silva. */
export const DEFAULT_CENTER: [number, number] = [51.339506220208065, -121.40991210937501];
export const DEFAULT_ZOOM = 6;
