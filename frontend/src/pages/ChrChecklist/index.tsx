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
import { useParams } from 'react-router-dom';

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

import './chrChecklist.scss';

const STATUS_LABELS: Record<string, string> = {
  [CHR_STATUS.ACTIVE]: 'Active',
  [CHR_STATUS.SUBMITTED]: 'Submitted',
  [CHR_STATUS.READ_ONLY_OFFLINE]: 'Checked out (offline)',
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
  const { display } = useNotification();
  const { canEdit, isViewOnly } = useAuthorization();
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

  const readOnly = isViewOnly || !canEdit || checkList?.status === CHR_STATUS.SUBMITTED;

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

  const handleSave = async () => {
    if (!checkList) return;
    setBusy(true);
    try {
      const payload = prepareForSave(checkList);
      if (isOfflineCopy) {
        await chrOfflineRepo.saveLocal(payload);
        display({ kind: 'success', title: 'Saved offline', timeout: 4000 });
      } else {
        const saved = await API.chrChecklist.save(payload);
        setCheckList(saved);
        display({ kind: 'success', title: 'Checklist saved', timeout: 4000 });
      }
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const handleSubmit = async () => {
    if (!checkList) return;
    setBusy(true);
    setErrors([]);
    try {
      const saved = await API.chrChecklist.submit(id, prepareForSave(checkList));
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
          <h1>CHR checklist {checkList.checklistID}</h1>
          <Tag type="blue" size="sm">
            {STATUS_LABELS[checkList.status ?? ''] ?? checkList.status ?? '—'}
          </Tag>
          {isOfflineCopy && (
            <Tag type="teal" size="sm">
              Offline copy
            </Tag>
          )}
          <Tag type={online ? 'green' : 'red'} size="sm">
            {online ? 'Online' : 'Offline'}
          </Tag>
          <Tag type="cool-gray" size="sm">
            MRVA {mrva || '—'}
          </Tag>
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

      <Column sm={4} md={8} lg={16}>
        <div className="chr-checklist__actions">
          {!readOnly && (
            <Button onClick={() => void handleSave()} disabled={busy}>
              {isOfflineCopy ? 'Save offline' : 'Save'}
            </Button>
          )}
          {!readOnly && (
            <Button kind="primary" onClick={() => void handleSubmit()} disabled={busy}>
              Submit
            </Button>
          )}
          {!isOfflineCopy && online && (
            <Button kind="tertiary" onClick={() => void handleTakeOffline()} disabled={busy}>
              Take offline
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
            <Tab>Photos</Tab>
            {errors.length > 0 && <Tab>Errors ({errors.length})</Tab>}
          </TabList>
          <TabPanels>
            <TabPanel>
              <OpeningInformation value={checkList} onPatch={patch} readOnly={readOnly} />
            </TabPanel>
            <TabPanel>
              <BlockSummary value={checkList} onPatch={patch} readOnly={readOnly} />
            </TabPanel>
            <TabPanel>
              <Contacts
                contacts={checkList.contacts ?? []}
                onChange={(contacts: ContactDto[]) => patch({ contacts })}
                readOnly={readOnly}
              />
            </TabPanel>
            <TabPanel>
              <FeatureList
                features={checkList.features ?? []}
                onChange={(features: Feature[]) => patch({ features })}
                readOnly={readOnly}
              />
            </TabPanel>
            <TabPanel>
              <Photos
                pictures={checkList.pictures ?? []}
                onChange={(pictures: Picture[]) => patch({ pictures })}
                readOnly={readOnly}
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
