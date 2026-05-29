export type FrepReportDefinition = {
  id: string;
  title: string;
  category: string;
  description: string;
  adminOnly?: boolean;
};

/** Legacy Jasper catalog (FREPRPT on JCRS). Read-only placeholder until Phase 4 migration. */
export const FREP_REPORT_DEFINITIONS: FrepReportDefinition[] = [
  {
    id: 'FREPRPT001',
    title: 'Biodiversity extract',
    category: 'Biodiversity',
    description: 'Opening-level biodiversity checklist extract.',
  },
  {
    id: 'FREPRPT002',
    title: 'Biodiversity extract',
    category: 'Biodiversity',
    description: 'District biodiversity summary extract.',
  },
  {
    id: 'FREPRPT003',
    title: 'Biodiversity extract',
    category: 'Biodiversity',
    description: 'Provincial biodiversity listing.',
  },
  {
    id: 'FREPRPT004',
    title: 'Biodiversity extract',
    category: 'Biodiversity',
    description: 'Biodiversity targeted-site extract.',
  },
  {
    id: 'FREPRPT005',
    title: 'Biodiversity extract',
    category: 'Biodiversity',
    description: 'Biodiversity results export.',
  },
  {
    id: 'FREPRPT006',
    title: 'Riparian extract',
    category: 'Riparian',
    description: 'Riparian stream opening extract.',
  },
  {
    id: 'FREPRPT007',
    title: 'Riparian extract',
    category: 'Riparian',
    description: 'Riparian district summary.',
  },
  {
    id: 'FREPRPT009',
    title: 'Riparian extract',
    category: 'Riparian',
    description: 'Riparian checklist listing.',
  },
  {
    id: 'FREPRPT010',
    title: 'Riparian extract',
    category: 'Riparian',
    description: 'Riparian results export.',
  },
  {
    id: 'FREPRPT011',
    title: 'Riparian extract',
    category: 'Riparian',
    description: 'Riparian targeted-site extract.',
  },
  {
    id: 'FREPRPT012',
    title: 'Checklist statistics',
    category: 'Statistics',
    description: 'Provincial checklist statistics summary.',
  },
  {
    id: 'FREPRPT013',
    title: 'Water quality extract',
    category: 'Water quality',
    description: 'Water quality sample-area extract.',
  },
  {
    id: 'FREPRPT015',
    title: 'Water quality extract',
    category: 'Water quality',
    description: 'Water quality district summary.',
  },
  {
    id: 'FREPRPT016',
    title: 'Water quality extract',
    category: 'Water quality',
    description: 'Water quality results export.',
  },
  {
    id: 'FREPRPT017',
    title: 'Water quality extract',
    category: 'Water quality',
    description: 'Water quality targeted-site extract.',
  },
  {
    id: 'FREPRPT018',
    title: 'Rejection reasons',
    category: 'Statistics',
    description: 'Checklist rejection reason summary.',
  },
  {
    id: 'FREPRPT019',
    title: 'Riparian extract',
    category: 'Riparian',
    description: 'Riparian provincial listing.',
  },
  {
    id: 'FREPRPT020',
    title: 'Riparian extract',
    category: 'Riparian',
    description: 'Riparian opening detail export.',
  },
  {
    id: 'FREPRPT021',
    title: 'Water quality extract',
    category: 'Water quality',
    description: 'Water quality provincial listing.',
  },
  {
    id: 'FREPRPT022',
    title: 'CHR data extract',
    category: 'Culture Heritage',
    description: 'Culture Heritage administrative data extract.',
    adminOnly: true,
  },
];
