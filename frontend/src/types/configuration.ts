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
