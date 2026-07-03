export type RandomListSite = {
  frepSelectedSiteId: string;
  underReview: boolean;
  orgUnitCode: string;
  openingNumber: string;
  openingId: string;
  licenceId: string;
  cuttingPermitId: string;
  cutBlockId: string;
  exhibitArea: number | null;
  grossArea: number | null;
  netArea: number | null;
  disturbanceStartDate: string | null;
  disturbanceEndDate: string | null;
  managementUnit: string | null;
  existingChecklists: string[];
};

export type RandomListSummary = {
  orgUnitDescription: string | null;
  biodiversity: number;
  culturalHeritage: number;
};

export type RandomListResponse = {
  summary: RandomListSummary;
  sites: RandomListSite[];
};

export type RandomListQuery = {
  effectiveYear: string;
  orgUnit?: string;
};
