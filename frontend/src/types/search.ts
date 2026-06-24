export type ChecklistSearchResult = {
  checklistId: string;
  protocolCode: string;
  protocolName: string;
  effectiveYear: string;
  orgUnitCode: string;
  licenceId: string;
  cuttingPermitId: string;
  cutBlockId: string;
  openingId: string;
  clientNumber: string;
  evaluationDate: string;
  evaluatorUserid: string;
  /** Evaluator's display name when they have FREP access (resolved via FAM), else the userid. */
  evaluatorName: string;
  checklistStatusCode: string;
  checklistStatus: string;
};

export type ChecklistSearchQuery = {
  effectiveYear?: string;
  orgUnit?: string;
  protocolType?: string;
  licenceId?: string;
  cuttingPermitId?: string;
  cutBlockId?: string;
  openingId?: string;
  clientNumber?: string;
  checklistStatusCode?: string;
  checklistId?: string;
  evaluationDateFrom?: string;
  evaluationDateTo?: string;
};

export type PagedResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
};

export type ChecklistSearchPageQuery = ChecklistSearchQuery & {
  pageNumber?: number;
  pageSize?: number;
  // "field" or "field,(asc|desc)" over the backend's whitelisted sort fields.
  sort?: string;
};

export type ClientSearchResult = {
  clientAcronym: string;
  clientNumber: string;
  clientLocnCode: string;
  clientName: string;
  clientLocnName: string;
  city: string;
  clientStatus: string;
};

export type ClientSearchQuery = {
  clientNumber?: string;
  clientAcronym?: string;
  clientName?: string;
  legalFirstName?: string;
  legalMiddleName?: string;
};
