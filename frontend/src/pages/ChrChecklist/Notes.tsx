import { Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState, type FC } from 'react';

import { TextAreaField } from '@/pages/ChrChecklist/fields';

import type { CheckList } from '@/types/chrChecklist';

import { NOTES_TEXT_LIMITS } from '@/pages/ChrChecklist/textLimits';
import { overLimitError } from '@/utils/textLimits';

/**
 * Section — Notes: the block-level free-text note (legacy CHR "Comments" tab → `BLOCK_COMMENTS`,
 * exposed as `commentaires`). Its own tab to mirror the Biodiversity "Notes" tab. Read-only by
 * default with an Edit / Save / Cancel toggle; persists via the block-summary save (same column).
 */
const Notes: FC<{
  value: CheckList;
  onSave: (patch: Partial<CheckList>) => Promise<boolean>;
  readOnly: boolean;
  busy: boolean;
}> = ({ value, onSave, readOnly, busy }) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<string | undefined>(undefined);
  // Errors stay hidden until a save is attempted, matching the other CHR tabs.
  const [showErrors, setShowErrors] = useState(false);

  const beginEdit = () => {
    setDraft(value.commentaires);
    setShowErrors(false);
    setEditing(true);
  };
  // Block the save while the note is over its column limit, the same way the other CHR forms
  // gate on their field errors — the counter has already said why.
  const limitError = overLimitError(draft, NOTES_TEXT_LIMITS.commentaires);
  // An empty box with no note already stored is nothing to save: it would write a blank comment,
  // report "saved", and bump the checklist's revision count for no change. Clearing an existing
  // note is a real edit, so that case is deliberately still allowed through.
  const blankError = !draft?.trim() && !value.commentaires?.trim();
  const save = async () => {
    // First point the user has asked for the note to be saved — reveal the error now.
    setShowErrors(true);
    if (limitError || blankError) return;
    if (await onSave({ commentaires: draft })) setEditing(false);
  };

  const readOnlyNotes = value.commentaires ? (
    <div className="protocol-checklist__field">
      <span className="protocol-checklist__value protocol-checklist__multiline">
        {value.commentaires}
      </span>
    </div>
  ) : (
    <p>No notes yet</p>
  );

  return (
    <div className="rip-form">
      <div className="protocol-checklist__section-actions">
        {!editing && !readOnly && (
          <Button kind="tertiary" size="lg" disabled={busy} onClick={beginEdit}>
            <span className="protocol-checklist__edit-label">
              Edit <Edit />
            </span>
          </Button>
        )}
        {editing && (
          <>
            <Button
              kind="ghost"
              size="lg"
              disabled={busy}
              onClick={() => {
                setShowErrors(false);
                setEditing(false);
              }}
            >
              Cancel
            </Button>
            <Button size="lg" disabled={busy || Boolean(limitError)} onClick={() => void save()}>
              Save
            </Button>
          </>
        )}
      </div>

      {editing ? (
        <TextAreaField
          id="chr-notes"
          labelText="Notes"
          rows={10}
          value={draft}
          limit={NOTES_TEXT_LIMITS.commentaires}
          invalid={showErrors && blankError}
          invalidText="Enter a note before saving."
          onChange={(v) => setDraft(v)}
        />
      ) : (
        readOnlyNotes
      )}
    </div>
  );
};

export default Notes;
