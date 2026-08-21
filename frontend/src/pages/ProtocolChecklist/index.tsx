import { ArrowLeft } from '@carbon/icons-react';
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
// Notes / Attachments are shared (named Rip* for legacy reasons) and used by Biodiversity. Riparian
// + Water are out of scope, so their dedicated editors are removed.
import RipAttachmentsView from './RipAttachmentsView';
import RipNotesView from './RipNotesView';
import { formatSubmitValidation } from './submitValidation';
import TabStatusIcon from './TabStatusIcon';
import { useTabStatuses } from './useTabStatuses';

import type { ProtocolChecklist, ProtocolType } from '@/types/protocolChecklist';

import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';
import { PROTOCOL_TYPE_LABEL, PROTOCOL_TYPE_TO_BACKEND } from '@/types/protocolChecklist';
import { apiErrorMessage } from '@/utils/apiError';
import { statusLabel, statusTagType } from '@/utils/checklistStatus';
import { formatShortDate } from '@/utils/date';
import { silvaOpeningUrl } from '@/utils/silva';

import './protocolChecklist.scss';

const extractValidationErrors = (err: unknown): string[] | null => {
  const body = (err as { body?: { validationErrors?: string[] } })?.body;
  return Array.isArray(body?.validationErrors) ? body.validationErrors : null;
};

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
  // Dedicated biodiversity route (/protocol-checklists/slr/:id) — the family is the route, so there is
  // no type param. The record's actual code (SLB legacy / SLR going forward) comes from the GET, not
  // the URL. The API contract still uses the 'bio' segment (unchanged here).
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit } = useAuthorization();
  const { user } = useAuth();

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

  // Per-tab completion dots. Held here rather than in each view so the whole strip is derived from
  // one read, and so a save on any tab can move another tab's dot (stratum plot counts vs Plots).
  const {
    statuses: tabStatuses,
    counts: tabCounts,
    outstanding: tabOutstanding,
    refresh: refreshTabStatuses,
    evaluate: evaluateTabs,
  } = useTabStatuses(id, !!checklist);

  // Outstanding work found by the submit pre-flight, keyed by section id. Set when Submit is pressed
  // and the checklist is not ready; cleared on the next attempt.
  const [preflight, setPreflight] = useState<Record<string, string[]>>({});
  // Once Submit has been pressed, every tab shows its count — including the ones held back for never
  // having been opened. The user has asked the question, so the answer stops being a nag.
  const [countsRevealed, setCountsRevealed] = useState(false);

  const protocolType: ProtocolType = 'biodiversity';
  const backendCode = PROTOCOL_TYPE_TO_BACKEND[protocolType];

  useEffect(() => {
    if (!id) {
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
        const message = apiErrorMessage(err);
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

  // Opening ID deep-links into SILVA, carrying an idp_hint for the provider the user signed in
  // with so they land on the opening without a second login. Opens in a new tab — the checklist
  // may hold unsaved edits — with rel="noopener noreferrer" so the opened page gets no handle on
  // this window. Falls back to the plain cell when the record has no opening id.
  const openingIdCell = (value: string | undefined) => {
    const href = silvaOpeningUrl(value, user?.idpProvider);
    if (!href) return headerCell('Opening ID', value);
    return (
      <div key="Opening ID">
        <span className="protocol-checklist__label">Opening ID</span>
        <span>
          <a href={href} target="_blank" rel="noopener noreferrer">
            {value}
          </a>
        </span>
      </div>
    );
  };

  const handleSubmit = async () => {
    if (!backendCode) return;
    setBusy(true);
    setValidationErrors([]);
    setPreflight({});
    try {
      // Pre-flight every tab against current data before troubling the proc. This is what catches
      // the checklist whose Opening tab was never opened: those tabs are deliberately quiet until
      // they have been saved, so without this the first news of the problem would be a rejected
      // submit. The proc stays authoritative — a clean pre-flight still submits and can still be
      // refused; this only stops us asking when the answer is already known.
      let blocking: Record<string, string[]> = {};
      try {
        const { outstanding } = await evaluateTabs();
        blocking = Object.fromEntries(
          Object.entries(outstanding).filter(([, items]) => items.length > 0),
        );
      } catch {
        // The pre-flight is an early warning, not an authority. If its read fails we say nothing and
        // submit anyway: refusing because we could not check would be worse than asking the proc.
      }
      if (Object.keys(blocking).length > 0) {
        setPreflight(blocking);
        setCountsRevealed(true);
        display({ kind: 'warning', title: 'Submit blocked by validation', timeout: 6000 });
        return;
      }

      await API.protocolChecklist.submit(backendCode, id);
      display({ kind: 'success', title: 'Checklist submitted', timeout: 5000 });
      setCountsRevealed(false);
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
          subtitle: apiErrorMessage(err),
          timeout: 9000,
        });
      }
    } finally {
      setBusy(false);
    }
  };

  // The tabs the pre-flight objected to, named in the page-level banner. The items themselves stay
  // on the tabs that own them, so there is one place to read them and one place to fix them.
  const preflightTabNames = (() => {
    const names = (checklist?.sections ?? [])
      .filter((section) => preflight[section.id]?.length)
      .map((section) => section.title);
    if (names.length <= 1) return names[0] ?? 'A tab';
    return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
  })();

  // A tab held back for never having been saved starts showing its count once Submit has been
  // pressed — at that point the silence would be hiding the very thing the user asked about.
  const statusFor = (sectionId: string) => {
    const status = tabStatuses[sectionId] ?? 'empty';
    return countsRevealed && status === 'empty' && (tabCounts[sectionId] ?? 0) > 0
      ? 'errors'
      : status;
  };

  // A tab lists its outstanding items exactly when its dot is red. That keeps the two in step: a
  // quiet dot on a never-opened tab means a quiet tab, and pressing Submit turns both on at once.
  const visibleOutstanding = (sectionId: string): string[] =>
    statusFor(sectionId) === 'errors' ? (tabOutstanding[sectionId] ?? []) : [];

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
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
    } finally {
      setBusy(false);
    }
  };

  const submitted = checklist?.statusCode === 'SUB';
  // Historical biodiversity records carry code SLB and are view-only in the new app (SLR is the
  // go-forward code). The backend also 403s any SLB mutation — this just hides the edit affordances.
  const isLegacySlb = checklist?.protocolType === 'SLB';
  const editable = canEdit && !isLegacySlb;

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
          {/* Title row: heading left, the checklist-level action right. Kept on one line so the
              primary action sits at the top of the page rather than below the tombstone tile. */}
          <div className="protocol-checklist__title-row">
            <h1>
              {protocolType ? `${id}-${PROTOCOL_TYPE_LABEL[protocolType]}` : 'Protocol checklist'}
            </h1>
            {!loading && !notFound && !hasError && checklist && editable && (
              <div className="protocol-checklist__actions">
                {submitted ? (
                  <Button kind="tertiary" onClick={() => void handleUnsubmit()} disabled={busy}>
                    Unsubmit
                  </Button>
                ) : (
                  <Button onClick={() => void handleSubmit()} disabled={busy}>
                    Submit
                  </Button>
                )}
              </div>
            )}
          </div>
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
          {(submitted || isLegacySlb) && (
            <Column sm={4} md={8} lg={16}>
              <InlineNotification
                kind="info"
                title="Read only"
                subtitle={
                  isLegacySlb
                    ? 'This is a historical Stand Level Retention (SLB) record and is read-only.'
                    : 'This checklist has been submitted and is read-only. Unsubmit it to make changes.'
                }
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
                {headerCell('Client number', headerExtras['Client'])}
                {headerCell('Client name', headerExtras['Client name'])}
                {headerCell('Opening number', checklist.openingNumber, true)}
                {openingIdCell(headerExtras['Opening ID'])}
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

          {Object.keys(preflight).length > 0 && (
            <Column sm={4} md={8} lg={16}>
              <InlineNotification
                className="protocol-checklist__preflight"
                kind="error"
                hideCloseButton
                lowContrast
                title="This checklist isn't ready to submit"
                subtitle={`${preflightTabNames} ${
                  Object.keys(preflight).length === 1 ? 'has' : 'have'
                } required fields outstanding. Fix the items listed on each tab, then submit again.`}
              />
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
                  <Tab key={section.id}>
                    <span className="protocol-checklist__tab-label">
                      <TabStatusIcon
                        status={statusFor(section.id)}
                        count={tabCounts[section.id]}
                        section={section.title}
                      />
                      {section.title}
                    </span>
                  </Tab>
                ))}
              </TabList>
              <TabPanels>
                {/* All Biodiversity sections edit inline (their own Edit/Save). */}
                {checklist.sections.map((section, i) => (
                  <TabPanel key={section.id}>
                    {section.id === 'notes' ? (
                      <RipNotesView
                        protocol={backendCode ?? ''}
                        checklistId={id}
                        canEdit={editable}
                        submitted={submitted}
                      />
                    ) : section.id === 'attachments' ? (
                      <RipAttachmentsView
                        protocol={backendCode ?? ''}
                        checklistId={id}
                        canEdit={editable}
                        submitted={submitted}
                      />
                    ) : section.id === 'opening' ? (
                      <BioOpeningView
                        checklistId={id}
                        canEdit={editable}
                        submitted={submitted}
                        onSaved={refreshTabStatuses}
                        revealOutstanding={countsRevealed}
                      />
                    ) : section.id === 'stratum' ? (
                      <BioStratumView
                        checklistId={id}
                        canEdit={editable}
                        submitted={submitted}
                        onSaved={refreshTabStatuses}
                        outstanding={visibleOutstanding('stratum')}
                      />
                    ) : section.id === 'plots' ? (
                      <BioPlotsView
                        checklistId={id}
                        canEdit={editable}
                        submitted={submitted}
                        active={i === tabIndex}
                        onSaved={refreshTabStatuses}
                        outstanding={visibleOutstanding('plots')}
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
