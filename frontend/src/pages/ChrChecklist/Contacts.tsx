import { Add, Edit, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState, type FC } from 'react';

import { CodeSelect, DateField, IndicatorCheckbox, TextField } from '@/pages/ChrChecklist/fields';

import type { Contact } from '@/types/chrChecklist';

import { useConfirm } from '@/context/confirm/useConfirm';
import { labelFor, useContactRoleCodes } from '@/pages/ChrChecklist/useChrCodeLists';
import ActionButton from '@/components/core/ActionButton';

const fullName = (c: Contact) => `${c.firstName ?? ''} ${c.lastName ?? ''}`.trim();

/** A contact form session: editing an existing row (index ≥ 0) or adding a new one (index null). */
type FormState = { index: number | null; draft: Contact };

/**
 * Section 2 — First Nation / proponent contacts. Master-detail: a table of contacts with per-row
 * Edit / Delete and an "Add contact" button (mirroring the Biodiversity Stratum tab). Editing or
 * adding opens a detail form; Save commits the whole list, Cancel discards the form. Delete removes
 * the row and persists immediately.
 */
const Contacts: FC<{
  contacts: Contact[];
  onSave: (contacts: Contact[]) => Promise<boolean>;
  readOnly: boolean;
  busy: boolean;
}> = ({ contacts, onSave, readOnly, busy }) => {
  // The roles come from CHR_PARTICIPANT_ROLE_CODE, so the table's label lookup needs them too.
  const roleCodes = useContactRoleCodes();
  const roleLabel = (code?: string) => labelFor(roleCodes, code);
  const confirm = useConfirm();
  const [form, setForm] = useState<FormState | null>(null);
  // Errors stay hidden until a save is attempted, matching the other CHR tabs.
  const [showErrors, setShowErrors] = useState(false);

  const openAdd = () => {
    setShowErrors(false);
    setForm({ index: null, draft: { contactedInd: 'false', attendingOnSiteInd: 'false' } });
  };
  const openEdit = (index: number) => {
    setShowErrors(false);
    setForm({ index, draft: { ...contacts[index] } });
  };
  const setField = (patch: Partial<Contact>) =>
    setForm((f) => (f ? { ...f, draft: { ...f.draft, ...patch } } : f));

  // Nothing meaningful entered — don't persist a blank contact. No field is individually required
  // (every column on CHR_CHECKLIST_PARTICIPANT/_PARTICIPATION that this form writes is nullable, and
  // neither the section save nor submit validation requires a contact at all), so this is the one
  // rule the form has, and it is form-level rather than per-field: marking any single input invalid
  // would name the wrong culprit.
  const isEmpty = (draft: Contact): boolean =>
    !draft.firstName?.trim() &&
    !draft.lastName?.trim() &&
    !draft.roleCode?.trim() &&
    !draft.organization?.trim();

  const save = async () => {
    if (!form) return;
    const { draft } = form;
    // First point the user has asked for the contact to be complete — reveal the error now.
    setShowErrors(true);
    if (isEmpty(draft)) return;
    const next =
      form.index === null
        ? [...contacts, draft]
        : contacts.map((c, i) => (i === form.index ? draft : c));
    if (await onSave(next)) setForm(null);
  };
  const remove = async (index: number) => {
    const name = fullName(contacts[index]) || 'this contact';
    if (
      !(await confirm({
        title: 'Are you sure you want to delete this contact?',
        // "Removed", not "deleted": what goes is this person's place on the checklist, not any
        // record of the person themselves.
        message: (
          <>
            <strong>{name}</strong> will be permanently removed from this checklist. This action
            cannot be undone.
          </>
        ),
      }))
    )
      return;
    await onSave(contacts.filter((_, i) => i !== index));
  };

  // Detail form (add / edit a single contact).
  if (form) {
    const { draft } = form;
    return (
      <div className="rip-form">
        <div className="protocol-checklist__section-actions">
          {!readOnly && (
            <ActionButton busy={busy} onClick={() => void save()} />
          )}
          <Button
            kind="ghost"
            size="lg"
            disabled={busy}
            onClick={() => {
              setShowErrors(false);
              setForm(null);
            }}
          >
            Cancel
          </Button>
        </div>
        <fieldset className="rip-form__group">
          <legend>{form.index === null ? 'New contact' : 'Edit contact'}</legend>
          {showErrors && isEmpty(draft) && (
            <p className="chr-checklist__form-error">
              Enter a name, role, or organization before saving the contact.
            </p>
          )}
          <div className="rip-form__grid chr-checklist__contacts-grid">
            <TextField
              id="contact-first"
              labelText="First name"
              value={draft.firstName}
              disabled={readOnly}
              maxLength={40}
              onChange={(v) => setField({ firstName: v })}
            />
            <TextField
              id="contact-last"
              labelText="Last name"
              value={draft.lastName}
              disabled={readOnly}
              maxLength={40}
              onChange={(v) => setField({ lastName: v })}
            />
            <CodeSelect
              id="contact-role"
              labelText="Role"
              value={draft.roleCode}
              options={roleCodes}
              disabled={readOnly}
              onChange={(v) => setField({ roleCode: v })}
            />
            {/* Organization starts the second row: the name fields and the role are who the contact
                is, the rest is what has happened with them. */}
            <TextField
              id="contact-org"
              className="chr-checklist__row-break"
              labelText="Organization"
              value={draft.organization}
              disabled={readOnly}
              maxLength={60}
              onChange={(v) => setField({ organization: v })}
            />
            <div className="chr-checklist__grid-check">
              <IndicatorCheckbox
                id="contact-contacted"
                labelText="Contacted"
                value={draft.contactedInd}
                disabled={readOnly}
                onToggle={(v) => setField({ contactedInd: v })}
              />
            </div>
            {/* Contacted date shows only once the contact has been contacted (legacy parity). */}
            {draft.contactedInd === 'true' && (
              <DateField
                id="contact-date"
                labelText="Contacted date"
                value={draft.contactedDate}
                disabled={readOnly}
                onChange={(v) => setField({ contactedDate: v })}
              />
            )}
            <div className="chr-checklist__grid-check">
              <IndicatorCheckbox
                id="contact-attending"
                labelText="Attending on site"
                value={draft.attendingOnSiteInd}
                disabled={readOnly}
                onToggle={(v) => setField({ attendingOnSiteInd: v })}
              />
            </div>
          </div>
        </fieldset>
      </div>
    );
  }

  // List view: a table of contacts with per-row Edit / Delete + an "Add contact" toolbar button.
  return (
    <div className="rip-form">
      <div className="bio-strata">
        {!readOnly && (
          <div className="bio-strata__toolbar">
            <Button
              kind="tertiary"
              size="lg"
              className="bio-strata__add"
              renderIcon={Add}
              disabled={busy}
              onClick={openAdd}
            >
              Add contact
            </Button>
          </div>
        )}
        {contacts.length === 0 ? (
          <p>No contacts recorded.</p>
        ) : (
          <Table size="sm" className="bio-strata__table">
            <TableHead>
              <TableRow>
                <TableHeader>Name</TableHeader>
                <TableHeader>Role</TableHeader>
                <TableHeader>Organization</TableHeader>
                <TableHeader>Action</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {contacts.map((contact, index) => (
                <TableRow key={contact.id ?? `contact-${index}`}>
                  <TableCell>{fullName(contact) || '—'}</TableCell>
                  <TableCell>{roleLabel(contact.roleCode) || '—'}</TableCell>
                  <TableCell>{contact.organization || '—'}</TableCell>
                  <TableCell className="table-actions">
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Edit}
                      disabled={busy}
                      onClick={() => openEdit(index)}
                    >
                      Edit
                    </Button>
                    {!readOnly && (
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        renderIcon={TrashCan}
                        disabled={busy}
                        onClick={() => void remove(index)}
                      >
                        Delete
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  );
};

export default Contacts;
