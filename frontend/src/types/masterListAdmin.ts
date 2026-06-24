export type MasterListGenerationStat = {
  orgUnitNo: string;
  orgUnitCode: string;
  orgUnitName: string;
  eligibleSites: number;
  selectedSites: number;
  /** Legacy resource_value_ind: 'Y' when this district already has evaluated resources (Regenerate disabled). */
  resourceValueInd: string;
};

export type MasterListAdmin = {
  effectiveYear: string;
  minHarvestCompleteDate: string;
  maxHarvestCompleteDate: string;
  minOpeningGrossAreaHa: number | null;
  maxSitesPerDistrict: number | null;
  /** Legacy resource_evaluation_ind lock flag: '' = no list, 'N' = generated, 'Y' = evaluations under way. */
  resourceEvaluationInd: string;
  generationComments: string;
  generated: boolean;
  generationStats: MasterListGenerationStat[];
};

export type GenerateMasterListRequest = {
  effectiveYear: string;
  minHarvestCompleteDate?: string;
  maxHarvestCompleteDate?: string;
  minOpeningGrossAreaHa?: number | null;
  maxSitesPerDistrict?: number | null;
  comments?: string;
};
