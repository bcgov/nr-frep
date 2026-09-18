import { ArrowLeft, WarningFilled } from '@carbon/icons-react';
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

import { ExternalLink } from '@/components/core/ExternalLink';

import BioOpeningView from './BioOpeningView';
import BioPlotsView from './BioPlotsView';
import BioStratumView from './BioStratumView';
// Notes / Attachments are shared (named Rip* for legacy reasons) and used by Biodiversity. Riparian
// + Water are out of scope, so their dedicated editors are removed.
import OutstandingSummary from './OutstandingSummary';
import RipAttachmentsView from './RipAttachmentsView';
import RipNotesView from './RipNotesView';
import { formatSubmitValidation } from './submitValidation';
import { groupOutstanding } from './tabStatus';
import TabStatusIcon from './TabStatusIcon';
import { useTabStatuses } from './useTabStatuses';

import type { OutstandingGroup } from './tabStatus';
import type { BioAttachmentOp, OfflineBioChecklist } from '@/services/offline/bioDb';
import type { ProtocolChecklist, ProtocolType } from '@/types/protocolChecklist';

import { useAuth } from '@/context/auth/useAuth';
import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import API from '@/services/APIs';
import { CheckInBlockedError, checkInBioChecklist } from '@/services/offline/bioCheckIn';
import { bioOfflineRepo } from '@/services/offline/bioOfflineRepo';
import { TakeOfflineCancelled, takeBioChecklistOffline } from '@/services/offline/bioTakeOffline';
import { PROTOCOL_TYPE_LABEL, PROTOCOL_TYPE_TO_BACKEND } from '@/types/protocolChecklist';
import { apiErrorMessage } from '@/utils/apiError';
import { statusLabel, statusTagType } from '@/utils/checklistStatus';
import { formatShortDate } from '@/utils/date';
import { READ_ONLY_CHECKED_OUT, READ_ONLY_SUBMITTED } from '@/utils/readOnlyReason';
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
  if (checkedOut) return READ_ONLY_CHECKED_OUT;
  return READ_ONLY_SUBMITTED;
};

const ProtocolChecklistPage: FC = () => {
  // Dedicated biodiversity route (/protocol-checklists/slr/:id) — the family is the route, so there is
  // no type param. The record's actual code (SLB legacy / SLR going forward) comes from the GET, not
  // the URL. The API contract still uses the 'bio' segment (unchanged here).
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit, canPerformSysAdminActions } = useAuthorization();
  const { user } = useAuth();
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

  // Per-tab completion dots. Held here rather than in each view so the whole strip is derived from
  // one read, and so a save on any tab can move another tab's dot (stratum plot counts vs Plots).
  const {
    statuses: tabStatuses,
    counts: tabCounts,
    items: tabItems,
    refresh: refreshTabStatuses,
    evaluate: evaluateTabs,
  } = useTabStatuses(id, !!checklist);

  // Outstanding work found by the submit pre-flight, keyed by section id. Set when Submit is pressed
  // and the checklist is not ready; cleared on the next attempt.
  const [preflight, setPreflight] = useState<Record<string, string[]>>({});

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
          <ExternalLink href={href}>{value}</ExternalLink>
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
      // Pre-flight every tab against current data before troubling the proc. The tab counts are
      // derived from the last read, which a save on another tab can leave behind; this re-reads so
      // the answer is current. The proc stays authoritative — a clean pre-flight still submits and
      // can still be refused; this only stops us asking when the answer is already known.
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
        display({ kind: 'warning', title: 'Submit blocked by validation', timeout: 6000 });
        return;
      }

      // Submit requires the server status to be ACT, but a copy held on this device leaves it RDO.
      // So check in first (attachments, then the graph — which flips RDO → ACT and clears the local
      // record), then submit the now-active checklist. This is CHR's order and CHR's reasoning: the
      // pre-flight above runs against the local copy, so a checklist that will fail validation is
      // turned away *before* the irreversible check-in; and once checked in the local copy cannot
      // upload a second time, so a submit the proc then refuses leaves us cleanly on the online ACT
      // checklist, where the evaluator can fix it and resubmit.
      if (offlineRecord) {
        setOfflineBusy('Syncing changes…');
        try {
          await runCheckIn();
        } finally {
          // Runs on the failure path too: check-in removes the local record only on success, so the
          // page must re-read either way to show what actually survived.
          await refreshOfflineState();
          setOfflineBusy(null);
        }
      }

      await API.protocolChecklist.submit(backendCode, id);
      display({ kind: 'success', title: 'Checklist submitted', timeout: 5000 });
      setReloadKey((k) => k + 1);
    } catch (err) {
      // A blocked check-in is not a failed submit — the submit was never attempted and the local
      // copy is still on the device. Saying "Submit failed" would send the evaluator looking for a
      // validation problem that isn't there.
      if (err instanceof CheckInBlockedError) {
        display({
          kind: 'error',
          title: 'Sync stopped — nothing was submitted',
          subtitle: err.message,
          timeout: 9000,
        });
        return;
      }
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

  // A submit has been turned away for these items. Everything that reports them — the page banner,
  // the tab counts, each tab's disclosure — turns red together, so the page reads as one answer to
  // "why didn't it submit?" rather than three separate remarks.
  const submitRefused = Object.keys(preflight).length > 0;
  const tone = submitRefused ? 'error' : 'neutral';

  // `empty` only until the read lands — see TabStatusIcon, which draws nothing for it, just as it
  // draws nothing for the `none` a rule-less tab reports. Which tabs those are is decided next to
  // the rules themselves rather than listed here, so a tab that gains a rule lights up on its own.
  const statusFor = (sectionId: string) => tabStatuses[sectionId] ?? 'empty';

  /** What the tab's own panel lists, grouped by the record each item belongs to. */
  const outstandingGroups = (sectionId: string): OutstandingGroup[] =>
    groupOutstanding(tabItems[sectionId] ?? []);

  // The page-level tally. Counted across every tab, including any without an indicator: a total that
  // silently omitted a tab would read as "nothing left" while submit still refused.
  const outstandingTotal = Object.values(tabCounts).reduce((sum, count) => sum + count, 0);
  const outstandingTabs = Object.values(tabCounts).filter((count) => count > 0).length;

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
        // The header's "Opening ID" (the RESULTS key, e.g. 86496) — NOT `checklist.openingNumber`,
        // which is the map-sheet label ("93A 023 0.0 111"). The page renders both, a row apart; the
        // offline list's column is the id, matching CHR. See TakeOfflineOptions.openingId.
        openingId: headerExtras['Opening ID'],
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
              `This checklist's files need about ${formatMb(needBytes)}, and only ` +
              `${formatMb(availableBytes)} is free. Continuing may fail part-way. Continue anyway?`,
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

  /**
   * Upload the local copy: attachments first, then the graph.
   *
   * Extracted from the Check in button because Submit runs it too — an offline copy has to be
   * checked in before it can be submitted. Reports progress through `offlineBusy` and leaves the
   * caller to refresh state and say what happened, since the two callers report it differently.
   */
  const runCheckIn = async () => {
    await checkInBioChecklist(id, {
      onProgress: (progress) => {
        if (progress.phase === 'attachments' && progress.total) {
          setOfflineBusy(`Uploading files (${progress.done ?? 0} of ${progress.total})…`);
        } else if (progress.phase === 'graph') {
          setOfflineBusy('Saving to the server…');
        }
      },
    });
  };

  /** Check the local copy back in: attachments first, then the graph. */
  const handleCheckIn = async () => {
    setOfflineBusy('Syncing changes…');
    try {
      await runCheckIn();
      await refreshOfflineState();
      display({
        kind: 'success',
        title: 'Checklist uploaded',
        subtitle: 'Your changes are checked in and the offline copy has been removed.',
        timeout: 5000,
      });
      setReloadKey((k) => k + 1);
    } catch (err) {
      await refreshOfflineState();
      display({
        kind: 'error',
        title: 'Sync stopped',
        subtitle: err instanceof CheckInBlockedError ? err.message : apiErrorMessage(err),
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
          `"${op.fileName ?? 'This file'}" was refused by the server and has never been uploaded. ` +
          'Discarding it deletes it from this device permanently.',
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
          'This checklist is checked out to a field device. Reactivating it releases that checkout, ' +
          'and any unsynced work still on that device can no longer be checked in.',
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

  /**
   * This device holds the copy — which makes it the authoritative one, and makes this page its
   * editor.
   *
   * Both gates below branch on it, because a held copy fails the online checks for two independent
   * reasons and neither means "read-only":
   *
   * 1. `checkedOut` is always true. `getChecklist` is not facaded, so the page reads the server's
   *    `RDO` — the state take-offline *put it in*. For everyone else that is a lock; for the holder
   *    it is the normal state of holding a copy.
   * 2. `canEdit` is false once the session lapses. It comes from `user.roles`, and offline there is
   *    no session to refresh, so the roles are empty. The role check is not available exactly when
   *    the copy is most needed.
   *
   * Together these made a held copy read-only: no Edit on any tab, online or off, which is the whole
   * feature. Authorisation is not lost by trusting the holder — the checkout token is evidence this
   * user was permitted to take it, and the backend re-checks permission when the copy is synced.
   * CHR draws exactly this line: `readOnly = isOfflineCopy ? status === SUBMITTED : …`.
   */
  const holdsOfflineCopy = !!offlineRecord;

  const editable = holdsOfflineCopy
    ? !isLegacySlb && !submitted
    : canEdit && !isLegacySlb && !submitted && !checkedOut;

  /**
   * Whether the checklist-level actions (Submit / Unsubmit / Take offline / Check in) can be offered
   * at all.
   *
   * Deliberately not `editable`, which excludes submitted — Unsubmit is precisely the submitted case,
   * so gating on `editable` made it unreachable. A checklist checked out to another device offers
   * nothing here: that device has to check in first, and a sys admin can still reactivate a stranded
   * checkout from the read-only banner below.
   */
  /**
   * `!checkedOut || !!offlineRecord` — checked out to SOMEONE ELSE hides the actions; checked out to
   * THIS device does not.
   *
   * `getChecklist` is not facaded, so once take-offline claims the checkout the page sees the
   * server's `RDO`. A bare `!checkedOut` therefore withdrew every action from the holder the moment
   * the checkout landed: after a reload there was no Check in button, and the copy could not be
   * synced or released from the page at all. CHR draws the line the same way — an offline copy is
   * editable, someone else's checkout is not.
   */
  const actionsReady =
    !loading &&
    !notFound &&
    !hasError &&
    !!checklist &&
    !isLegacySlb &&
    (holdsOfflineCopy || (canEdit && !checkedOut));

  /**
   * Reactivate: admin recovery for a checklist stranded on **someone else's** device.
   *
   * `!offlineRecord` is the important half — it is never offered on a copy this device holds, where
   * the right action is Check in. Without that a sys admin holding their own copy was shown a button
   * whose whole purpose is to discard unsynced work, next to the one that saves it. CHR gates it the
   * same way (`!isOfflineCopy && online && canPerformSysAdminActions && status === READ_ONLY_OFFLINE`).
   */
  const canReactivate =
    !loading &&
    !notFound &&
    !hasError &&
    !!checklist &&
    checkedOut &&
    !holdsOfflineCopy &&
    online &&
    canPerformSysAdminActions;

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
            {/* Status reads beside the heading rather than as a cell of the tombstone grid: it
                governs what the whole page allows, so it belongs where the eye lands first. */}
            {checklist && (
              <Tag type={statusTagType(offlineRecord ? 'RDO' : checklist.statusCode)} size="sm">
                {/* Two corrections to the raw code, both for CHR parity:
                    1. An offline copy is served from the local snapshot, taken while the checklist
                       was still ACT — so `checklist.statusCode` reads "Active" even though the
                       server now holds it RDO. Label the server's truth.
                    2. RDO reads "Checked out", not the shared map's "Read-only". That map serves the
                       search and accepted-sites tables, where the generic word is right; on a
                       checklist page the specific one says *why* the page is read-only, which is
                       what CHR's own map does (`READ_ONLY_OFFLINE: 'Checked out'`). */}
                {offlineRecord || checklist.statusCode === 'RDO'
                  ? 'Checked out'
                  : statusLabel(checklist.statusCode, checklist.statusLabel)}
              </Tag>
            )}
            {/* An offline copy is your editable local copy (always RDO on the server), so it is
                flagged separately from the status above — CHR parity. */}
            {offlineRecord && (
              <Tag type="teal" size="sm">
                Offline copy
              </Tag>
            )}
            {!online && (
              <Tag type="red" size="sm">
                No network connection
              </Tag>
            )}
            {/* `actionsReady` rather than main's inline condition: it is that same gate plus
                `!checkedOut`. Both already include canEdit && !isLegacySlb, so no SLB or
                permission check is lost by using it. */}
            {actionsReady && (
              <div className="protocol-checklist__actions">
                {/* While a local copy exists it is the authoritative one, so Take offline is not
                    offered alongside it — a second download over the top of unsynced field work.
                    Submit is, and checks in on the way through (see handleSubmit): CHR offers both
                    on an offline copy, and withholding Submit here meant an evaluator who had
                    finished in the field had to check in and then find the checklist again. */}
                {offlineRecord ? (
                  <>
                    {/* Submit needs the server — and so does the check-in it runs first — so it is
                        offered only while online, as CHR gates its offline-copy Submit. */}
                    {online && (
                      <Button onClick={() => void handleSubmit()} disabled={busy || !!offlineBusy}>
                        Submit checklist
                      </Button>
                    )}
                    {/* "Sync changes", matching CHR — the same operation on both pages: upload,
                        release the checkout (RDO → ACT) and delete the local copy. The handler keeps
                        the check-in name internally because that is what the server call is. */}
                    <Button
                      kind="tertiary"
                      onClick={() => void handleCheckIn()}
                      disabled={!!offlineBusy || !online || busy}
                    >
                      {offlineBusy ?? 'Sync changes'}
                    </Button>
                  </>
                ) : (
                  <>
                    {submitted ? (
                      <Button kind="tertiary" onClick={() => void handleUnsubmit()} disabled={busy}>
                        Unsubmit checklist
                      </Button>
                    ) : (
                      <Button onClick={() => void handleSubmit()} disabled={busy}>
                        Submit checklist
                      </Button>
                    )}
                    {/* Take offline is offered only for an editable, active checklist: a submitted
                        one has nothing to take and the server would refuse the checkout. */}
                    {!submitted && online && (
                      <Button
                        kind="tertiary"
                        onClick={() => void handleTakeOffline()}
                        disabled={!!offlineBusy || busy}
                      >
                        {offlineBusy ?? 'Take offline'}
                      </Button>
                    )}
                  </>
                )}
              </div>
            )}
            {canReactivate && (
              <div className="protocol-checklist__actions">
                <Button kind="tertiary" onClick={() => void handleActivate()} disabled={busy}>
                  Reactivate
                </Button>
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
          {/* A copy held on this device. Shown above everything else because while it exists it is
              the authoritative one — the rest of this page is its local state. */}
          {/* No "Saved on this device" banner: the teal "Offline copy" chip beside the heading already
              says it, and CHR carries no such banner. Only the rejected-files case gets one, because
              it needs a decision rather than an announcement. */}
          {offlineRecord && rejectedFiles.length > 0 && (
            <Column sm={4} md={8} lg={16}>
              {/* Not an InlineNotification.
                  Carbon's alert components REFUSE interactive children — mounting a button inside
                  one logs "component should have no interactive child nodes" and the click does
                  nothing. Each refused file needs its own Discard, so the banner is built here in
                  Carbon's warning language instead of fighting a component that is documented not to
                  take it. One block: heading, sentence, then the files as bullets.

                  Named per file rather than "3 files failed": the user has to decide about each one,
                  and the bytes may be field evidence that cannot be re-collected. */}
              <section
                className="protocol-checklist__refused"
                aria-labelledby="refused-files-title"
              >
                <WarningFilled size={20} className="protocol-checklist__refused-icon" />
                <div>
                  <p className="protocol-checklist__refused-lead">
                    <strong id="refused-files-title">Some files were refused</strong> The server
                    refused these files. Review them, then sync again.
                  </p>
                  <ul className="protocol-checklist__rejected">
                    {rejectedFiles.map((op) => (
                      <li key={op.id} className="protocol-checklist__rejected-row">
                        <strong>{op.fileName ?? 'File'}</strong>
                        <span className="protocol-checklist__rejected-reason">
                          {op.rejectedReason ?? 'refused'}
                        </span>
                        <Button
                          kind="danger--tertiary"
                          size="sm"
                          onClick={() => void handleDiscardRejected(op)}
                          disabled={!!offlineBusy}
                        >
                          Discard
                        </Button>
                      </li>
                    ))}
                  </ul>
                </div>
              </section>
            </Column>
          )}

          {/* `!offlineRecord` throughout: when the copy is THIS device's, RDO is not a read-only
              state — it is the normal state of holding one, and the page is fully editable. Without
              this the holder was told their own checklist was read-only, and a sys-admin holder was
              offered Reactivate on their own work. CHR draws the same line with `!isOfflineCopy`. */}
          {!offlineRecord && (submitted || isLegacySlb || checkedOut) && (
            <Column sm={4} md={8} lg={16}>
              <InlineNotification
                kind="info"
                title="Read only"
                subtitle={readOnlyReason({ isLegacySlb, checkedOut })}
                hideCloseButton
                lowContrast
              />
            </Column>
          )}

          <Column sm={4} md={8} lg={16}>
            <OutstandingSummary
              total={outstandingTotal}
              tabs={outstandingTabs}
              refused={submitRefused}
            />
          </Column>

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
                {headerCell('Evaluator', checklist.evaluatorName, true)}
                {headerCell('Evaluation date', formatShortDate(checklist.evaluationDate), true)}
                {headerCell('Sample #', headerExtras['Sample #'])}
              </div>
            </Tile>
          </Column>

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
                      {section.title}
                      <TabStatusIcon
                        status={statusFor(section.id)}
                        count={tabCounts[section.id]}
                        section={section.title}
                        tone={tone}
                      />
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
                        tone={tone}
                      />
                    ) : section.id === 'stratum' ? (
                      <BioStratumView
                        checklistId={id}
                        canEdit={editable}
                        submitted={submitted}
                        onSaved={refreshTabStatuses}
                        outstanding={outstandingGroups('stratum')}
                        tone={tone}
                      />
                    ) : section.id === 'plots' ? (
                      <BioPlotsView
                        checklistId={id}
                        canEdit={editable}
                        submitted={submitted}
                        active={i === tabIndex}
                        onSaved={refreshTabStatuses}
                        outstanding={outstandingGroups('plots')}
                        tone={tone}
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
