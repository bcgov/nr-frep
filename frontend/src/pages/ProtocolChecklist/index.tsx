import {
  ArrowLeft,
  Attachment,
  Document,
  Information,
  Layers,
  Location,
  Notebook,
  Settings,
  type CarbonIconType,
} from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  Tag,
  Tile,
} from '@carbon/react';
import { useEffect, useMemo, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import BioOpeningView from './BioOpeningView';
import BioPlotsView from './BioPlotsView';
import BioStratumView from './BioStratumView';
// Administration / Notes / Attachments are shared (named Rip* for legacy reasons) and used by
// Biodiversity. Riparian + Water are out of scope, so their dedicated editors are removed.
import RipAdministrationView from './RipAdministrationView';
import RipAttachmentsView from './RipAttachmentsView';
import RipNotesView from './RipNotesView';
import { formatSubmitValidation } from './submitValidation';

import type { ProtocolChecklist, ProtocolType } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';
import { PROTOCOL_TYPE_LABEL, PROTOCOL_TYPE_TO_BACKEND } from '@/types/protocolChecklist';
import { statusLabel, statusTagType } from '@/utils/checklistStatus';
import { formatShortDate } from '@/utils/date';

import './protocolChecklist.scss';

// Per-section tab icons (keyed by the backend section id), mirroring the contained-tab style with
// an icon beside each label. Unknown sections fall back to a generic document icon.
const SECTION_ICONS: Record<string, CarbonIconType> = {
  administration: Settings,
  opening: Information,
  stratum: Layers,
  plots: Location,
  notes: Notebook,
  attachments: Attachment,
};

const extractValidationErrors = (err: unknown): string[] | null => {
  const body = (err as { body?: { validationErrors?: string[] } })?.body;
  return Array.isArray(body?.validationErrors) ? body.validationErrors : null;
};

function isProtocolType(value: string | undefined): value is ProtocolType {
  return value === 'biodiversity';
}

// Tombstone fields the legacy screen shows in the page header band rather than in a section. The
// backend returns these inside the section reads; we promote them to the header (in legacy order)
// and hide them from the section field list to mirror the legacy layout.
const HEADER_EXTRA_LABELS = [
  'Org unit',
  'Client',
  'Client name',
  'Opening ID',
  'Licence',
  'Cutting permit',
  'Cut block',
  'Sample #',
] as const;
const HEADER_EXTRA_LABEL_SET = new Set<string>(HEADER_EXTRA_LABELS);

const ProtocolChecklistPage: FC = () => {
  const { type, id = '' } = useParams<{ type: string; id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();

  const [checklist, setChecklist] = useState<ProtocolChecklist | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [reloadKey, setReloadKey] = useState(0);
  // Carbon keeps every TabPanel mounted, so sibling tabs (e.g. Plots) hold data loaded once on
  // mount. Track the active tab so a view can refetch when it becomes visible.
  const [tabIndex, setTabIndex] = useState(0);

  const protocolType: ProtocolType | null = isProtocolType(type) ? type : null;
  const backendCode = protocolType ? PROTOCOL_TYPE_TO_BACKEND[protocolType] : null;

  useEffect(() => {
    if (!protocolType || !id) {
      setLoading(false);
      setNotFound(true);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setHasError(false);

    API.protocolChecklist
      .getChecklist(PROTOCOL_TYPE_TO_BACKEND[protocolType], id)
      .then((data) => {
        if (cancelled) return;
        setChecklist(data);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const status = (err as { status?: number })?.status;
        if (status === 404) {
          setNotFound(true);
          return;
        }
        const message = err instanceof Error ? err.message : 'Unknown error';
        display({
          kind: 'error',
          title: "We couldn't load the checklist",
          subtitle: message,
          timeout: 9000,
        });
        setHasError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [display, id, protocolType, reloadKey]);

  // Tombstone fields the backend returns inside section reads, lifted to the page header band.
  const headerExtras = useMemo(() => {
    const map: Record<string, string> = {};
    checklist?.sections.forEach((section) =>
      section.fields.forEach((field) => {
        if (HEADER_EXTRA_LABEL_SET.has(field.label) && !map[field.label] && field.value) {
          map[field.label] = field.value;
        }
      }),
    );
    return map;
  }, [checklist]);

  // Render a header cell; core fields are always shown, promoted extras only when they have a value.
  const headerCell = (label: string, value: string | undefined, always = false) =>
    always || value ? (
      <div key={label}>
        <span className="protocol-checklist__label">{label}</span>
        <span>{value ?? ''}</span>
      </div>
    ) : null;

  const handleSubmit = async () => {
    if (!backendCode) return;
    setBusy(true);
    setValidationErrors([]);
    try {
      await API.protocolChecklist.submit(backendCode, id);
      display({ kind: 'success', title: 'Checklist submitted', timeout: 5000 });
      setReloadKey((k) => k + 1);
    } catch (err) {
      const validation = extractValidationErrors(err);
      if (validation) {
        setValidationErrors(validation);
        display({ kind: 'warning', title: 'Submit blocked by validation', timeout: 6000 });
      } else {
        display({
          kind: 'error',
          title: 'Submit failed',
          subtitle: err instanceof Error ? err.message : 'Unknown error',
          timeout: 9000,
        });
      }
    } finally {
      setBusy(false);
    }
  };

  const handleUnsubmit = async () => {
    if (!backendCode) return;
    setBusy(true);
    try {
      await API.protocolChecklist.unsubmit(backendCode, id);
      display({ kind: 'success', title: 'Checklist reopened', timeout: 5000 });
      setReloadKey((k) => k + 1);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Unsubmit failed',
        subtitle: err instanceof Error ? err.message : 'Unknown error',
        timeout: 9000,
      });
    } finally {
      setBusy(false);
    }
  };

  const submitted = checklist?.statusCode === 'SUB';

  return (
    <Grid fullWidth className="default-grid protocol-checklist-grid">
      <Column sm={4} md={8} lg={16}>
        <div className="protocol-checklist__header">
          <button
            type="button"
            className="protocol-checklist__back"
            onClick={() => navigate(-1)}
            aria-label="Back"
          >
            <ArrowLeft /> Back
          </button>
          <h1>{protocolType ? `${id}-${PROTOCOL_TYPE_LABEL[protocolType]}` : 'Protocol checklist'}</h1>
        </div>
      </Column>

      {loading && (
        <Column sm={4} md={8} lg={16}>
          <SkeletonText paragraph lineCount={6} data-testid="protocol-checklist-loading" />
        </Column>
      )}

      {!loading && notFound && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="warning"
            title="Checklist not found"
            subtitle={`No ${protocolType ?? 'protocol'} checklist exists for id ${id}.`}
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      {!loading && hasError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            title="Unable to load checklist"
            subtitle="Please try again later."
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      {!loading && !notFound && !hasError && checklist && (
        <>
          {submitted && (
            <Column sm={4} md={8} lg={16}>
              <InlineNotification
                kind="info"
                title="Read only"
                subtitle="This checklist has been submitted and is read-only. Unsubmit it to make changes."
                hideCloseButton
                lowContrast
              />
            </Column>
          )}

          <Column sm={4} md={8} lg={16}>
            <Tile className="protocol-checklist__summary">
              <div className="protocol-checklist__summary-grid">
                {/* Tombstone header laid out like the legacy screen. */}
                {headerCell('Master list year', checklist.effectiveYear, true)}
                {headerCell('Org unit', headerExtras['Org unit'])}
                {headerCell('Checklist', checklist.checklistId, true)}
                {headerCell('Client', headerExtras['Client'])}
                {headerCell('Client name', headerExtras['Client name'])}
                {headerCell('Opening', checklist.openingNumber, true)}
                {headerCell('Opening ID', headerExtras['Opening ID'])}
                {headerCell('Licence', headerExtras['Licence'])}
                {headerCell('Cutting permit', headerExtras['Cutting permit'])}
                {headerCell('Cut block', headerExtras['Cut block'])}
                <div>
                  <span className="protocol-checklist__label">Status</span>
                  <Tag type={statusTagType(checklist.statusCode)} size="sm">
                    {statusLabel(checklist.statusCode, checklist.statusLabel)}
                  </Tag>
                </div>
                {headerCell('Evaluator', checklist.evaluatorName, true)}
                {headerCell('Evaluation date', formatShortDate(checklist.evaluationDate), true)}
                {headerCell('Sample #', headerExtras['Sample #'])}
              </div>
            </Tile>
          </Column>

          {canEdit && (
            <Column sm={4} md={8} lg={16}>
              <div className="protocol-checklist__actions">
                {!submitted && (
                  <Button onClick={() => void handleSubmit()} disabled={busy}>
                    Submit
                  </Button>
                )}
                {submitted && (
                  <Button kind="tertiary" onClick={() => void handleUnsubmit()} disabled={busy}>
                    Unsubmit
                  </Button>
                )}
              </div>
            </Column>
          )}

          {validationErrors.length > 0 && (
            <Column sm={4} md={8} lg={16}>
              <p className="protocol-checklist__errors-intro">
                This checklist isn&apos;t ready to submit. Fix the following, then submit again:
              </p>
              <div className="protocol-checklist__errors">
                {validationErrors.map((code) => {
                  const { title, detail } = formatSubmitValidation(code);
                  return (
                    <InlineNotification
                      key={code}
                      kind="error"
                      title={title}
                      subtitle={detail}
                      hideCloseButton
                      lowContrast
                    />
                  );
                })}
              </div>
            </Column>
          )}

          <Column sm={4} md={8} lg={16}>
            <Tabs
              selectedIndex={tabIndex}
              onChange={({ selectedIndex }) => setTabIndex(selectedIndex)}
            >
              <TabList aria-label="Checklist sections" contained>
                {checklist.sections.map((section) => (
                  <Tab key={section.id} renderIcon={SECTION_ICONS[section.id] ?? Document}>
                    {section.title}
                  </Tab>
                ))}
              </TabList>
              <TabPanels>
                {/* All Biodiversity sections edit inline (their own Edit/Save). */}
                {checklist.sections.map((section, i) => (
                  <TabPanel key={section.id}>
                    {section.id === 'administration' ? (
                      <RipAdministrationView
                        protocol={backendCode ?? ''}
                        checklistId={id}
                        canEdit={canEdit}
                        submitted={submitted}
                      />
                    ) : section.id === 'notes' ? (
                      <RipNotesView
                        protocol={backendCode ?? ''}
                        checklistId={id}
                        canEdit={canEdit}
                        submitted={submitted}
                      />
                    ) : section.id === 'attachments' ? (
                      <RipAttachmentsView
                        protocol={backendCode ?? ''}
                        checklistId={id}
                        canEdit={canEdit}
                        submitted={submitted}
                      />
                    ) : section.id === 'opening' ? (
                      <BioOpeningView checklistId={id} canEdit={canEdit} submitted={submitted} />
                    ) : section.id === 'stratum' ? (
                      <BioStratumView checklistId={id} canEdit={canEdit} submitted={submitted} />
                    ) : section.id === 'plots' ? (
                      <BioPlotsView
                        checklistId={id}
                        canEdit={canEdit}
                        submitted={submitted}
                        active={i === tabIndex}
                      />
                    ) : null}
                  </TabPanel>
                ))}
              </TabPanels>
            </Tabs>
          </Column>
        </>
      )}
    </Grid>
  );
};

export default ProtocolChecklistPage;
