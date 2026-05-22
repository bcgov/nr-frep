export type RandomListSite = {
  frepSelectedSiteId: string;
  underReview: boolean;
  orgUnitCode: string;
  openingNumber: string;
  openingId: string;
  licenceId: string;
  cuttingPermitId: string;
  cutBlockId: string;
  grossArea: number | null;
  netArea: number | null;
  disturbanceStartDate: string | null;
  disturbanceEndDate: string | null;
  existingChecklists: string[];
};

export type RandomListQuery = {
  effectiveYear: string;
  orgUnit?: string;
};
