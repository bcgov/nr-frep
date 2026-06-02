export type ProtocolType = 'biodiversity' | 'riparian' | 'water';

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

/** One riparian stream-edge measurement (mirrors FREP_STRM_EDGE_MEASMNT_OBJECT). */
export type RipStreamEdgeRow = {
  measureType?: string;
  measurement?: string;
  description?: string;
  revisionCount?: string;
};

/** Typed, editable riparian stream opening (FREP 230). All scalar fields round-trip on save. */
export type RiparianStreamOpening = {
  checklistId?: string;
  sampleNumber?: string;
  rangeUsePlan?: string;
  pastureId?: string;
  streamName?: string;
  streamLocationInd?: string;
  plnRiparianStrmRmaCls?: string;
  actRiparianStrmRmaCls?: string;
  channelWidth?: string;
  channelGradientPct?: string;
  channelDepth?: string;
  reachLocationTo?: string;
  reachLocationFrom?: string;
  reachLocationUpsDsInd?: string;
  reachLocationFromDesc?: string;
  utmSignal?: string;
  utmAtReference?: string;
  utmZone?: string;
  utmEasting?: string;
  utmNorthing?: string;
  riparianChanMorphology?: string;
  rttnRmaDomsOnPlans?: string;
  rttnRmaDomsOnPlansInd?: string;
  rttnRmaDomsInField?: string;
  rttnRmaUndrstryOnPlans?: string;
  rttnRmaUndrstryOnPlnI?: string;
  rttnRmaUndrstryInField?: string;
  rttnRrzDomsOnPlans?: string;
  rttnRrzDomsOnPlansInd?: string;
  rttnRrzDomsInFieldPct?: string;
  rttnRrzDomsInField?: string;
  rttnRrzUndrstryOnPlans?: string;
  rttnRrzUndrstryOnPlnI?: string;
  rttnRrzUndrstryFldPct?: string;
  rttnRrzUndrstryInField?: string;
  rttnRmzDomsOnPlans?: string;
  rttnRmzDomsOnPlansInd?: string;
  rttnRmzDomsInField?: string;
  rttnRmzUndrstryOnPlans?: string;
  rttnRmzUndrstryOnPlnI?: string;
  rttnRmzUndrstryInField?: string;
  plnRiparianStrNaInd?: string;
  invasivePlantIndicator?: string;
  invasivePlantComment?: string;
  revisionCount?: string;
  streamEdge?: RipStreamEdgeRow[];
};

/** Typed, editable riparian final comments (FREP 235). */
export type RiparianFinalComments = {
  checklistId?: string;
  conclusionComment?: string;
  specificImpactComment?: string;
  assessmentProblemsComment?: string;
  mapLegibilityComment?: string;
  leaveStripAssessmentComment?: string;
  checklistRecommComment?: string;
  revisionCount?: string;
};

/** Riparian field-data (231) indicator rows + aggregate. */
export type RipPointIndRow = {
  pointIndicatorId?: string;
  questionNo?: string;
  pointIndType?: string;
  transectNo?: string;
  measure1?: string;
  measure2?: string;
  measure3?: string;
  measure4?: string;
  measure5?: string;
  measure6?: string;
  threshold?: string;
  mean?: string;
  revisionCount?: string;
};
export type RipContinuousIndRow = {
  continuousIndId?: string;
  questionNo?: string;
  continuousIndType?: string;
  question?: string;
  total?: string;
  comments?: string;
  threshold?: string;
  revisionCount?: string;
};
export type RiparianFieldData = {
  checklistId?: string;
  fieldDataStreamReachDry?: string;
  points?: RipPointIndRow[];
  continuous?: RipContinuousIndRow[];
};

/** Riparian other-indicators (232). */
export type RipOtherIndRow = {
  otherIndTypeId?: string;
  quesSectCode?: string;
  headerQuestionInd?: string;
  question?: string;
  otherIndicatorId?: string;
  otherAnswerInd?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};
export type RiparianOtherIndicators = {
  checklistId?: string;
  indicators?: RipOtherIndRow[];
};

/** Riparian questions (233). */
export type RipQuestionRow = {
  checklistId?: string;
  checklistQuestionId?: string;
  questionNo?: string;
  question?: string;
  chanMorphologyCode?: string;
  applicableInd?: string;
  morphologyDesc?: string;
  questionType?: string;
  questionDesc?: string;
  subQuestion?: string;
  answerCode?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};
export type RipNoAnswerRow = {
  answerImpactId?: string;
  checklistId?: string;
  checklistQuestionId?: string;
  questionNo?: string;
  answerImpactType?: string;
  answerImpactDesc?: string;
  sortOrder?: string;
  answerInd?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};
export type RiparianQuestions = {
  checklistId?: string;
  questions?: RipQuestionRow[];
  noAnswers?: RipNoAnswerRow[];
};

/** Riparian specific impacts (234). */
export type RipOpenSpecImpactRow = {
  openingSpecificImpactId?: string;
  openingSpecificImpactType?: string;
  specImpactInd?: string;
  revisionCount?: string;
};
export type RipOtherSpecImpactRow = {
  otherRiparianSpecImpactId?: string;
  description?: string;
  specImpactInd?: string;
  revisionCount?: string;
};
export type RiparianSpecificImpacts = {
  checklistId?: string;
  openImpacts?: RipOpenSpecImpactRow[];
  otherImpacts?: RipOtherSpecImpactRow[];
};

/** Water (250-253) types. */
export type WtrDisturbanceRow = {
  disturbanceId?: string;
  checklistId?: string;
  disturbanceCode?: string;
  disturbanceAgeCode?: string;
  disturbanceNumber?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};
export type WtrAccessRoadRow = {
  accessRoadId?: string;
  checklistId?: string;
  accessRoadType?: string;
  accessRoadDesc?: string;
  accessRoadStatusCode?: string;
  approximateRoadLength?: string;
  approximateRoadAge?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};
export type WaterSampleArea = {
  waterChecklistId?: string;
  frepResourceValueId?: string;
  statusCode?: string;
  siteAccessCode?: string;
  mainAccessRoadNumber?: string;
  mainWatershedDescription?: string;
  drinkingWaterAnswerCode?: string;
  waterIntakeComment?: string;
  intakeToCutblockDistance?: string;
  waterIntakeConnectivityCode?: string;
  intakeToCutblockComment?: string;
  specResourceAnswerCode?: string;
  specialResourceValueComment?: string;
  reportedDisturbanceInd?: string;
  fertilizerUseOnRoadInd?: string;
  fertilizerUseWithinBlckInd?: string;
  sensitiveSoilAnswerCode?: string;
  herbicideUseOnRoadInd?: string;
  herbicideUseWithinBlockInd?: string;
  pesticideUseOnRoadInd?: string;
  pesticideUseWithinBlockInd?: string;
  streamCrossingsInd?: string;
  roadsParallelToStreamInd?: string;
  unstableSlopesInd?: string;
  sensitiveSoilsInd?: string;
  adjacentHarvestingInd?: string;
  livestockConcernsInd?: string;
  otherActivityInd?: string;
  otherActivityDescription?: string;
  noteDescription?: string;
  blockAccessTime?: string;
  hoursOnBlock?: string;
  peopleOnBlock?: string;
  invasivePlantAnswerCode?: string;
  invasivePlantComment?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
  disturbances?: WtrDisturbanceRow[];
  accessRoads?: WtrAccessRoadRow[];
};
export type WaterSampleSite = {
  waterSampleSiteId?: string;
  waterChecklistId?: string;
  statusCode?: string;
  waterSiteType?: string;
  waterStreamWidthCode?: string;
  evaluatorNameId?: string;
  domesticIntakeInd?: string;
  sampleSiteNumber?: string;
  utmSignal?: string;
  utmZone?: string;
  utmEasting?: string;
  utmNorthing?: string;
  roadTypeCode?: string;
  roadUseCode?: string;
  roadReference?: string;
  watershedReference?: string;
  communityWatershedInd?: string;
  rangeImpactEvaluationInd?: string;
  waterCompromisedInd?: string;
  otherObservedConditionInd?: string;
  otherObservedConditionDesc?: string;
  otherSolutionInd?: string;
  otherSolutionDescription?: string;
  assessmentComment?: string;
  rangeComment?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};
export type WtrAssessmentRow = {
  waterSampleSiteId?: string;
  activityGrpCode?: string;
  activityGrpDesc?: string;
  activityGrpCount?: string;
  assessmentType?: string;
  assessmentDesc?: string;
  assessmentInd?: string;
  revisionCount?: string;
  entryUserid?: string;
  updateUserid?: string;
};
export type WaterAssessment = {
  waterSampleSiteId?: string;
  conditions?: WtrAssessmentRow[];
  solutions?: WtrAssessmentRow[];
};
export type WaterRange = {
  waterSampleSiteId?: string;
  ranges?: WtrAssessmentRow[];
};

export const PROTOCOL_TYPE_TO_BACKEND: Record<ProtocolType, 'bio' | 'rip' | 'wat'> = {
  biodiversity: 'bio',
  riparian: 'rip',
  water: 'wat',
};

export const PROTOCOL_TYPE_LABEL: Record<ProtocolType, string> = {
  biodiversity: 'Biodiversity',
  riparian: 'Riparian',
  water: 'Water Quality',
};
