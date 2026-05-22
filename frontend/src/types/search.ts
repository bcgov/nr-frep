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
};

export type ClientSearchResult = {
  clientNumber: string;
  clientName: string;
  clientStatus: string;
  locationCount: number;
};

export type ClientSearchQuery = {
  clientNumber?: string;
  clientName?: string;
};
