import { Accordion, AccordionItem, Column, Grid, InlineNotification } from '@carbon/react';
import { useEffect, useMemo, useState, type FC } from 'react';

import ReportConfigForm from './ReportConfigForm';
import { GENERATABLE_REPORTS, type GeneratableReport } from './reportDefinitions';

import type { CodeOption, MasterListYear, OrgUnit } from '@/types/configuration';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';

import './reports.scss';

// Two-cell row that drives both the header band and the accordion title. Grid
// alignment is set in reports.scss (.reports-row).
const ReportAccordionTitle: FC<{ definition: GeneratableReport }> = ({ definition }) => (
  <div className="reports-row">
    <div className="reports-row__cell reports-row__cell--name">{definition.title}</div>
    <div className="reports-row__cell reports-row__cell--description">{definition.summary}</div>
  </div>
);

const ReportsPage: FC = () => {
  const { display } = useNotification();
  const { canEdit, canAnyChr } = useAuthorization();

  // Only show reports the user may run: the CHR extract needs CHR access; the Biodiversity extracts
  // need Bio access. Other (checklist) reports are unrestricted here. The backend also enforces this.
  const visibleReports = useMemo(
    () =>
      GENERATABLE_REPORTS.filter((report) => {
        if (report.id === 'chr-data-extract') return canAnyChr;
        if (report.id.startsWith('biodiversity-extract')) return canEdit;
        return true;
      }),
    [canEdit, canAnyChr],
  );

  const [openId, setOpenId] = useState<string | null>(null);

  // Reference data shared by every report form (loaded once, only when needed).
  const [orgUnits, setOrgUnits] = useState<OrgUnit[]>([]);
  const [loading, setLoading] = useState(true);
  const [masterListYears, setMasterListYears] = useState<MasterListYear[]>([]);
  const [resourceValueStatuses, setResourceValueStatuses] = useState<CodeOption[]>([]);
  const [checklistStatuses, setChecklistStatuses] = useState<CodeOption[]>([]);

  useEffect(() => {
    // Skip the lookups entirely while there are no generatable reports.
    if (GENERATABLE_REPORTS.length === 0) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    Promise.all([
      API.configuration.getOrgUnits(),
      API.configuration.getMasterListYears(),
      API.configuration.getResourceValueStatusCodes(),
      API.configuration.getChecklistStatusCodes(),
    ])
      .then(([orgs, years, statuses, checklistStatusCodes]) => {
        if (cancelled) return;
        setOrgUnits(orgs);
        setMasterListYears(years);
        setResourceValueStatuses(statuses);
        setChecklistStatuses(checklistStatusCodes);
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'warning',
            title: 'Report filters unavailable',
            subtitle:
              "We couldn't load the report filter options. Try again, or pick values manually.",
            timeout: 8000,
          });
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [display]);

  return (
    <Grid fullWidth className="default-grid reports-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="reports__title">Exports</h1>
        <p className="reports__subtitle">Generate FREP exports below.</p>
      </Column>

      <Column sm={4} md={8} lg={16}>
        {visibleReports.length === 0 ? (
          <InlineNotification
            kind="info"
            title="No reports available to generate yet"
            subtitle="The generation pipeline is wired and ready; FREP report templates will be added here as they're ported from the legacy catalog."
            hideCloseButton
            lowContrast
          />
        ) : (
          <div className="reports-accordion-table">
            <div className="reports-row reports-row--header">
              <div className="reports-row__cell reports-row__cell--name">Report</div>
              <div className="reports-row__cell reports-row__cell--description">Description</div>
            </div>
            <Accordion className="reports-accordion" align="start">
              {visibleReports.map((report) => (
                <AccordionItem
                  key={report.id}
                  open={openId === report.id}
                  onHeadingClick={({ isOpen }) => setOpenId(isOpen ? report.id : null)}
                  title={<ReportAccordionTitle definition={report} />}
                >
                  <ReportConfigForm
                    definition={report}
                    orgUnits={orgUnits}
                    masterListYears={masterListYears}
                    resourceValueStatuses={resourceValueStatuses}
                    checklistStatuses={checklistStatuses}
                    loading={loading}
                  />
                </AccordionItem>
              ))}
            </Accordion>
          </div>
        )}
      </Column>
    </Grid>
  );
};

export default ReportsPage;
