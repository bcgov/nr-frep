import {
  ArrowLeft,
  Attachment,
  Document,
  Information,
  Layers,
  Location,
  Notebook,
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
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import BioOpeningView from './BioOpeningView';
import BioPlotsView from './BioPlotsView';
import BioStratumView from './BioStratumView';
// Notes / Attachments are shared (named Rip* for legacy reasons) and used by Biodiversity. Riparian
// + Water are out of scope, so their dedicated editors are removed.
import RipAttachmentsView from './RipAttachmentsView';
import RipNotesView from './RipNotesView';
import { formatSubmitValidation } from './submitValidation';

import type { BioAttachmentOp, OfflineBioChecklist } from '@/services/offline/bioDb';
import type { ProtocolChecklist, ProtocolType } from '@/types/protocolChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { CheckInBlockedError, checkInBioChecklist } from '@/services/offline/bioCheckIn';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';
import { TakeOfflineCancelled, takeBioChecklistOffline } from '@/services/offline/bioTakeOffline';
import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import API from '@/services/APIs';
import { PROTOCOL_TYPE_LABEL, PROTOCOL_TYPE_TO_BACKEND } from '@/types/protocolChecklist';
import { apiErrorMessage } from '@/utils/apiError';
import { statusLabel, statusTagType } from '@/utils/checklistStatus';
import { formatShortDate } from '@/utils/date';

import './protocolChecklist.scss';

// Per-section tab icons (keyed by the backend section id), mirroring the contained-tab style with
// an icon beside each label. Unknown sections fall back to a generic document icon.
const SECTION_ICONS: Record<string, CarbonIconType> = {
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

/**
 * Why the checklist is read-only. Ordered by precedence: a historical record can never be edited,
 * whatever its status; a checked-out one is temporarily locked to the device holding it.
 */
/** Bytes as a rounded MB, for the quota warning. */
const formatMb = (bytes: number): string =>
  Number.isFinite(bytes) ? `${Math.round(bytes / 1_000_000)} MB` : 'an unknown amount';

const readOnlyReason = ({
  isLegacySlb,
  checkedOut,
}: {
  isLegacySlb: boolean;
  checkedOut: boolean;
}): string => {
  if (isLegacySlb) {
    return 'This is a historical Stand Level Retention (SLB) record and is read-only.';
  }
  if (checkedOut) {
    return 'This checklist is checked out to a field device, so the online copy is read-only. '
      + 'Check it in from that device, or have an administrator reactivate it, to edit here.';
  }
  return 'This checklist has been submitted and is read-only. Unsubmit it to make changes.';
};

const ProtocolChecklistPage: FC = () => {
  // Dedicated biodiversity route (/protocol-checklists/slr/:id) — the family is the route, so there is
  // no type param. The record's actual code (SLB legacy / SLR going forward) comes from the GET, not
  // the URL. The API contract still uses the 'bio' segment (unchanged here).
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit, canPerformSysAdminActions } = useAuthorization();
  const confirm = useConfirm();
  const online = useOnlineStatus();

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
          subtitle: apiErrorMessage(err),
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
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      });
    } finally {
      setBusy(false);
    }
  };

  // Offline copy held on this device, if any. Drives which of Take offline / Sync changes is shown.
  const [offlineRecord, setOfflineRecord] = useState<OfflineBioChecklist | null>(null);
  const [offlineBusy, setOfflineBusy] = useState<string | null>(null);
  const [rejectedFiles, setRejectedFiles] = useState<BioAttachmentOp[]>([]);

  const refreshOfflineState = useCallback(async () => {
    const record = await bioOfflineRepo.load(id);
    setOfflineRecord(record ?? null);
    setRejectedFiles(record ? await bioOfflineRepo.rejectedAttachmentOps(id) : []);
  }, [id]);

  useEffect(() => {
    void refreshOfflineState();
  }, [refreshOfflineState]);

  /**
   * Pull the checklist onto this device.
   *
   * The progress text matters more than it looks: attachments dominate the wall-clock (files run to
   * 15 MB with no per-checklist cap), so without a per-file count a large checklist looks hung.
   */
  const handleTakeOffline = async () => {
    setOfflineBusy('Preparing…');
    try {
      await takeBioChecklistOffline(id, {
        onProgress: (progress) => {
          if (progress.phase === 'attachments' && progress.total) {
            setOfflineBusy(`Downloading files (${progress.done ?? 0} of ${progress.total})…`);
          } else if (progress.phase === 'reference') {
            setOfflineBusy('Downloading reference data…');
          } else if (progress.phase === 'checkout') {
            setOfflineBusy('Checking out…');
          } else {
            setOfflineBusy('Downloading checklist…');
          }
        },
        onQuotaWarning: (needBytes, availableBytes) =>
          confirm({
            title: 'Not enough room on this device?',
            message:
              `This checklist's files need about ${formatMb(needBytes)}, and only `
              + `${formatMb(availableBytes)} is free. Continuing may fail part-way. Continue anyway?`,
            confirmButtonText: 'Continue',
          }),
      });
      await refreshOfflineState();
      display({ kind: 'success', title: 'Saved to this device', timeout: 5000 });
    } catch (err) {
      // A cancelled quota warning is a choice, not a failure — say nothing.
      if (!(err instanceof TakeOfflineCancelled)) {
        display({
          kind: 'error',
          title: 'Could not take this checklist offline',
          subtitle: apiErrorMessage(err),
          timeout: 9000,
        });
      }
    } finally {
      setOfflineBusy(null);
    }
  };

  /** Check the local copy back in: attachments first, then the graph. */
  const handleCheckIn = async () => {
    setOfflineBusy('Checking in…');
    try {
      await checkInBioChecklist(id, {
        onProgress: (progress) => {
          if (progress.phase === 'attachments' && progress.total) {
            setOfflineBusy(`Uploading files (${progress.done ?? 0} of ${progress.total})…`);
          } else if (progress.phase === 'graph') {
            setOfflineBusy('Saving to the server…');
          }
        },
      });
      await refreshOfflineState();
      display({ kind: 'success', title: 'Checked in', timeout: 5000 });
      setReloadKey((k) => k + 1);
    } catch (err) {
      await refreshOfflineState();
      display({
        kind: 'error',
        title: 'Check in stopped',
        subtitle: err instanceof CheckInBlockedError
          ? err.message
          : apiErrorMessage(err),
        timeout: 9000,
      });
    } finally {
      setOfflineBusy(null);
    }
  };

  /** Discard one file the server refused, so the check-in is no longer blocked by it. */
  const handleDiscardRejected = async (op: BioAttachmentOp) => {
    if (
      !(await confirm({
        title: 'Discard this file?',
        message:
          `"${op.fileName ?? 'This file'}" was refused by the server and has never been uploaded. `
          + 'Discarding it deletes it from this device permanently.',
        confirmButtonText: 'Discard',
      }))
    ) {
      return;
    }
    if (op.id !== undefined) await bioOfflineRepo.discardAttachmentOp(op.id);
    await refreshOfflineState();
  };

  /**
   * Admin recovery for a checkout stranded on a lost or wiped device (RDO → ACT).
   *
   * Clearing the token is the point — and the cost: whatever is still on that device can never be
   * checked in afterwards, so the confirmation has to say so rather than just asking "are you sure".
   */
  const handleActivate = async () => {
    if (
      !(await confirm({
        title: 'Reactivate this checklist?',
        message:
          'This checklist is checked out to a field device. Reactivating it releases that checkout, '
          + 'and any unsynced work still on that device can no longer be checked in.',
        confirmButtonText: 'Reactivate',
      }))
    ) {
      return;
    }
    setBusy(true);
    try {
      await API.protocolChecklist.activateCheckout(id);
      display({ kind: 'success', title: 'Checklist reactivated', timeout: 5000 });
      // Re-read rather than trusting the response: the status flip is the whole point, and a fresh
      // read is what clears the read-only banner.
      setReloadKey((k) => k + 1);
    } catch (err) {
      display({
        kind: 'error',
        title: 'Reactivate failed',
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
  /**
   * Checked out to a field device (RDO). The online copy is read-only until that device checks it
   * back in — otherwise two people edit the same checklist and one set of changes is lost at sync.
   *
   * Until now the SLR page handled only SUB, so a checked-out checklist rendered fully editable and
   * every save 403'd (once BE-1 added the status guard) or, before that, silently raced the device.
   */
  const checkedOut = checklist?.statusCode === 'RDO';
  const editable = canEdit && !isLegacySlb && !submitted && !checkedOut;

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
          <h1>
            {protocolType ? `${id}-${PROTOCOL_TYPE_LABEL[protocolType]}` : 'Protocol checklist'}
          </h1>
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
          {(submitted || isLegacySlb || checkedOut) && (
            <Column sm={4} md={8} lg={16}>
              <InlineNotification
                kind="info"
                title="Read only"
                subtitle={readOnlyReason({ isLegacySlb, checkedOut })}
                hideCloseButton
                lowContrast
              />
              {checkedOut && canPerformSysAdminActions && (
                <div className="protocol-checklist__actions">
                  <Button kind="tertiary" onClick={() => void handleActivate()} disabled={busy}>
                    Reactivate
                  </Button>
                </div>
              )}
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

          {/* Offline copy held on this device. Shown above the other actions because while a copy
              exists it is the authoritative one — everything else on this page is its local state. */}
          {offlineRecord && (
            <Column sm={4} md={8} lg={16}>
              <InlineNotification
                kind="info"
                title="Saved on this device"
                subtitle={
                  rejectedFiles.length > 0
                    ? 'Some files were refused by the server. Review them below, then check in again.'
                    : 'You can edit this checklist without a connection. Check it in when you are back online.'
                }
                hideCloseButton
                lowContrast
              />
              <div className="protocol-checklist__actions">
                <Button onClick={() => void handleCheckIn()} disabled={!!offlineBusy || !online}>
                  {offlineBusy ?? 'Check in'}
                </Button>
              </div>
              {rejectedFiles.length > 0 && (
                <ul className="protocol-checklist__rejected">
                  {rejectedFiles.map((op) => (
                    <li key={op.id}>
                      <strong>{op.fileName ?? 'File'}</strong>
                      {` — ${op.rejectedReason ?? 'refused'} `}
                      <Button
                        kind="ghost"
                        size="sm"
                        onClick={() => void handleDiscardRejected(op)}
                        disabled={!!offlineBusy}
                      >
                        Discard
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </Column>
          )}

          {/* `editable` now excludes submitted, so Unsubmit needs its own gate: a submitted checklist
              is read-only but must still be reversible. Neither action is offered while the
              checklist is checked out — the device holding it has to check in first. */}
          {canEdit && !isLegacySlb && !checkedOut && !offlineRecord && (
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
                {/* Take offline is offered only for an editable, active checklist: a submitted or
                    already checked-out one has nothing to take, and the server would refuse. */}
                {!submitted && online && (
                  <Button
                    kind="tertiary"
                    onClick={() => void handleTakeOffline()}
                    disabled={!!offlineBusy || busy}
                  >
                    {offlineBusy ?? 'Take offline'}
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
                      <BioOpeningView checklistId={id} canEdit={editable} submitted={submitted} />
                    ) : section.id === 'stratum' ? (
                      <BioStratumView checklistId={id} canEdit={editable} submitted={submitted} />
                    ) : section.id === 'plots' ? (
                      <BioPlotsView
                        checklistId={id}
                        canEdit={editable}
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
