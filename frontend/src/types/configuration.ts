export type MasterListYear = {
  effectiveYear: string;
  label: string;
  current: boolean;
};

export type OrgUnit = {
  orgUnitNo: string;
  orgUnitCode: string;
  orgUnitName: string;
};

export type Protocol = {
  code: string;
  name: string;
};

export type RejectionReason = {
  code: string;
  description: string;
};

/** A generic code-list option ({@code code} + human-readable {@code description}) for dropdowns. */
export type CodeOption = {
  code: string;
  description: string;
};

/** Filters for the FAM evaluator (FREP-editor) search. All optional; blank = no filter. */
export type EvaluatorSearchParams = {
  userId?: string;
  firstName?: string;
  lastName?: string;
  page?: number;
  size?: number;
};

/** A page of FREP-editor candidates from the FAM evaluator search (code = IDIR username). */
export type EvaluatorSearchResult = {
  users: CodeOption[];
  total: number;
  page: number;
  size: number;
};

/** One BEC catalogue row returned by the FREP211 BEC search. */
export type BecRow = {
  bgcZoneCode?: string;
  bgcSubzoneCode?: string;
  bgcVariant?: string;
  bgcPhase?: string;
  becSiteSeriesCd?: string;
  siteSeriesPhaseCd?: string;
  seral?: string;
  description?: string;
};
