/**
 * TypeScript mirror of the backend CHR DTOs (ca.bc.gov.nrs.frep.dto.frep.*).
 *
 * IMPORTANT: the editor posts the whole {@link CheckList} back to the API verbatim, so every
 * field name here must match the backend JSON exactly (field names taken from the Java DTOs).
 * All boolean-style indicators are JSON strings `"true"` / `"false"`, not booleans.
 */

export type Indicator = string; // "true" | "false"

export type Contact = {
  id?: string;
  firstName?: string;
  lastName?: string;
  roleCode?: string;
  organization?: string;
  contactedInd?: Indicator;
  contactedDate?: string;
  attendingOnSiteInd?: Indicator;
};

export type Picture = {
  id?: string;
  date?: string;
  description?: string;
  code?: string; // base64 (no data-URL prefix on save)
  mimeTypeCode?: string;
  /** The feature this photo documents, if any. Set once at upload; the server resolves the label. */
  featureId?: string;
  featureLabel?: string;
  fileName?: string;
  checklistId?: string;
  status?: string;
};

export type OtherPlannedManagementStrategy = {
  otherStrategy?: string;
  fnInd?: Indicator;
  aiaInd?: Indicator;
  spInd?: Indicator;
};

export type ValidationError = {
  type?: string;
  message?: string;
  referenceId?: string;
  field?: string;
  /** Which entity the error belongs to. For a checklist it is the checklist id; for a CHR feature it
   * is `"<checklistId>-<featureLabel>"`, so a feature-level error can name the feature it came from. */
  entityLabel?: string;
};

/**
 * A single CHR feature. Field names mirror Feature.java; the form mutates these in place and the
 * whole object is posted back. Only the fields the editor reads/writes are typed; unknown
 * passthrough fields are preserved via the index signature.
 */
export type Feature = {
  id?: string;
  featureLabel?: string;
  compositeFeatureInd?: Indicator;
  compositeFeature?: string;
  featureDescriptionCode?: string;
  featureInfoSourceCode?: string;
  featureComment?: string;
  associatedFeatures?: string[];

  // Description / section 4
  chrRegisteredSite?: Indicator;
  borden?: string;
  featureDescription?: string;
  widthofFeature?: string;
  lengthofFeature?: string;
  areaofFeature?: string;
  managementStrategyFN?: Indicator;
  managementStrategySP?: Indicator;
  permit?: string;
  sitePermitIssued?: Indicator;

  // Feature type checkboxes (keys match the backend Feature DTO @JsonProperty names)
  culturaltraildesignated?: Indicator;
  culturaltrailundesignated?: Indicator;
  burialSite?: Indicator;
  nest?: Indicator;
  ceremonialSite?: Indicator;
  cremationSite?: Indicator;
  caveorotherKarst?: Indicator;
  den?: Indicator;
  traditionalUseSite?: Indicator;
  cedarBarkStriparea?: Indicator;
  rockOutcrop?: Indicator;
  spiritualSite?: Indicator;
  ofCMTs?: Indicator;
  ofCMTsNumber?: string;
  ofMonumentalCedars?: Indicator;
  standofMonumentalCedar?: string;
  culturalDepression?: Indicator;
  lithics?: Indicator;
  other?: Indicator;
  otherdescription?: string;

  // Location
  inharvestedarea?: Indicator;
  adjacenttoblock?: Indicator;
  adjacenttowater?: Indicator;
  locationOther?: Indicator;
  locationOtherDescription?: string;
  entirecutblock?: Indicator;
  inReserve?: Indicator;
  locationReservetype?: string;

  // Age
  pre1846?: Indicator;
  post1846?: Indicator;
  ageUnknown?: Indicator;
  historicalUse?: Indicator;

  // Planning — FN / AIA / SP triples
  modifyBlockBoundaryFN?: Indicator;
  modifyBlockBoundaryAIA?: Indicator;
  modifyBlockBoundarySP?: Indicator;
  retainBufferFN?: Indicator;
  retainBufferAIA?: Indicator;
  retainBufferSP?: Indicator;
  bufferLengthFN?: string;
  bufferLengthAIA?: string;
  bufferLengthSP?: string;
  retaininHarvestAreaFN?: Indicator;
  retaininHarvestAreaAIA?: Indicator;
  retaininHarvestAreaSP?: Indicator;
  crownorstandmodificationFN?: Indicator;
  crownorstandmodificationAIA?: Indicator;
  crownorstandmodificationSP?: Indicator;
  conserveinRotationalReserveFN?: Indicator;
  conserveinRotationalReserveAIA?: Indicator;
  conserveinRotationalReserveSP?: Indicator;
  conserveRotationalReserveTypeFN?: string;
  conserveRotationalReserveTypeAIA?: string;
  conserveRotationalReserveTypeSP?: string;
  permanentReserveFN?: Indicator;
  permanentReserveAIA?: Indicator;
  permanentReserveSP?: Indicator;
  temporaryRetentionTypeFN?: string;
  temporaryRetentionTypeAIA?: string;
  temporaryRetentionTypeSP?: string;
  datetheFeatureFN?: Indicator;
  datetheFeatureAIA?: Indicator;
  datetheFeatureSP?: Indicator;
  stubCMTsabovescarFN?: Indicator;
  stubCMTsabovescarAIA?: Indicator;
  stubCMTsabovescarSP?: Indicator;
  stubnonCMTsFN?: Indicator;
  stubnonCMTsAIA?: Indicator;
  stubnonCMTsSP?: Indicator;
  leaveStandingFN?: Indicator;
  leaveStandingAIA?: Indicator;
  leaveStandingSP?: Indicator;
  avoidSilvAvoidPlantingFN?: Indicator;
  avoidSilvAvoidPlantingAIA?: Indicator;
  avoidSilvAvoidPlantingSP?: Indicator;
  avoidSilvAvoidSitePrepFN?: Indicator;
  avoidSilvAvoidSitePrepAIA?: Indicator;
  avoidSilvAvoidSitePrepSP?: Indicator;
  machineFreeZoneFN?: Indicator;
  machineFreeZoneAIA?: Indicator;
  machineFreeZoneSP?: Indicator;
  harvestUnderSapFN?: Indicator;
  harvestUnderSapAIA?: Indicator;
  harvestUnderSapSP?: Indicator;
  winterHarvestFrozenGroundFN?: Indicator;
  winterHarvestFrozenGroundAIA?: Indicator;
  winterHarvestFrozenGroundSP?: Indicator;
  otherPlannedManagementStrategy?: OtherPlannedManagementStrategy[];

  // Effectiveness / used strategies
  forCompositeFeaturesInd?: Indicator;
  unabletoLocate?: Indicator;
  noManagement?: Indicator;
  partiallytemporaryreserve?: Indicator;
  partiallytemporaryreservetype?: string;
  fullyconservedintemporaryreserve?: Indicator;
  fullytemporaryreserve?: string;
  partiallyconservedinpermanentreserve?: Indicator;
  partiallyconservedinpermanentreserveType?: string;
  fullyconservedinpermanentreserve?: Indicator;
  fullyconservedinpermanentreserveType?: string;
  modifiedblockboundary?: Indicator;
  retainabuffer?: Indicator;
  bufferWidthMeter?: string;
  compledCrownorstandmodification?: Indicator;
  datedthefeature?: Indicator;
  retainedinharvestareanobuffer?: Indicator;
  leftStanding?: Indicator;
  stubbed?: Indicator;
  stubbedNon?: Indicator;
  avoidSilvAvoidPlanting?: Indicator;
  avoidSilvAvoidSitePrep?: Indicator;
  machineFreeZone?: Indicator;
  harvestUnderSap?: Indicator;
  winterHarvestFrozenGround?: Indicator;
  otherActivities?: string;

  // Damage Q1/Q2/Q3
  q1Isthereevidenceofdamagetothesiteorfeature?: Indicator;
  harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  fireQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  roadQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  otherQ2Wheredamagehasoccurredwhatisthemostlikelycause?: Indicator;
  ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause?: string;
  q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse?: string;
  descriptionofdamage?: string;

  // Windthrow
  windthrowManagement?: Indicator;
  windthrow?: Indicator;
  estwindthrow?: string;
  windthrowTechniqueNone?: Indicator;
  windthrowTechniqueRetentionBuffer?: Indicator;
  windthrowTechniquePruning?: Indicator;
  windthrowTechniqueFeathering?: Indicator;
  windthrowTechniqueTopping?: Indicator;
  otherTechnique?: Indicator;
  ifotherpleasedescribe?: string;

  // Trail
  trailfeatures?: Indicator;
  canthetrailstillbelocated?: Indicator;
  hasthetrailbeenmadelesspassble?: Indicator;
  isthereevidenceofdamage?: Indicator;
  trailLength?: string;

  // Summary Q4/Q5/Q6 (CHR-cased fields are the ones the backend round-trips)
  q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature?: Indicator;
  q4Description?: string;
  q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective?: Indicator;
  q5Description?: string;
  q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature?: Indicator;
  q6Description?: string;

  featureRating?: string;
  featureRatingRationale?: string;

  pictures?: Picture[];

  // Preserve any backend fields not modelled above on round-trip.
  [key: string]: unknown;
};

export type CheckList = {
  checklistID?: string;
  status?: string;
  revisionCount?: string;
  deviceCheckoutGuid?: string;
  /** IDIR of the user who last updated the record (server-provided). */
  updateUserid?: string;
  /** When the record was last updated, "yyyy-MM-dd HH:mm:ss" (server-provided). */
  updateTimestamp?: string;

  // Read-only context (display)
  effectiveYear?: string;
  masterList?: string;
  orgUnitCode?: string;
  orgUnitName?: string;
  district?: string;
  openingID?: string;
  openingNumber?: string;
  licensee?: string;
  cuttingPermit?: string;
  block?: string;
  client?: string;
  clientName?: string;
  yearOfHarvest?: string;

  // Editable opening info
  evaluationDate?: string;
  assessedBy?: string;
  // FAM-resolved "Name (USERID)" display for assessedBy (read-only; assessedBy keeps the raw userid).
  assessedByName?: string;
  firstNationName?: string;
  generalLocation?: string;
  targeted?: Indicator;

  // Block summary Q8/Q9/Q10 + comments
  q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock?: Indicator;
  q8Comments?: string;
  q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues?: Indicator;
  q9Comments?: string;
  q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock?: Indicator;
  q10Comments?: string;

  rating?: string;
  ratingRationale?: string;
  mrvaRatingCode?: string;
  commentaires?: string;

  contacts?: Contact[];
  features?: Feature[];
  pictures?: Picture[];

  // Preserve unknown backend fields on round-trip.
  [key: string]: unknown;
};

export const CHR_STATUS = {
  ACTIVE: 'ACT',
  SUBMITTED: 'SUB',
  READ_ONLY_OFFLINE: 'RDO',
} as const;

/** One page of CHR photo metadata; bytes are fetched per photo from the content endpoint. */
export type PhotoPageResponse = {
  photos: Picture[];
  totalCount: number;
};
