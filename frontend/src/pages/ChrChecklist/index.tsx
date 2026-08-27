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
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { ExternalLink } from '@/components/core/ExternalLink';
import BlockSummary from '@/pages/ChrChecklist/BlockSummary';
import Contacts from '@/pages/ChrChecklist/Contacts';
import FeatureList from '@/pages/ChrChecklist/FeatureList';
import Notes from '@/pages/ChrChecklist/Notes';
import OpeningInformation from '@/pages/ChrChecklist/OpeningInformation';
import Photos from '@/pages/ChrChecklist/Photos';
import TabIncompleteBanner from '@/pages/ProtocolChecklist/TabIncompleteBanner';
import TabStatusIcon from '@/pages/ProtocolChecklist/TabStatusIcon';

import { useAuth } from '@/context/auth/useAuth';
import { useConfirm } from '@/context/confirm/useConfirm';
import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { calculateMrvaRatingCode } from '@/pages/ChrChecklist/codeLists';
import { chrTabStatuses, type ChrTabKey } from '@/pages/ChrChecklist/tabStatus';
import API from '@/services/APIs';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';
import {
  classifyStaleness,
  isStale,
  stalenessBanner,
  type StalenessVerdict,
} from '@/services/offline/chrStaleness';
import {
  CHR_STATUS,
  type CheckList,
  type Contact as ContactDto,
  type Feature,
  type Picture,
  type ValidationError,
} from '@/types/chrChecklist';
import { apiErrorMessage } from '@/utils/apiError';
import { statusTagType } from '@/utils/checklistStatus';
import { formatShortDate } from '@/utils/date';
import { pictureToFile } from '@/utils/pictureFile';
import { silvaOpeningUrl } from '@/utils/silva';

// Reuse the Biodiversity checklist form primitives (rip-form / rip-form__group / rip-form__grid /
// protocol-checklist__field) so CHR tab content is structured the same way.
import '@/pages/ProtocolChecklist/protocolChecklist.scss';
import './chrChecklist.scss';

const STATUS_LABELS: Record<string, string> = {
  [CHR_STATUS.ACTIVE]: 'Active',
  [CHR_STATUS.SUBMITTED]: 'Submitted',
  [CHR_STATUS.READ_ONLY_OFFLINE]: 'Checked out',
};

// Stable empty-array reference so the Contacts draft-sync effect doesn't re-fire each render.
const EMPTY_CONTACTS: ContactDto[] = [];

// The tab strip, in order. `key` is what tabStatus.ts reports against; `title` is what the tab, the
// submit pre-flight banner and the per-tab banner all call the section.
const TABS: { key: ChrTabKey; title: string }[] = [
  { key: 'opening', title: 'Opening info' },
  { key: 'blockSummary', title: 'Block summary' },
  { key: 'contacts', title: 'Contacts' },
  { key: 'features', title: 'Features' },
  { key: 'notes', title: 'Notes' },
  { key: 'attachments', title: 'Attachments' },
];

// Map a submit-validation error's field to the CHR tab that owns it, so the inline panel names the
// tab (like the Biodiversity submit panel) rather than the raw field. Checklist-level fields are
// split between Opening info and Block summary; every other field is feature-level.
const OPENING_FIELDS = new Set([
  'evaluationDate',
  'yearOfHarvest',
  'generalLocation',
  'assessedBy',
  'firstNationName',
  'targeted',
]);
const BLOCK_SUMMARY_FIELDS = new Set(['rating', 'q8Comments', 'q9Comments', 'q10Comments']);
const tabForField = (field?: string): string => {
  if (!field || field === 'checklist') return 'Checklist';
  if (OPENING_FIELDS.has(field)) return 'Opening info';
  if (BLOCK_SUMMARY_FIELDS.has(field)) return 'Block summary';
  return 'Features';
};

// The feature number from an error's entityLabel (`"<checklistId>-<featureLabel>"`); '' for a
// checklist-level error (no trailing label) so the title falls back to the plain tab name.
const featureLabelFromEntity = (entityLabel?: string): string => {
  if (!entityLabel) return '';
  const dash = entityLabel.lastIndexOf('-');
  return dash === -1 ? '' : entityLabel.slice(dash + 1);
};

// Notification title for a submit-validation error: the owning tab, plus the feature number for
// feature-level errors so a stack of "Features" cards is distinguishable.
const errorTitle = (e: ValidationError): string => {
  const tab = tabForField(e.field);
  if (tab !== 'Features') return tab;
  const label = featureLabelFromEntity(e.entityLabel);
  return label ? `Features — Feature ${label}` : tab;
};

// Stable React key for a submit-validation error, built from its identifying fields (no array index).
const errorKey = (e: ValidationError): string =>
  [e.field, e.entityLabel, e.referenceId, e.message].filter(Boolean).join('|');

// The "Read only" banner copy, by why the checklist is locked.
const readOnlyReason = (status: CheckList['status']): string => {
  if (status === CHR_STATUS.READ_ONLY_OFFLINE) {
    return 'This checklist is checked out offline, so the online copy is read-only. Upload it from the device that holds it (which reactivates it), or have it reactivated, to edit online.';
  }
  if (status === CHR_STATUS.SUBMITTED) {
    return 'This checklist has been submitted and is read-only. Unsubmit it to make changes.';
  }
  return 'This checklist is not active, so it is read-only.';
};

/**
 * The local (device) shape: the whole document, photos included. Offline a captured photo's base64
 * is the ONLY copy of those bytes until check-in flushes it, so anything written to Dexie must carry
 * `pictures` through untouched.
 */
const prepareForLocalSave = (checkList: CheckList): CheckList => ({
  ...checkList,
  mrvaRatingCode: calculateMrvaRatingCode(checkList.rating, checkList.features),
});

/**
 * The wire shape for a server save. Photos are separate resources since the multipart split — a save
 * ignores `pictures` entirely — so they are stripped rather than shipped back as base64.
 *
 * <p>Never use this for a local save. Doing so wiped every offline-captured photo before it could be
 * flushed; see {@link prepareForLocalSave}.
 */
const prepareForServerSave = (checkList: CheckList): CheckList => ({
  ...prepareForLocalSave(checkList),
  pictures: [],
});

const extractValidationErrors = (err: unknown): ValidationError[] | null => {
  const body = (err as { body?: unknown })?.body;
  return Array.isArray(body) ? (body as ValidationError[]) : null;
};

const ChrChecklistPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const { display } = useNotification();
  const { canPerformSysAdminActions, canChr } = useAuthorization();
  const { user } = useAuth();
  const online = useOnlineStatus();

  const [checkList, setCheckList] = useState<CheckList | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [isOfflineCopy, setIsOfflineCopy] = useState(false);
  // Photos are no longer embedded in the checklist GET. Online, the tab reads its own page; offline,
  // the page is sliced from the locally stored copy, which still carries each photo's base64.
  const [photoPage, setPhotoPage] = useState(0);
  const [photoPageSize, setPhotoPageSize] = useState(10);
  const [photoTotal, setPhotoTotal] = useState(0);
  // Server-vs-local reconcile for an offline copy: verdict + the server's last-updater audit.
  const [offlineStaleness, setOfflineStaleness] = useState<{
    verdict: StalenessVerdict;
    updateUserid?: string;
    updateTimestamp?: string;
  } | null>(null);
  const [errors, setErrors] = useState<ValidationError[]>([]);
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState(0);
  // Outstanding work found by the submit pre-flight, keyed by tab. Set when Submit is pressed and the
  // checklist is not ready; cleared on the next attempt.
  const [preflight, setPreflight] = useState<Partial<Record<ChrTabKey, string[]>>>({});
  // Once Submit has been pressed, every tab shows its count — including the ones held back for never
  // having been started. The user has asked the question, so the answer stops being a nag.
  const [countsRevealed, setCountsRevealed] = useState(false);
  // Tabs that have had a save land in this session, so their banner can lead with "saved" rather than
  // reporting gaps in a record the user has not touched yet.
  const [savedTabs, setSavedTabs] = useState<Partial<Record<ChrTabKey, boolean>>>({});

  /**
   * Per-tab completion state, derived from the checklist the page is already holding.
   *
   * No requests: CHR keeps the whole document — features included — in client state, so every submit
   * rule can be answered from memory. The dots therefore move the moment a save lands and keep
   * working on an offline copy.
   */
  const tabState = useMemo(() => chrTabStatuses(checkList), [checkList]);

  /** Record that a section save landed, for that tab's banner wording. */
  const markSaved = useCallback(
    (key: ChrTabKey) => setSavedTabs((prev) => ({ ...prev, [key]: true })),
    [],
  );

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setHasError(false);

    const loadFromApi = () =>
      API.chrChecklist.getChecklist(id).then((data) => {
        if (cancelled) return;
        setCheckList(data);
        setIsOfflineCopy(false);
      });

    chrOfflineRepo
      .load(id)
      .then((record) => {
        if (cancelled) return undefined;
        if (record) {
          setCheckList(record.checkList);
          setIsOfflineCopy(true);
          return undefined;
        }
        return loadFromApi();
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const status = (err as { status?: number })?.status;
        if (status === 404) {
          setNotFound(true);
          return;
        }
        display({
          kind: 'error',
          title: "We couldn't load the CHR checklist",
          subtitle: apiErrorMessage(err),
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
  }, [id, display]);

  // When viewing an offline copy, reconcile it against the server so we can warn if it's been
  // superseded (reactivated/submitted/removed elsewhere) — see chrStaleness. Server copies aren't
  // stale; their last-updated info is read straight off the checklist below.
  useEffect(() => {
    if (!isOfflineCopy) {
      setOfflineStaleness(null);
      return undefined;
    }
    if (!online) {
      setOfflineStaleness({ verdict: 'UNVERIFIED' });
      return undefined;
    }
    let cancelled = false;
    void (async () => {
      try {
        const [record, server] = await Promise.all([
          chrOfflineRepo.load(id),
          API.chrChecklist.getChecklist(id),
        ]);
        if (cancelled) return;
        setOfflineStaleness({
          verdict: classifyStaleness(record?.deviceCheckoutGuid, server),
          updateUserid: server.updateUserid,
          updateTimestamp: server.updateTimestamp,
        });
      } catch (err) {
        if (cancelled) return;
        const status = (err as { status?: number })?.status;
        setOfflineStaleness({ verdict: status === 404 ? 'GONE' : 'UNVERIFIED' });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id, isOfflineCopy, online]);

  // Editability mirrors the backend gate: an online (server) copy can only be saved when its status
  // is ACT — the legacy save rejects any other status with "Checklist status does not allow this
  // operation." An offline copy is the user's own checked-out (RDO) copy, editable locally until
  // submitted. Without this, a checklist checked out offline (RDO) would appear editable here but
  // every save would fail server-side.
  const statusLocked =
    !isOfflineCopy && checkList != null && checkList.status !== CHR_STATUS.ACTIVE;
  // An offline copy is the user's own checked-out (RDO) copy, edited on-device until submitted —
  // editability depends only on its status, not the online role check (which requires a session
  // that doesn't exist offline; the backend re-checks permission on upload). Online (server) copies
  // keep the role + status gating.
  // CHR editing is district-scoped: sys-admin or the FREP_CHR_EDITOR_DISTRICT_<code> role matching
  // this checklist's org unit. (Replaces the old global canEdit, which excluded district editors.)
  const canEditThisChr = canChr(checkList?.orgUnitCode);
  const readOnly = isOfflineCopy
    ? checkList?.status === CHR_STATUS.SUBMITTED
    : !canEditThisChr || statusLocked;

  // A stale offline copy can't be uploaded (the server checkout was reset/submitted/removed), so both
  // Submit and Sync changes — which upload — are dead ends and are hidden; the banner explains why and
  // only "Remove from device" is offered.
  const offlineOutOfDate =
    isOfflineCopy && offlineStaleness != null && isStale(offlineStaleness.verdict);

  /**
   * Features being edited but not yet saved.
   *
   * The Features tab edits the whole list at once — a composite's membership and the cross-feature
   * associations are both edited from inside one feature — so it needs the live array, and it used to
   * write that straight back into `checkList`. That made a feature added seconds ago look stored, and
   * greeted the user with a banner listing everything they had not typed yet. Holding the pending
   * list here keeps `checkList` meaning "what the server (or the local record) actually holds", which
   * is what the tab dots and banners describe. It also puts Features on the same footing as Opening
   * info and Block summary, whose drafts have always been local to the tab until Save.
   */
  const [draftFeatures, setDraftFeatures] = useState<Feature[] | null>(null);

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        // The backend's real reason lives in the response body's `message` (e.g. a validation error);
        // apiErrorMessage prefers that over the bare status phrase ("Bad Request").
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      }),
    [display],
  );

  // Per-section save (mirrors the Biodiversity per-tab save). `merged` is the full checklist sent
  // to the section endpoint; the backend persists only that section. `applyBack` merges the
  // server's response into state online — pulling back the new revision count and any
  // server-assigned ids for this section while preserving other tabs' in-memory edits. Offline
  // saves the whole document locally. Returns true on success so per-tab Save can exit edit mode.
  const persistSection = useCallback(
    async (
      section: ChrTabKey,
      endpoint: (checklistId: string, cl: CheckList) => Promise<CheckList>,
      merged: CheckList,
      applyBack: (prev: CheckList, saved: CheckList) => CheckList,
    ): Promise<boolean> => {
      setBusy(true);
      try {
        if (isOfflineCopy) {
          await chrOfflineRepo.saveLocal(prepareForLocalSave(merged));
          setCheckList(merged);
          display({ kind: 'success', title: 'Saved offline', timeout: 4000 });
        } else {
          const saved = await endpoint(id, prepareForServerSave(merged));
          setCheckList((prev) => (prev ? applyBack(prev, saved) : prev));
          display({ kind: 'success', title: 'Checklist saved', timeout: 4000 });
        }
        markSaved(section);
        return true;
      } catch (err) {
        reportError('Save failed', err);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [id, isOfflineCopy, display, reportError, markSaved],
  );

  // Opening / Block summary are buffered (Edit → Save): they pass their committed draft, which we
  // merge into the checklist and carry forward verbatim (plus the server's fresh revision count).
  const saveOpening = useCallback(
    (draft: Partial<CheckList>): Promise<boolean> => {
      if (!checkList) return Promise.resolve(false);
      return persistSection(
        'opening',
        (cid, cl) => API.chrChecklist.saveOpening(cid, cl),
        { ...checkList, ...draft },
        // assessedBy is decided server-side (set-once / assign-to-me), so reflect its truth.
        (prev, saved) => ({
          ...prev,
          ...draft,
          assessedBy: saved.assessedBy,
          revisionCount: saved.revisionCount,
        }),
      );
    },
    [checkList, persistSection],
  );

  const saveBlockSummary = useCallback(
    (draft: Partial<CheckList>): Promise<boolean> => {
      if (!checkList) return Promise.resolve(false);
      return persistSection(
        'blockSummary',
        (cid, cl) => API.chrChecklist.saveBlockSummary(cid, cl),
        { ...checkList, ...draft },
        (prev, saved) => ({
          ...prev,
          ...draft,
          mrvaRatingCode: saved.mrvaRatingCode,
          revisionCount: saved.revisionCount,
        }),
      );
    },
    [checkList, persistSection],
  );

  // Notes (block-level comment) is its own tab but persists to BLOCK_COMMENTS via the block-summary
  // save — so it reuses that endpoint, posting just the committed note.
  const saveNotes = useCallback(
    (draft: Partial<CheckList>): Promise<boolean> => {
      if (!checkList) return Promise.resolve(false);
      return persistSection(
        'notes',
        (cid, cl) => API.chrChecklist.saveBlockSummary(cid, cl),
        { ...checkList, ...draft },
        (prev, saved) => ({ ...prev, ...draft, revisionCount: saved.revisionCount }),
      );
    },
    [checkList, persistSection],
  );

  // Contacts / Features / Attachments edit the shared checklist in place (onPatch); their Save
  // posts the current checklist and pulls back the section's server truth (e.g. new row ids).
  // Contacts buffers its own draft (Edit → Save/Cancel parity); Save posts the committed contacts.
  const saveContacts = useCallback(
    (contacts: ContactDto[]): Promise<boolean> => {
      if (!checkList) return Promise.resolve(false);
      return persistSection(
        'contacts',
        (cid, cl) => API.chrChecklist.saveContacts(cid, cl),
        { ...checkList, contacts },
        (prev, saved) => ({
          ...prev,
          contacts: saved.contacts,
          revisionCount: saved.revisionCount,
        }),
      );
    },
    [checkList, persistSection],
  );

  const saveFeatures = useCallback(
    async (features: Feature[]): Promise<boolean> => {
      if (!checkList) return false;
      const saved = await persistSection(
        'features',
        (cid, cl) => API.chrChecklist.saveFeatures(cid, cl),
        { ...checkList, features },
        (prev, stored) => ({
          ...prev,
          features: stored.features,
          revisionCount: stored.revisionCount,
        }),
      );
      // The stored list is the truth again — drop the pending edits so the tab reads from it (and
      // picks up the ids the server assigned to any new rows).
      if (saved) setDraftFeatures(null);
      return saved;
    },
    [checkList, persistSection],
  );

  /**
   * Stable per-checklist reference. As an inline arrow this was a new function on every render, and
   * it is a dependency of the Photos tab's fetch effect — so every render re-ran that effect and
   * could re-request photos already in flight.
   */
  const fetchPhotoContent = useCallback(
    (photoId: string) => API.chrChecklist.getPhotoContent(id, photoId),
    [id],
  );

  /**
   * Load one page of photo metadata. Offline the local copy is the source of truth (it holds the
   * bytes); online this is a metadata-only read and each image is fetched individually for display.
   */
  const loadPhotos = useCallback(
    async (targetPage = photoPage, targetSize = photoPageSize) => {
      if (isOfflineCopy) {
        const all = checkList?.pictures ?? [];
        setPhotoTotal(all.length);
        setPhotoPage(targetPage);
        setPhotoPageSize(targetSize);
        return;
      }
      let landedPage = targetPage;
      let result = await API.chrChecklist.getPhotos(id, landedPage, targetSize);
      // Deleting the last row on the last page leaves the client asking for a page that no longer
      // exists: the response is empty while totalCount is still non-zero. Re-read the last page
      // that does exist, rather than showing an empty tab under a pager insisting there are items.
      // Bounded to one extra request — the recomputed page is always in range.
      if (result.photos.length === 0 && result.totalCount > 0 && landedPage > 0) {
        landedPage = Math.max(0, Math.ceil(result.totalCount / targetSize) - 1);
        result = await API.chrChecklist.getPhotos(id, landedPage, targetSize);
      }
      setPhotoTotal(result.totalCount);
      setPhotoPage(landedPage);
      setPhotoPageSize(targetSize);
      setCheckList((prev) => (prev ? { ...prev, pictures: result.photos } : prev));
    },
    [id, isOfflineCopy, checkList?.pictures, photoPage, photoPageSize],
  );

  /**
   * Add photos. Online each file is POSTed individually to the photo endpoint — sequentially, so a
   * batch never holds several files in server heap at once, and one failure doesn't lose the rest.
   * Offline there is nothing to POST: the photo is appended to the locally stored checklist with its
   * base64 intact, and the check-in flush uploads it later.
   */
  // The tab loads its own first page of photo metadata once the checklist is available. Offline
  // copies already hold everything locally.
  //
  // Depend on the BOOLEAN, not on `checkList` itself. The guard below needs a loaded checklist, but
  // `checkList` is null on mount while its own GET is in flight — so keying the effect on `id` alone
  // meant it ran once, bailed at the guard, and never ran again: getPhotos was never called at all,
  // leaving photoTotal at 0 while the table rendered the pictures the checklist GET returned.
  // Depending on `checkList` directly would loop, because this effect calls setCheckList. The
  // boolean flips false -> true exactly once and then stays true, which fires the fetch on arrival
  // without re-triggering on the update it performs itself.
  const hasChecklist = Boolean(checkList);
  useEffect(() => {
    if (!hasChecklist || isOfflineCopy) return;
    void API.chrChecklist
      .getPhotos(id, 0, photoPageSize)
      .then((result) => {
        setPhotoTotal(result.totalCount);
        setCheckList((prev) => (prev ? { ...prev, pictures: result.photos } : prev));
      })
      .catch((err: unknown) => {
        // A failed photo page must not block the rest of the checklist, but it must not be silent
        // either — swallowing it entirely is what made the missing call above invisible.
        reportError("We couldn't load the photos", err);
      });
    // photoPageSize is deliberately omitted: a page-size change is handled by loadPhotos, and
    // including it here would fire a second, competing fetch for the same page.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, isOfflineCopy, hasChecklist]);

  const addPhotos = useCallback(
    async (additions: Picture[]): Promise<boolean> => {
      if (!checkList) return false;
      setBusy(true);
      try {
        if (isOfflineCopy) {
          const merged = { ...checkList, pictures: [...(checkList.pictures ?? []), ...additions] };
          await chrOfflineRepo.saveLocal(merged);
          setCheckList(merged);
          display({ kind: 'success', title: 'Saved offline', timeout: 4000 });
          return true;
        }
        for (const picture of additions) {
          const file = pictureToFile(picture);
          if (!file) continue;
          await API.chrChecklist.addPhoto(
            id,
            file,
            picture.description ?? '',
            picture.date,
            undefined,
            picture.featureId,
          );
        }
        await loadPhotos();
        display({ kind: 'success', title: 'Photo saved', timeout: 4000 });
        return true;
      } catch (err) {
        reportError('Could not save the photo', err);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [checkList, id, isOfflineCopy, loadPhotos, display, reportError],
  );

  /**
   * Remove one photo. Offline the removal is recorded locally — including the server id, so the
   * check-in flush can issue the matching DELETE; delete-by-absence no longer exists server-side.
   */
  const deletePhoto = useCallback(
    async (picture: Picture): Promise<boolean> => {
      if (!checkList) return false;
      setBusy(true);
      try {
        if (isOfflineCopy) {
          const merged = {
            ...checkList,
            pictures: (checkList.pictures ?? []).filter((p) => p !== picture),
          };
          await chrOfflineRepo.saveLocal(merged, picture.id ? [picture.id] : []);
          setCheckList(merged);
          display({ kind: 'success', title: 'Removed offline', timeout: 4000 });
          return true;
        }
        if (picture.id) {
          await API.chrChecklist.deletePhoto(id, picture.id);
        }
        await loadPhotos();
        display({ kind: 'success', title: 'Photo removed', timeout: 4000 });
        return true;
      } catch (err) {
        reportError('Could not remove the photo', err);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [checkList, id, isOfflineCopy, loadPhotos, display, reportError],
  );

  const handleSubmit = async () => {
    if (!checkList) return;
    setBusy(true);
    setErrors([]);
    setPreflight({});
    try {
      // Pre-flight every tab against the checklist in hand before troubling the server. This is what
      // catches the checklist whose Block summary was never opened: those tabs are deliberately quiet
      // until they have been started, so without this the first news of the problem would be a
      // rejected submit. For an offline copy it also avoids checking the record back in — an
      // irreversible step — only for submit to turn it down. The server stays authoritative: a clean
      // pre-flight still submits and can still be refused; this only stops us asking when the answer
      // is already known.
      const blocking = Object.fromEntries(
        Object.entries(chrTabStatuses(checkList).outstanding).filter(
          ([, items]) => items.length > 0,
        ),
      ) as Partial<Record<ChrTabKey, string[]>>;
      if (Object.keys(blocking).length > 0) {
        setPreflight(blocking);
        setCountsRevealed(true);
        display({ kind: 'warning', title: 'Submit blocked by validation', timeout: 6000 });
        return;
      }

      // Submit requires the server status to be ACT, but an offline copy is RDO. So for an offline
      // copy, first check it back in (upload: RDO → ACT) and drop the local draft, then submit the
      // now-active checklist. Checking in before submit also avoids a stuck state: upload clears the
      // server deviceCheckoutGuid, so the local copy can't upload a second time — if submit then
      // fails validation, we're cleanly on the online ACT checklist and can fix + resubmit.
      let toSubmit = checkList;
      if (isOfflineCopy) {
        // Local save — keeps the photos, which upload() is about to flush.
        await chrOfflineRepo.saveLocal(prepareForLocalSave(checkList));
        toSubmit = await chrOfflineRepo.upload(id);
        await chrOfflineRepo.remove(id);
        setIsOfflineCopy(false);
        setCheckList(toSubmit);
      }
      const saved = await API.chrChecklist.submit(id, prepareForServerSave(toSubmit));
      // Photos are a separate paged resource, so a checklist response carries no `pictures`.
      // Replacing state wholesale would blank the Photos tab; a status change doesn't touch photos,
      // so carry the loaded page over. (A check-in flips isOfflineCopy, which re-runs the photo
      // effect — these online status changes don't, so they must preserve it here.)
      setCheckList((prev) => ({ ...saved, pictures: prev?.pictures ?? [] }));
      setCountsRevealed(false);
      display({ kind: 'success', title: 'Checklist submitted', timeout: 5000 });
    } catch (err) {
      const validation = extractValidationErrors(err);
      if (validation) {
        setErrors(validation);
        display({ kind: 'warning', title: 'Submit blocked by validation', timeout: 6000 });
      } else {
        reportError('Submit failed', err);
      }
    } finally {
      setBusy(false);
    }
  };

  const handleUnsubmit = async () => {
    setBusy(true);
    setErrors([]);
    try {
      const saved = await API.chrChecklist.unsubmit(id);
      // Carry the photo page over — see the note in handleSubmit.
      setCheckList((prev) => ({ ...saved, pictures: prev?.pictures ?? [] }));
      display({ kind: 'success', title: 'Checklist reopened for editing', timeout: 5000 });
    } catch (err) {
      reportError('Unsubmit failed', err);
    } finally {
      setBusy(false);
    }
  };

  const handleTakeOffline = async () => {
    setBusy(true);
    try {
      const record = await chrOfflineRepo.takeOffline(id);
      setCheckList(record.checkList);
      setIsOfflineCopy(true);
      display({ kind: 'success', title: 'Checklist taken offline', timeout: 4000 });
    } catch (err) {
      reportError('Could not take checklist offline', err);
    } finally {
      setBusy(false);
    }
  };

  // Admin-only recovery for a checklist stuck "Checked out" (RDO) whose offline copy is on another
  // device/browser: clears the checkout (RDO → ACT) so it can be edited online again. Mirrors the
  // legacy ACTIVATECHECKLIST action; the backend re-checks admin + that the status is RDO.
  const handleReactivate = async () => {
    setBusy(true);
    try {
      const saved = await API.chrChecklist.activate(id);
      // Carry the photo page over — see the note in handleSubmit.
      setCheckList((prev) => ({ ...saved, pictures: prev?.pictures ?? [] }));
      display({ kind: 'success', title: 'Checklist reactivated', timeout: 4000 });
    } catch (err) {
      reportError('Reactivate failed', err);
    } finally {
      setBusy(false);
    }
  };

  // Discard a stale offline copy (its server checkout is gone, so it can't be uploaded) and return to
  // the previous screen. No release call — the server has already moved on. Removing a *current*
  // offline copy still lives on the Offline checklists list, which releases the checkout.
  const handleRemoveOfflineCopy = async () => {
    if (
      !(await confirm({
        title: 'Remove from device?',
        message:
          'Remove this offline copy from this device? Any unsynced local changes will be lost.',
        confirmButtonText: 'Remove',
      }))
    ) {
      return;
    }
    setBusy(true);
    try {
      await chrOfflineRepo.remove(id);
      display({ kind: 'success', title: 'Offline copy removed', timeout: 4000 });
      navigate(-1);
    } catch (err) {
      reportError('Could not remove offline copy', err);
    } finally {
      setBusy(false);
    }
  };

  const handleUpload = async () => {
    if (!checkList) return;
    setBusy(true);
    try {
      // Local save — stripping photos here (as a server payload would) deleted every offline capture
      // moments before upload() flushed them, so the bytes were gone by the time it looked.
      await chrOfflineRepo.saveLocal(prepareForLocalSave(checkList));
      const saved = await chrOfflineRepo.upload(id);
      // Drop the local draft, exactly as the submit chain does. A check-in clears the server's
      // deviceCheckoutGuid, so a retained copy could never be uploaded again — it would sit there
      // looking editable, and (since the save response carries no photos) looking like it had lost
      // them. Only on success: a failed upload must leave the copy untouched so it can be retried.
      await chrOfflineRepo.remove(id);
      setIsOfflineCopy(false);
      setCheckList(saved);
      display({
        kind: 'success',
        title: 'Checklist uploaded',
        subtitle: 'Your changes are checked in and the offline copy has been removed.',
        timeout: 5000,
      });
    } catch (err) {
      reportError(
        'Upload failed — the checklist may have changed on the server; re-pull and retry',
        err,
      );
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <Grid fullWidth className="default-grid">
        <Column sm={4} md={8} lg={16}>
          <SkeletonText paragraph lineCount={8} />
        </Column>
      </Grid>
    );
  }

  if (notFound) {
    return (
      <Grid fullWidth className="default-grid">
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            title="Checklist not found"
            subtitle={`No CHR checklist for id ${id}.`}
            hideCloseButton
            lowContrast
          />
        </Column>
      </Grid>
    );
  }

  if (hasError || !checkList) {
    return (
      <Grid fullWidth className="default-grid">
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            title="Unable to load checklist"
            hideCloseButton
            lowContrast
          />
        </Column>
      </Grid>
    );
  }

  // Tombstone header cell — always rendered (with an em-dash when empty) so the layout stays
  // consistent, matching the Opening info "Site" section.
  const headerCell = (label: string, value?: string) => (
    <div key={label}>
      <span className="protocol-checklist__label">{label}</span>
      <span>{value || '—'}</span>
    </div>
  );

  // Opening ID deep-links into SILVA, carrying an idp_hint for the provider the user signed in
  // with so they land on the opening without a second login. New tab (the checklist may hold
  // unsaved edits) with rel="noopener noreferrer" so the opened page gets no handle on this window.
  // Rendered as plain text when offline or when the record has no opening id — this screen works
  // offline, and a link to a corporate app that cannot load is worse than no link.
  const openingIdCell = (value?: string) => {
    const href = online ? silvaOpeningUrl(value, user?.idpProvider) : null;
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
  const orgUnit = [checkList.orgUnitCode, checkList.orgUnitName].filter(Boolean).join(' - ');

  // A tab held back for never having been started starts showing its count once Submit has been
  // pressed — at that point the silence would be hiding the very thing the user asked about.
  const statusFor = (key: ChrTabKey) => {
    const status = tabState.statuses[key];
    return countsRevealed && status === 'empty' && tabState.counts[key] > 0 ? 'errors' : status;
  };

  // A tab lists its outstanding items exactly when its dot is red. That keeps the two in step: a
  // quiet dot on a never-opened tab means a quiet tab, and pressing Submit turns both on at once.
  const visibleOutstanding = (key: ChrTabKey): string[] =>
    statusFor(key) === 'errors' ? tabState.outstanding[key] : [];

  // The tabs the pre-flight objected to, named in the page-level banner. The items themselves stay
  // on the tabs that own them, so there is one place to read them and one place to fix them.
  const preflightTabNames = (() => {
    const names = TABS.filter((t) => preflight[t.key]?.length).map((t) => t.title);
    if (names.length <= 1) return names[0] ?? 'A tab';
    return `${names.slice(0, -1).join(', ')} and ${names.at(-1)}`;
  })();

  /** The per-tab "you can save this, but you can't submit it yet" banner. */
  const tabBanner = (key: ChrTabKey, sectionLabel: string, noun?: string) => (
    <TabIncompleteBanner
      items={visibleOutstanding(key)}
      saved={Boolean(savedTabs[key])}
      sectionLabel={sectionLabel}
      noun={noun}
    />
  );

  return (
    <Grid fullWidth className="default-grid chr-checklist">
      <Column sm={4} md={8} lg={16}>
        <div className="chr-checklist__header">
          <button
            type="button"
            className="chr-checklist__back"
            onClick={() => navigate(-1)}
            aria-label="Back"
          >
            <ArrowLeft /> Back
          </button>
          <div className="chr-checklist__title-row">
            <h1>{`${checkList.checklistID}-Cultural Heritage`}</h1>
            {/* Status reads beside the heading rather than as the last cell of the tombstone grid:
                it governs what the whole page allows, so it belongs where the eye lands first. */}
            <Tag type={statusTagType(checkList.status)} size="sm">
              {STATUS_LABELS[checkList.status ?? ''] ?? checkList.status ?? '—'}
            </Tag>
            {/* An offline copy is your editable local copy (always RDO under the hood), so it is
                flagged separately from the server-side status above. */}
            {isOfflineCopy && (
              <Tag type="teal" size="sm">
                Offline copy
              </Tag>
            )}
            {!online && (
              <Tag type="red" size="sm">
                No network connection
              </Tag>
            )}
            {/* Every checklist-level action lives here, at the top of the page rather than below
                the tombstone tile. Most are mutually exclusive — Sync changes and Remove from
                device only ever appear on an offline copy, Reactivate only on a checked-out one —
                so the row stays short in practice, and wraps under the heading when it doesn't. */}
            <div className="chr-checklist__title-actions">
              {!readOnly && online && !offlineOutOfDate && (
                <Button kind="primary" onClick={() => void handleSubmit()} disabled={busy}>
                  Submit checklist
                </Button>
              )}
              {!isOfflineCopy && online && !readOnly && (
                <Button kind="tertiary" onClick={() => void handleTakeOffline()} disabled={busy}>
                  Take offline
                </Button>
              )}
              {/* A submitted server copy is read-only; Unsubmit reopens it for editing (the legacy
                  FREP_TOMBSTONE.UNSUBMIT proc enforces who may do so, same as Biodiversity). */}
              {!isOfflineCopy &&
                online &&
                canEditThisChr &&
                checkList.status === CHR_STATUS.SUBMITTED && (
                  <Button kind="tertiary" onClick={() => void handleUnsubmit()} disabled={busy}>
                    Unsubmit checklist
                  </Button>
                )}
              {/* Admin-only recovery for a checklist stuck "Checked out" on another device. */}
              {!isOfflineCopy &&
                online &&
                canPerformSysAdminActions &&
                checkList.status === CHR_STATUS.READ_ONLY_OFFLINE && (
                  <Button kind="tertiary" onClick={() => void handleReactivate()} disabled={busy}>
                    Reactivate
                  </Button>
                )}
              {isOfflineCopy && online && !offlineOutOfDate && (
                <Button kind="tertiary" onClick={() => void handleUpload()} disabled={busy}>
                  Sync changes
                </Button>
              )}
              {isOfflineCopy && offlineOutOfDate && (
                <Button
                  kind="danger--tertiary"
                  onClick={() => void handleRemoveOfflineCopy()}
                  disabled={busy}
                >
                  Remove from device
                </Button>
              )}
            </div>
          </div>
        </div>
      </Column>

      {isOfflineCopy &&
        offlineStaleness &&
        (() => {
          const banner = stalenessBanner(offlineStaleness.verdict, offlineStaleness);
          if (!banner) return null;
          return (
            <Column sm={4} md={8} lg={16}>
              <InlineNotification
                kind={banner.kind}
                title={banner.title}
                subtitle={banner.subtitle}
                hideCloseButton
                lowContrast
              />
            </Column>
          );
        })()}

      {/* Not shown for an offline copy — the same reasoning readOnly above already applies. Holding
          the copy at all proves the district check passed at checkout
          (@chrAuth.canEditChecklist gates POST /checklists/{id}/offline), and the role check cannot
          be evaluated offline anyway: there is no session to read the districts from, so it would
          always report "view only" and contradict a form that is genuinely editable. Anything that
          did slip through is refused server-side on sync. */}
      {!canEditThisChr && !isOfflineCopy && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="info"
            title="View only"
            subtitle="You do not have permission to edit CHR checklists for this district."
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      {canEditThisChr && statusLocked && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="info"
            title="Read only"
            subtitle={readOnlyReason(checkList.status)}
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <Tile className="protocol-checklist__summary">
          <div className="protocol-checklist__summary-grid">
            {/* Tombstone header laid out like the Biodiversity checklist (same fields, same order). */}
            {headerCell('Master list year', checkList.effectiveYear)}
            {headerCell('Org unit', orgUnit)}
            {headerCell('Checklist', checkList.checklistID)}
            {headerCell('Client number', checkList.client)}
            {headerCell('Client name', checkList.clientName)}
            {headerCell('Opening number', checkList.openingNumber)}
            {openingIdCell(checkList.openingID)}
            {headerCell('Licence', checkList.licensee)}
            {headerCell('Cutting permit', checkList.cuttingPermit)}
            {headerCell('Cut block', checkList.block)}
            {headerCell('Year of harvest', checkList.yearOfHarvest)}
            {headerCell('Evaluator', checkList.assessedByName || checkList.assessedBy)}
            {headerCell('Evaluation date', formatShortDate(checkList.evaluationDate))}
          </div>
        </Tile>
      </Column>

      {/* What the submit pre-flight found, named by tab. The items themselves are listed on the tabs
          that own them — this says which tabs to go and look at. */}
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

      {/* Submit validation errors returned by the server, shown inline (mirrors the Biodiversity
          checklist's submit-validation panel) rather than behind a tab. */}
      {errors.length > 0 && (
        <Column sm={4} md={8} lg={16}>
          <p className="protocol-checklist__errors-intro">
            This checklist isn&apos;t ready to submit. Fix the following, then submit again:
          </p>
          <div className="chr-checklist__errors">
            {errors.map((e) => (
              <InlineNotification
                key={errorKey(e)}
                kind="error"
                title={errorTitle(e)}
                subtitle={e.message}
                hideCloseButton
                lowContrast
              />
            ))}
          </div>
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <Tabs selectedIndex={tab} onChange={({ selectedIndex }) => setTab(selectedIndex)}>
          <TabList aria-label="CHR checklist sections" contained>
            {TABS.map((section) => (
              <Tab key={section.key}>
                <span className="protocol-checklist__tab-label">
                  <TabStatusIcon
                    status={statusFor(section.key)}
                    count={tabState.counts[section.key]}
                    section={section.title}
                  />
                  {section.title}
                </span>
              </Tab>
            ))}
          </TabList>
          <TabPanels>
            <TabPanel>
              {tabBanner('opening', 'Opening', 'required field')}
              <OpeningInformation
                value={checkList}
                onSave={saveOpening}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            <TabPanel>
              {tabBanner('blockSummary', 'Block summary', 'required field')}
              <BlockSummary
                value={checkList}
                onSave={saveBlockSummary}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            <TabPanel>
              <Contacts
                contacts={checkList.contacts ?? EMPTY_CONTACTS}
                onSave={saveContacts}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            <TabPanel>
              {tabBanner('features', 'Feature')}
              <FeatureList
                features={draftFeatures ?? checkList.features ?? []}
                onChange={setDraftFeatures}
                onSave={saveFeatures}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            <TabPanel>
              <Notes value={checkList} onSave={saveNotes} readOnly={readOnly} busy={busy} />
            </TabPanel>
            <TabPanel>
              {tabBanner('attachments', 'Photo')}
              <Photos
                pictures={
                  isOfflineCopy
                    ? (checkList.pictures ?? []).slice(
                        photoPage * photoPageSize,
                        photoPage * photoPageSize + photoPageSize,
                      )
                    : (checkList.pictures ?? [])
                }
                onAdd={addPhotos}
                onDelete={deletePhoto}
                fetchContent={fetchPhotoContent}
                page={photoPage}
                pageSize={photoPageSize}
                totalCount={isOfflineCopy ? (checkList.pictures ?? []).length : photoTotal}
                onPageChange={(nextPage, nextSize) => void loadPhotos(nextPage, nextSize)}
                readOnly={readOnly}
                busy={busy}
                active={tab === 5}
              />
            </TabPanel>
          </TabPanels>
        </Tabs>
      </Column>
    </Grid>
  );
};

export default ChrChecklistPage;
