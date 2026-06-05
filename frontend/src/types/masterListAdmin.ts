export type MasterListGenerationStat = {
  orgUnitNo: string;
  orgUnitCode: string;
  orgUnitName: string;
  eligibleSites: number;
  selectedSites: number;
};

export type MasterListAdmin = {
  effectiveYear: string;
  minHarvestCompleteDate: string;
  maxHarvestCompleteDate: string;
  minOpeningGrossAreaHa: number | null;
  maxSitesPerDistrict: number | null;
  resourceEvaluatedInd: string;
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
  resourceEvaluatedInd?: string;
  comments?: string;
};
