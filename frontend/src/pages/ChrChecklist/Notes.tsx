import { Edit } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useState, type FC } from 'react';

import { TextAreaField } from '@/pages/ChrChecklist/fields';

import type { CheckList } from '@/types/chrChecklist';

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

  const beginEdit = () => {
    setDraft(value.commentaires);
    setEditing(true);
  };
  const save = async () => {
    if (await onSave({ commentaires: draft })) setEditing(false);
  };

  return (
    <div className="rip-form">
      <div className="protocol-checklist__section-actions">
        {!editing && !readOnly && (
          <Button kind="tertiary" size="lg" disabled={busy} onClick={beginEdit}>
            <span className="protocol-checklist__edit-label">
              <Edit /> Edit
            </span>
          </Button>
        )}
        {editing && (
          <>
            <Button kind="ghost" size="lg" disabled={busy} onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button size="lg" disabled={busy} onClick={() => void save()}>
              Save
            </Button>
          </>
        )}
      </div>

      {editing ? (
        <TextAreaField
          id="chr-notes"
          labelText="Notes"
          value={draft}
          onChange={(v) => setDraft(v)}
        />
      ) : (
        <div className="protocol-checklist__field">
          <span className="protocol-checklist__label">Notes</span>
          <span className="protocol-checklist__value">{value.commentaires || '—'}</span>
        </div>
      )}
    </div>
  );
};

export default Notes;
