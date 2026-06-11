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
} from '@carbon/react';
import { useCallback, useEffect, useMemo, useState, type FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import BlockSummary from '@/pages/ChrChecklist/BlockSummary';
import Contacts from '@/pages/ChrChecklist/Contacts';
import FeatureList from '@/pages/ChrChecklist/FeatureList';
import OpeningInformation from '@/pages/ChrChecklist/OpeningInformation';
import Photos from '@/pages/ChrChecklist/Photos';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { calculateMrvaRatingCode } from '@/pages/ChrChecklist/codeLists';
import API from '@/services/APIs';
import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';
import {
  CHR_STATUS,
  type CheckList,
  type Contact as ContactDto,
  type Feature,
  type Picture,
  type ValidationError,
} from '@/types/chrChecklist';

// Reuse the Biodiversity checklist form primitives (rip-form / rip-form__group / rip-form__grid /
// protocol-checklist__field) so CHR tab content is structured the same way.
import '@/pages/ProtocolChecklist/protocolChecklist.scss';
import './chrChecklist.scss';

const STATUS_LABELS: Record<string, string> = {
  [CHR_STATUS.ACTIVE]: 'Active',
  [CHR_STATUS.SUBMITTED]: 'Submitted',
  [CHR_STATUS.READ_ONLY_OFFLINE]: 'Checked out',
};

/** Strip browser data-URL prefixes from new photos and recompute the MRVA before sending. */
const prepareForSave = (checkList: CheckList): CheckList => ({
  ...checkList,
  mrvaRatingCode: calculateMrvaRatingCode(checkList.rating, checkList.features),
  pictures: (checkList.pictures ?? []).map((p) =>
    p.code && p.code.startsWith('data:')
      ? { ...p, code: p.code.replace(/^data:[^;]+;base64,/, '') }
      : p,
  ),
});

const extractValidationErrors = (err: unknown): ValidationError[] | null => {
  const body = (err as { body?: unknown })?.body;
  return Array.isArray(body) ? (body as ValidationError[]) : null;
};

const ChrChecklistPage: FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { display } = useNotification();
  const { canEdit, isViewOnly, canPerformSysAdminActions } = useAuthorization();
  const online = useOnlineStatus();

  const [checkList, setCheckList] = useState<CheckList | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [isOfflineCopy, setIsOfflineCopy] = useState(false);
  const [errors, setErrors] = useState<ValidationError[]>([]);
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState(0);

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
          subtitle: err instanceof Error ? err.message : 'Unknown error',
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

  // Editability mirrors the backend gate: an online (server) copy can only be saved when its status
  // is ACT — the legacy save rejects any other status with "Checklist status does not allow this
  // operation." An offline copy is the user's own checked-out (RDO) copy, editable locally until
  // submitted. Without this, a checklist checked out offline (RDO) would appear editable here but
  // every save would fail server-side.
  const statusLocked =
    !isOfflineCopy && checkList != null && checkList.status !== CHR_STATUS.ACTIVE;
  const readOnly =
    isViewOnly ||
    !canEdit ||
    (isOfflineCopy ? checkList?.status === CHR_STATUS.SUBMITTED : statusLocked);

  const patch = useCallback(
    (p: Partial<CheckList>) => setCheckList((prev) => (prev ? { ...prev, ...p } : prev)),
    [],
  );

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: err instanceof Error ? err.message : 'Unknown error',
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
      endpoint: (checklistId: string, cl: CheckList) => Promise<CheckList>,
      merged: CheckList,
      applyBack: (prev: CheckList, saved: CheckList) => CheckList,
    ): Promise<boolean> => {
      setBusy(true);
      try {
        const payload = prepareForSave(merged);
        if (isOfflineCopy) {
          await chrOfflineRepo.saveLocal(payload);
          setCheckList(merged);
          display({ kind: 'success', title: 'Saved offline', timeout: 4000 });
        } else {
          const saved = await endpoint(id, payload);
          setCheckList((prev) => (prev ? applyBack(prev, saved) : prev));
          display({ kind: 'success', title: 'Checklist saved', timeout: 4000 });
        }
        return true;
      } catch (err) {
        reportError('Save failed', err);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [id, isOfflineCopy, display, reportError],
  );

  // Opening / Block summary are buffered (Edit → Save): they pass their committed draft, which we
  // merge into the checklist and carry forward verbatim (plus the server's fresh revision count).
  const saveOpening = useCallback(
    (draft: Partial<CheckList>): Promise<boolean> => {
      if (!checkList) return Promise.resolve(false);
      return persistSection(
        (cid, cl) => API.chrChecklist.saveOpening(cid, cl),
        { ...checkList, ...draft },
        (prev, saved) => ({ ...prev, ...draft, revisionCount: saved.revisionCount }),
      );
    },
    [checkList, persistSection],
  );

  const saveBlockSummary = useCallback(
    (draft: Partial<CheckList>): Promise<boolean> => {
      if (!checkList) return Promise.resolve(false);
      return persistSection(
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

  // Contacts / Features / Attachments edit the shared checklist in place (onPatch); their Save
  // posts the current checklist and pulls back the section's server truth (e.g. new row ids).
  const saveContacts = useCallback((): Promise<boolean> => {
    if (!checkList) return Promise.resolve(false);
    return persistSection(
      (cid, cl) => API.chrChecklist.saveContacts(cid, cl),
      checkList,
      (prev, saved) => ({ ...prev, contacts: saved.contacts, revisionCount: saved.revisionCount }),
    );
  }, [checkList, persistSection]);

  const saveFeatures = useCallback((): Promise<boolean> => {
    if (!checkList) return Promise.resolve(false);
    return persistSection(
      (cid, cl) => API.chrChecklist.saveFeatures(cid, cl),
      checkList,
      (prev, saved) => ({ ...prev, features: saved.features, revisionCount: saved.revisionCount }),
    );
  }, [checkList, persistSection]);

  const savePhotos = useCallback((): Promise<boolean> => {
    if (!checkList) return Promise.resolve(false);
    return persistSection(
      (cid, cl) => API.chrChecklist.savePhotos(cid, cl),
      checkList,
      (prev, saved) => ({ ...prev, pictures: saved.pictures, revisionCount: saved.revisionCount }),
    );
  }, [checkList, persistSection]);

  const handleSubmit = async () => {
    if (!checkList) return;
    setBusy(true);
    setErrors([]);
    try {
      // Submit requires the server status to be ACT, but an offline copy is RDO. So for an offline
      // copy, first check it back in (upload: RDO → ACT) and drop the local draft, then submit the
      // now-active checklist. Checking in before submit also avoids a stuck state: upload clears the
      // server deviceCheckoutGuid, so the local copy can't upload a second time — if submit then
      // fails validation, we're cleanly on the online ACT checklist and can fix + resubmit.
      let toSubmit = checkList;
      if (isOfflineCopy) {
        await chrOfflineRepo.saveLocal(prepareForSave(checkList));
        toSubmit = await chrOfflineRepo.upload(id);
        await chrOfflineRepo.remove(id);
        setIsOfflineCopy(false);
        setCheckList(toSubmit);
      }
      const saved = await API.chrChecklist.submit(id, prepareForSave(toSubmit));
      setCheckList(saved);
      display({ kind: 'success', title: 'Checklist submitted', timeout: 5000 });
    } catch (err) {
      const validation = extractValidationErrors(err);
      if (validation) {
        setErrors(validation);
        setTab(5); // Errors tab
        display({ kind: 'warning', title: 'Submit blocked by validation', timeout: 6000 });
      } else {
        reportError('Submit failed', err);
      }
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
      setCheckList(saved);
      display({ kind: 'success', title: 'Checklist reactivated', timeout: 4000 });
    } catch (err) {
      reportError('Reactivate failed', err);
    } finally {
      setBusy(false);
    }
  };

  const handleUpload = async () => {
    if (!checkList) return;
    setBusy(true);
    try {
      await chrOfflineRepo.saveLocal(prepareForSave(checkList));
      const saved = await chrOfflineRepo.upload(id);
      setCheckList(saved);
      display({ kind: 'success', title: 'Checklist uploaded', timeout: 5000 });
    } catch (err) {
      reportError(
        'Upload failed — the checklist may have changed on the server; re-pull and retry',
        err,
      );
    } finally {
      setBusy(false);
    }
  };

  const mrva = useMemo(
    () => calculateMrvaRatingCode(checkList?.rating, checkList?.features),
    [checkList?.rating, checkList?.features],
  );

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
            <h1>CHR checklist {checkList.checklistID}</h1>
            {isOfflineCopy ? (
              // An offline copy is always RDO ("Checked out") under the hood — but it's YOUR
              // editable local copy, so the "Offline copy" tag conveys that. Showing "Checked out"
              // alongside it (and an editable form) reads as a contradiction, so suppress it here.
              <Tag type="teal" size="sm">
                Offline copy
              </Tag>
            ) : (
              <Tag type="blue" size="sm">
                {STATUS_LABELS[checkList.status ?? ''] ?? checkList.status ?? '—'}
              </Tag>
            )}
            {!online && (
              <Tag type="red" size="sm">
                No network connection
              </Tag>
            )}
            <Tag type="cool-gray" size="sm">
              MRVA {mrva || '—'}
            </Tag>
          </div>
        </div>
      </Column>

      {!canEdit && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="info"
            title="View only"
            subtitle="You do not have permission to edit CHR checklists."
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      {canEdit && !isViewOnly && statusLocked && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="info"
            title="Read only"
            subtitle={
              checkList.status === CHR_STATUS.READ_ONLY_OFFLINE
                ? 'This checklist is checked out offline, so the online copy is read-only. Upload it from the device that holds it (which reactivates it), or have it reactivated, to edit online.'
                : checkList.status === CHR_STATUS.SUBMITTED
                  ? 'This checklist has been submitted and is read-only. Unsubmit it to make changes.'
                  : 'This checklist is not active, so it is read-only.'
            }
            hideCloseButton
            lowContrast
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <div className="chr-checklist__actions">
          {!readOnly && (
            <Button kind="primary" onClick={() => void handleSubmit()} disabled={busy}>
              Submit
            </Button>
          )}
          {!isOfflineCopy && online && !readOnly && (
            <Button kind="tertiary" onClick={() => void handleTakeOffline()} disabled={busy}>
              Take offline
            </Button>
          )}
          {!isOfflineCopy &&
            online &&
            canPerformSysAdminActions &&
            checkList.status === CHR_STATUS.READ_ONLY_OFFLINE && (
              <Button kind="tertiary" onClick={() => void handleReactivate()} disabled={busy}>
                Reactivate
              </Button>
            )}
          {isOfflineCopy && (
            <Button kind="tertiary" onClick={() => void handleUpload()} disabled={busy || !online}>
              Upload
            </Button>
          )}
        </div>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <Tabs selectedIndex={tab} onChange={({ selectedIndex }) => setTab(selectedIndex)}>
          <TabList aria-label="CHR checklist sections" contained>
            <Tab>Opening info</Tab>
            <Tab>Block summary</Tab>
            <Tab>Contacts</Tab>
            <Tab>Features</Tab>
            <Tab>Attachments</Tab>
            {errors.length > 0 && <Tab>Errors ({errors.length})</Tab>}
          </TabList>
          <TabPanels>
            <TabPanel>
              <OpeningInformation
                value={checkList}
                onSave={saveOpening}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            <TabPanel>
              <BlockSummary
                value={checkList}
                onSave={saveBlockSummary}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            <TabPanel>
              <Contacts
                contacts={checkList.contacts ?? []}
                onChange={(contacts: ContactDto[]) => patch({ contacts })}
                onSave={saveContacts}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            <TabPanel>
              <FeatureList
                features={checkList.features ?? []}
                onChange={(features: Feature[]) => patch({ features })}
                onSave={saveFeatures}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            <TabPanel>
              <Photos
                pictures={checkList.pictures ?? []}
                onChange={(pictures: Picture[]) => patch({ pictures })}
                onSave={savePhotos}
                readOnly={readOnly}
                busy={busy}
              />
            </TabPanel>
            {errors.length > 0 && (
              <TabPanel>
                <div className="chr-checklist__errors">
                  {errors.map((e, i) => (
                    <InlineNotification
                      key={`err-${i}`}
                      kind="error"
                      title={e.field || e.type || 'Validation error'}
                      subtitle={e.message}
                      hideCloseButton
                      lowContrast
                    />
                  ))}
                </div>
              </TabPanel>
            )}
          </TabPanels>
        </Tabs>
      </Column>
    </Grid>
  );
};

export default ChrChecklistPage;
