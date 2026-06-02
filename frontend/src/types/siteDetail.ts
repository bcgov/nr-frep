export type SiteResource = {
  resourceValueId: string | null;
  resourceType: string;
  resourceName: string;
  statusCode: 'ACC' | 'REJ' | 'TAR' | string;
  rejectionReasonCode: string | null;
  rationale: string | null;
  otherComments: string | null;
  checklistId: string | null;
  checklistStatusCode: string | null;
  revisionCount: string | null;
};

/** Subset of a resource the FREP110 screen submits to accept/reject/target it. */
export type SiteResourceSave = {
  resourceValueId: string | null;
  resourceType: string;
  statusCode: string;
  rejectionReasonCode: string | null;
  rationale: string | null;
  otherComments: string | null;
  revisionCount: string | null;
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
