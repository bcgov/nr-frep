export type AcceptedSite = {
  checklistId: string;
  checklistType: string;
  sampleNumber: string;
  targeted: boolean;
  openingNumber: string;
  openingId: string;
  licenceId: string;
  cuttingPermitId: string;
  cutBlockId: string;
  harvestCompleteDate: string;
  checklistStatusCode: string;
  checklistStatus: string;
  protocolCode: string;
  protocolName: string;
  effectiveYear: string;
  orgUnitNo: string;
};

export type AcceptedSitesQuery = {
  effectiveYear: string;
  orgUnit: string;
  protocolType?: string;
};

/** One opening from the "Add Target Site" opening search (legacy SIL56 Opening Tenure Search). */
export type OpeningSearchResult = {
  openingId: string;
  openingNumber: string;
  forestFileId: string;
  cuttingPermitId: string;
  timberMark: string;
  cutBlockId: string;
  grossArea: string;
  openCategoryCode: string;
  openingStatusCode: string;
  amendmentInd: string;
  licenseeOpeningId: string;
  adminDistrictNo: string;
};

/**
 * Opening-search filters, ported from the legacy SIL56 Opening Tenure Search. `orgUnit` is required;
 * everything else is optional, and a non-blank `openingId` ignores all other filters. The four
 * `openingNumber*` parts are the mapsheet pieces. The date fields are paired with `dateType`.
 */
export type OpeningSearchQuery = {
  orgUnit: string;
  clientNumber?: string;
  clientLocnCode?: string;
  openingNumber1?: string;
  openingNumber2?: string;
  openingNumber3?: string;
  openingNumber4?: string;
  forestFileId?: string;
  openingId?: string;
  licenseeOpeningId?: string;
  cuttingPermitId?: string;
  timberMark?: string;
  cutBlockId?: string;
  blockStatusSt?: string;
  openCategoryCode?: string;
  openingStatusCode?: string;
  dateType?: string;
  distStartDate?: string;
  distEndDate?: string;
  dueLateDateFrom?: string;
  dueLateDateTo?: string;
  fgDueEarlyDate?: string;
  fgDueLateDate?: string;
  updateDateFrom?: string;
  updateDateTo?: string;
  includeAllP87Ind?: string;
  sortBy?: string;
  /** Zero-based page index (backend caps the page size at 100). */
  pageNumber?: number;
  pageSize?: number;
};

/** Result of validating an opening for targeting (FREP_200_ACCEPTED_SITES.ADD_TARGETED_SITE). */
export type TargetedSiteValidationResponse = {
  valid: boolean;
  messages: string[];
  openingId: string;
  orgUnit: string;
};
