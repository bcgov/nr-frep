import { Button, Column, Grid, Tag, Tile } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { useNavigate } from 'react-router-dom';

import type { OfflineChecklist } from '@/services/offline/chrDb';

import { chrOfflineRepo } from '@/services/offline/chrOfflineRepo';

/** Lists CHR checklists currently stored offline in this browser, with quick links to open them. */
const ChrOfflineListPage: FC = () => {
  const navigate = useNavigate();
  const [records, setRecords] = useState<OfflineChecklist[]>([]);

  useEffect(() => {
    let cancelled = false;
    void chrOfflineRepo.listOffline().then((items) => {
      if (!cancelled) setRecords(items);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const remove = async (id: string) => {
    await chrOfflineRepo.remove(id);
    setRecords((prev) => prev.filter((r) => r.checklistId !== id));
  };

  return (
    <Grid fullWidth className="default-grid">
      <Column sm={4} md={8} lg={16}>
        <h1>Offline CHR checklists</h1>
        <p>Checklists saved on this device for offline editing.</p>
      </Column>
      <Column sm={4} md={8} lg={16}>
        {records.length === 0 && <p>No checklists are stored offline.</p>}
        {records.map((record) => (
          <Tile key={record.checklistId} className="chr-checklist__row">
            <strong>Checklist {record.checklistId}</strong>{' '}
            {record.dirty ? (
              <Tag type="magenta" size="sm">
                Unsynced changes
              </Tag>
            ) : (
              <Tag type="green" size="sm">
                Synced
              </Tag>
            )}
            <div className="chr-checklist__actions">
              <Button size="sm" onClick={() => navigate(`/chr/checklists/${record.checklistId}`)}>
                Open
              </Button>
              <Button
                size="sm"
                kind="danger--tertiary"
                onClick={() => void remove(record.checklistId)}
              >
                Remove from device
              </Button>
            </div>
          </Tile>
        ))}
      </Column>
    </Grid>
  );
};

export default ChrOfflineListPage;
