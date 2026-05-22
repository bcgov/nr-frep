export type SiteResource = {
  resourceType: string;
  resourceName: string;
  statusCode: 'ACC' | 'REJ' | 'TAR' | string;
  rejectionReasonCode: string | null;
  rationale: string | null;
  otherComments: string | null;
  checklistId: string | null;
  checklistStatusCode: string | null;
};

export type SiteDetail = {
  frepSelectedSiteId: string;
  masterList: string;
  orgUnit: string;
  client: string;
  clientName: string;
  opening: string;
  openingId: string;
  actualOpening: string;
  licenceNo: string;
  actualLicence: string;
  cuttingPermitId: string;
  cutBlockId: string;
  fspLink: string;
  harvestYear: string;
  resources: SiteResource[];
};
