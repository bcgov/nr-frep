// Riparian + Water are out of scope for the migration.
export type ProtocolType = 'biodiversity';

export type ProtocolFieldKind = 'TEXT' | 'NUMBER' | 'DATE' | 'YES_NO' | 'MULTILINE';

export type ProtocolChecklistField = {
  label: string;
  value: string;
  kind: ProtocolFieldKind;
};

export type ProtocolChecklistSection = {
  id: string;
  title: string;
  fields: ProtocolChecklistField[];
};

export type ProtocolChecklist = {
  checklistId: string;
  protocolType: 'BIO' | 'RIP' | 'WAT' | 'CHR';
  protocolName: string;
  frepSelectedSiteId: string;
  openingNumber: string;
  effectiveYear: string;
  statusCode: string;
  statusLabel: string;
  evaluatorUserid: string;
  evaluationDate: string;
  sections: ProtocolChecklistSection[];
};

/** Typed, editable Biodiversity Opening (FREP screen 210). Mirrors backend BiodiversityOpening. */
export type BiodiversityOpening = {
  checklistId?: string;
  resourceValueId?: string;
  statusCode?: string;
  frepWtpOverride?: string;
  locationDescription?: string;
  patchReservesOnBlock?: string;
  patchReservesSampled?: string;
  innovativePracticeInd?: string;
  innovativePracticesComment?: string;
  invasivePlantIndicator?: string;
  invasivePlantComment?: string;
  frepSiteEvaluationCode?: string;
  evaluatorOpinionComment?: string;
  revisionCount?: string;
  // Read-only RESULTS reference fields (legacy FREP210 derives these from frep_selected_site); never
  // editable, not persisted on save.
  grossArea?: string;
  netArea?: string;
  harvestDate?: string;
};

/** A windthrow treatment on a stratum (mirrors FREP_WINDTHROW_TREAT_OBJECT). */
export type BioWindthrowTreatment = {
  windthrowTreatmentId?: string;
  code?: string;
  checkInd?: string; // "Y" | "N"
};

/** Summary row for the stratum list. */
export type BioStratumRow = {
  stratumId?: string;
  stratumNumber?: string;
  strataTypeCode?: string;
  summaryDate?: string;
  plotCount?: string;
  size?: string;
  revisionCount?: string;
};

/** Read-only computed values for a stratum (NAR + plots completed), shown in the FREP211 header. */
export type StratumComputed = {
  nar?: string;
  plotsCompleted?: string;
};

/** Typed, editable biodiversity stratum (FREP 211). All scalar fields round-trip on save. */
export type BioStratum = {
  stratumId?: string;
  checklistId?: string;
  strataTypeCode?: string;
  stratumNumber?: string;
  summaryDate?: string;
  assessorName?: string;
  plotCount?: string;
  size?: string;
  consistentMapInd?: string;
  estimatedSize?: string;
  patchLocationCode?: string;
  patchEstimatedOldestTreeAge?: string;
  patchGeneralComment?: string;
  patchWindthrowPct?: string;
  constraintIndicator?: string;
  wetlandPct?: string;
  harvestAreaCode?: string;
  riparianManagementZonePct?: string;
  riparianReserveZonePct?: string;
  rockOutcropPct?: string;
  nonCommercialBrushPct?: string;
  nonMerchTimberPct?: string;
  sensitiveSoilPct?: string;
  ungHoofAnimalWinteringPct?: string;
  wildlifeHabitatAreaPct?: string;
  oldGrowthManagementAreaPct?: string;
  visualsPct?: string;
  culturalHeritageFeaturePct?: string;
  recreationFeaturePct?: string;
  otherConstraint?: string;
  otherConstraintPct?: string;
  ecoIndicator?: string;
  bearDenCnt?: string;
  hibernaculumCnt?: string;
  vetTreeCnt?: string;
  mineralLickCnt?: string;
  largeStickNestCnt?: string;
  cavityNestCnt?: string;
  largeHallowTreeCnt?: string;
  largeWitchesBroomCnt?: string;
  karstFeatureInd?: string;
  largestTreeInd?: string;
  cwdHeavyConcentrationInd?: string;
  activeWildlifeTrailsInd?: string;
  activeWltCwdFeedingInd?: string;
  uncommonTreeSpeciesInd?: string;
  otherEcoAnchorCnt?: string;
  otherEcoAnchorDesc?: string;
  bgcZoneCode?: string;
  bgcSubzoneCode?: string;
  bgcVariant?: string;
  bgcPhase?: string;
  becSiteSeriesCd?: string;
  siteSeriesPhaseCd?: string;
  seral?: string;
  windthrowDistributionCode?: string;
  otherWindthrowTreatment?: string;
  constrainedTotal?: string;
  revisionCount?: string;
  windthrowTreatments?: BioWindthrowTreatment[];
};

/** One stand-table (tree) row of a plot (mirrors FREP_STAND_TABLE_OBJECT). */
export type BioStandRow = {
  standId?: string;
  plotId?: string;
  speciesCode?: string;
  speciesDesc?: string;
  treeNumber?: string;
  dbh?: string;
  height?: string;
  comments?: string;
  decayClassCode?: string;
  decayClassDesc?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};

/** One coarse-woody-debris row of a plot (mirrors FREP_CWD_TABLE_OBJECT). */
export type BioCwdRow = {
  cwdId?: string;
  plotId?: string;
  speciesCode?: string;
  speciesDesc?: string;
  logNumber?: string;
  logDiameter?: string;
  logLength?: string;
  decayClassCode?: string;
  decayClassDesc?: string;
  comments?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};

/** Summary row for the plot list. */
export type BioPlotRow = {
  plotId?: string;
  plotNumber?: string;
  assessorName?: string;
  revisionCount?: string;
};

/** Typed, editable biodiversity plot (FREP 212). All scalar fields round-trip on save. */
export type BioPlot = {
  plotId?: string;
  stratumId?: string;
  plotNumber?: string;
  assessorName?: string;
  utmSignal?: string;
  utmZone?: string;
  utmEasting?: string;
  utmNorthing?: string;
  treeIndicator?: string;
  basalAreaFactor?: string;
  fixedAreaRadius?: string;
  fullCountArea?: string;
  cwdTransectIndicator?: string;
  firstLegTransect?: string;
  secondLegTransect?: string;
  plotComment?: string;
  revisionCount?: string;
  standTable?: BioStandRow[];
  cwdTable?: BioCwdRow[];
};

/** An evaluation-team member (FREP301 Administration); the team lead has teamLeadInd = 'Y'. */
export type EvaluatorRow = {
  evaluatorUserid?: string;
  frepResourceValueId?: string;
  teamLeadInd?: string;
  evaluatorDescription?: string;
  revisionCount?: string;
};

/** The single free-text note for a checklist (legacy Notes tab). */
export type RiparianNotes = {
  checklistId?: string;
  noteDescription?: string;
  revisionCount?: string;
};

/** Metadata for one checklist attachment (legacy Attachments tab). */
export type AttachmentRow = {
  checklistAttachmentId?: string;
  fileName?: string;
  description?: string;
  mimeTypeCode?: string;
  fileSize?: string;
};

/** A downloaded attachment's bytes (base64) + metadata. */
export type AttachmentContent = {
  fileName?: string;
  mimeType?: string;
  data?: string;
};

/** Upload payload — {@code data} is base64-encoded file bytes. */
export type AttachmentUploadRequest = {
  fileName?: string;
  description?: string;
  contentType?: string;
  data?: string;
};

/** Checklist Administration tab (FREP301) — evaluation date, site access/cost data, team. */
export type AdministrationData = {
  checklistId?: string;
  selectedSiteId?: string;
  resourceValueId?: string;
  resourceValueType?: string;
  statusCode?: string;
  evaluationDate?: string;
  siteAccessCode?: string;
  blockAccessTime?: string;
  hoursOnBlock?: string;
  peopleOnBlock?: string;
  additionalComments?: string;
  teamLeadNameId?: string;
  teamLeadRevisionCount?: string;
  revisionCount?: string;
  revisionCountAccess?: string;
  teamMembers?: EvaluatorRow[];
};

export const PROTOCOL_TYPE_TO_BACKEND: Record<ProtocolType, 'bio'> = {
  biodiversity: 'bio',
};

export const PROTOCOL_TYPE_LABEL: Record<ProtocolType, string> = {
  biodiversity: 'Biodiversity',
};
