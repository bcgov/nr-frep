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
