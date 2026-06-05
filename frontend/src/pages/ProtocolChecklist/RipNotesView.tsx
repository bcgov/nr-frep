import { Edit } from '@carbon/icons-react';
import { Button, SkeletonText, TextArea } from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import type { RiparianNotes } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';

/**
 * Checklist Notes tab (legacy {@code checklistNote}) — a single free-text note, read-only with
 * inline editing. Drives off the typed read and round-trips it (with revision count) on save.
 */

type Props = {
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

const RipNotesView: FC<Props> = ({ checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [data, setData] = useState<RiparianNotes | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

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

  const loadData = useCallback(
    (signal?: { cancelled: boolean }) => {
      setLoading(true);
      API.protocolChecklist
        .getRipNotes(checklistId)
        .then((d) => {
          if (!signal?.cancelled) setData(d);
        })
        .catch((err: unknown) => {
          if (!signal?.cancelled) reportError("We couldn't load the notes", err);
        })
        .finally(() => {
          if (!signal?.cancelled) setLoading(false);
        });
    },
    [checklistId, reportError],
  );

  useEffect(() => {
    const signal = { cancelled: false };
    loadData(signal);
    return () => {
      signal.cancelled = true;
    };
  }, [loadData]);

  const handleSave = async () => {
    if (!data) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveRipNotes(checklistId, data);
      setData(saved);
      setEditing(false);
      display({ kind: 'success', title: 'Notes saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    loadData();
    setEditing(false);
  };

  if (loading) {
    return <SkeletonText paragraph lineCount={4} />;
  }

  const note = data?.noteDescription ?? '';
  const showEditControls = canEdit && !submitted;

  return (
    <div className="rip-form">
      <div className="protocol-checklist__section-actions">
        {!editing && showEditControls && (
          <Button kind="tertiary" size="lg" onClick={() => setEditing(true)}>
            <span className="protocol-checklist__edit-label">
              <Edit /> Edit
            </span>
          </Button>
        )}
        {editing && (
          <>
            <Button kind="ghost" size="lg" disabled={busy} onClick={cancel}>
              Cancel
            </Button>
            <Button size="lg" disabled={busy} onClick={() => void handleSave()}>
              Save
            </Button>
          </>
        )}
      </div>

      {editing ? (
        <TextArea
          id="rip-note"
          labelText="Notes"
          rows={10}
          value={note}
          onChange={(e) =>
            setData((prev) => ({ ...(prev ?? { checklistId }), noteDescription: e.target.value }))
          }
        />
      ) : (
        <div className="protocol-checklist__field">
          <span className="protocol-checklist__label">Notes</span>
          <span className="protocol-checklist__value protocol-checklist__multiline">
            {note || '—'}
          </span>
        </div>
      )}
    </div>
  );
};

export default RipNotesView;
