import { Edit } from '@carbon/icons-react';
import { Button, SkeletonText, TextArea } from '@carbon/react';
import { useCallback, useEffect, useState, type FC } from 'react';

import type { RiparianNotes } from '@/types/protocolChecklist';

import { useNotification } from '@/context/notification/useNotification';
import API from '@/services/APIs';
import { apiErrorMessage } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';
import FormLock from '@/components/core/FormLock';
import ActionButton from '@/components/core/ActionButton';

/**
 * Byte limit of `<checklist>.note_description`. 2000 is what the legacy app enforced before the
 * insert (`FrepNotesValidationManager.java:32`, a StringLengthValidator on noteDescription); the
 * table DDL is not in this repo, so that validator is the authority.
 */
const NOTE_LIMIT = 2000;

/**
 * Checklist Notes tab (legacy {@code checklistNote}) — a single free-text note, read-only with
 * inline editing. Drives off the typed read and round-trips it (with revision count) on save.
 */

type Props = {
  protocol: string;
  checklistId: string;
  canEdit: boolean;
  submitted: boolean;
};

const RipNotesView: FC<Props> = ({ protocol, checklistId, canEdit, submitted }) => {
  const { display } = useNotification();
  const [data, setData] = useState<RiparianNotes | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  // Errors stay hidden until a save is attempted, matching the other tabs.
  const [showErrors, setShowErrors] = useState(false);
  // The note as it stands on the server. `data` is mutated as the user types, so it can't answer
  // "was there a note before this edit?" — which is what separates an empty save from a deletion.
  const [stored, setStored] = useState('');

  const reportError = useCallback(
    (title: string, err: unknown) =>
      display({
        kind: 'error',
        title,
        subtitle: apiErrorMessage(err),
        timeout: 9000,
      }),
    [display],
  );

  const loadData = useCallback(
    (signal?: { cancelled: boolean }) => {
      setLoading(true);
      API.protocolChecklist
        .getNotes(protocol, checklistId)
        .then((d) => {
          if (!signal?.cancelled) {
            setData(d);
            setStored(d?.noteDescription ?? '');
          }
        })
        .catch((err: unknown) => {
          if (!signal?.cancelled) reportError("We couldn't load the notes", err);
        })
        .finally(() => {
          if (!signal?.cancelled) setLoading(false);
        });
    },
    [protocol, checklistId, reportError],
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
    // First point the user has asked for the note to be saved — reveal the error now.
    setShowErrors(true);
    if (limitError || blankError) return;
    setBusy(true);
    try {
      const saved = await API.protocolChecklist.saveNotes(protocol, checklistId, data);
      setData(saved);
      setStored(saved?.noteDescription ?? '');
      setEditing(false);
      display({ kind: 'success', title: 'Notes saved', timeout: 4000 });
    } catch (err) {
      reportError('Save failed', err);
    } finally {
      setBusy(false);
    }
  };

  // Re-fetch immediately before editing so we hold the current optimistic-lock token. The Opening,
  // Administration and Notes tabs all persist to biodiversity_checklist and share its
  // revision_count, so a sibling-tab save can otherwise leave this view's token stale and
  // FREP_CHECKLIST_NOTES.SAVE rejects it with "record.modified2". A silent refresh (no skeleton)
  // avoids a flicker.
  const beginEdit = async () => {
    setBusy(true);
    try {
      const fresh = await API.protocolChecklist.getNotes(protocol, checklistId);
      setData(fresh);
      setStored(fresh?.noteDescription ?? '');
      setShowErrors(false);
      setEditing(true);
    } catch (err) {
      reportError("We couldn't load the notes", err);
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => {
    loadData();
    setShowErrors(false);
    setEditing(false);
  };

  if (loading) {
    return <SkeletonText paragraph lineCount={4} />;
  }

  const note = data?.noteDescription ?? '';
  const showEditControls = canEdit && !submitted;
  // Checked here rather than left to the database: note_description is byte-limited, so an
  // over-long note used to surface only as a failed save.
  const limitError = overLimitError(note, NOTE_LIMIT);
  // An empty box with no note already stored is nothing to save. The save would send NULL, report
  // "Notes saved", and bump biodiversity_checklist.revision_count — which the Opening and
  // Administration tabs share, so it also invalidates their lock tokens for no change. Clearing an
  // existing note is a real edit, so that case is deliberately still allowed through.
  const blankError = !note.trim() && !stored.trim();

  const readOnlyNote = note ? (
    <div className="protocol-checklist__field">
      <span className="protocol-checklist__value protocol-checklist__multiline">{note}</span>
    </div>
  ) : (
    <p>No notes yet</p>
  );

  return (
    <div className="rip-form">
      <FormLock busy={busy}>
        <div className="protocol-checklist__section-actions">
          {!editing && showEditControls && (
            <Button kind="tertiary" size="lg" disabled={busy} onClick={() => void beginEdit()}>
              <span className="protocol-checklist__edit-label">
                Edit <Edit />
              </span>
            </Button>
          )}
          {editing && (
            <>
              <Button kind="ghost" size="lg" disabled={busy} onClick={cancel}>
                Cancel
              </Button>
              <ActionButton
                busy={busy}
                disabled={Boolean(limitError)}
                onClick={() => void handleSave()}
              />
            </>
          )}
        </div>

        {editing ? (
          // Same counter treatment as the CHR text fields (see utils/textLimits.ts): count bytes,
          // never truncate, and let the count sit bottom-right under the box.
          <div className="frep-field">
            <TextArea
              autoComplete="off"
              id="rip-note"
              labelText="Notes"
              rows={10}
              value={note}
              invalid={Boolean(limitError) || (showErrors && blankError)}
              invalidText={limitError || 'Enter a note before saving.'}
              onChange={(e) =>
                setData((prev) => ({ ...(prev ?? { checklistId }), noteDescription: e.target.value }))
              }
            />
            <div className="frep-field__footer">
              <span
                className={
                  limitError ? 'frep-field__counter frep-field__counter--over' : 'frep-field__counter'
                }
                aria-live="polite"
              >
                {byteLength(note)} / {NOTE_LIMIT}
              </span>
            </div>
          </div>
        ) : (
          readOnlyNote
        )}
      </FormLock>
    </div>
  );
};

export default RipNotesView;
