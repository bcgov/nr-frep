export type ProtocolType = 'biodiversity' | 'riparian' | 'water';

export type ProtocolFieldKind = 'TEXT' | 'NUMBER' | 'DATE' | 'YES_NO' | 'MULTILINE';

export type ProtocolChecklistField = {
  label: string;
  value: string;
  kind: ProtocolFieldKind;
};

export type ProtocolChecklistSection = {
  id: string;
  title: string;
  fields: ProtocolChecklistField[];
};

export type ProtocolChecklist = {
  checklistId: string;
  protocolType: 'BIO' | 'RIP' | 'WAT' | 'CHR';
  protocolName: string;
  frepSelectedSiteId: string;
  openingNumber: string;
  effectiveYear: string;
  statusCode: string;
  statusLabel: string;
  evaluatorUserid: string;
  evaluationDate: string;
  sections: ProtocolChecklistSection[];
};

export const PROTOCOL_TYPE_TO_BACKEND: Record<ProtocolType, 'bio' | 'rip' | 'wat'> = {
  biodiversity: 'bio',
  riparian: 'rip',
  water: 'wat',
};

export const PROTOCOL_TYPE_LABEL: Record<ProtocolType, string> = {
  biodiversity: 'Biodiversity',
  riparian: 'Riparian',
  water: 'Water Quality',
};
