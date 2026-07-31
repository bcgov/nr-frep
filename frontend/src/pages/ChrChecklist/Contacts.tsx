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
import { useNotification } from '@/context/notification/useNotification';
import { CONTACT_ROLE_CODES } from '@/pages/ChrChecklist/codeLists';

const roleLabel = (code?: string) =>
  CONTACT_ROLE_CODES.find((r) => r.code === code)?.label ?? code ?? '';
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
  const confirm = useConfirm();
  const { display } = useNotification();
  const [form, setForm] = useState<FormState | null>(null);

  const openAdd = () =>
    setForm({ index: null, draft: { contactedInd: 'false', attendingOnSiteInd: 'false' } });
  const openEdit = (index: number) => setForm({ index, draft: { ...contacts[index] } });
  const setField = (patch: Partial<Contact>) =>
    setForm((f) => (f ? { ...f, draft: { ...f.draft, ...patch } } : f));

  const save = async () => {
    if (!form) return;
    const { draft } = form;
    // Nothing meaningful entered — don't persist a blank contact (mirrors SiteDetail's guard).
    const isEmpty =
      !draft.firstName?.trim() &&
      !draft.lastName?.trim() &&
      !draft.roleCode?.trim() &&
      !draft.organization?.trim();
    if (isEmpty) {
      display({
        kind: 'info',
        title: 'Nothing to save',
        subtitle: 'Enter a name, role, or organization before saving the contact.',
        timeout: 6000,
      });
      return;
    }
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
        title: 'Delete contact?',
        message: `Delete ${name}? This can't be undone.`,
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
            <Button size="lg" disabled={busy} onClick={() => void save()}>
              Save
            </Button>
          )}
          <Button kind="ghost" size="lg" disabled={busy} onClick={() => setForm(null)}>
            Cancel
          </Button>
        </div>
        <fieldset className="rip-form__group">
          <legend>{form.index === null ? 'New contact' : 'Edit contact'}</legend>
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
              options={CONTACT_ROLE_CODES}
              disabled={readOnly}
              onChange={(v) => setField({ roleCode: v })}
            />
            <TextField
              id="contact-org"
              labelText="Organization"
              value={draft.organization}
              disabled={readOnly}
              maxLength={60}
              onChange={(v) => setField({ organization: v })}
            />
            <IndicatorCheckbox
              id="contact-contacted"
              labelText="Contacted"
              value={draft.contactedInd}
              disabled={readOnly}
              onToggle={(v) => setField({ contactedInd: v })}
            />
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
            <IndicatorCheckbox
              id="contact-attending"
              labelText="Attending on site"
              value={draft.attendingOnSiteInd}
              disabled={readOnly}
              onToggle={(v) => setField({ attendingOnSiteInd: v })}
            />
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
              disabled={busy}
              onClick={openAdd}
            >
              <Add size={16} className="bio-strata__add-icon" />
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
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {contacts.map((contact, index) => (
                <TableRow key={contact.id ?? `contact-${index}`}>
                  <TableCell>{fullName(contact) || '—'}</TableCell>
                  <TableCell>{roleLabel(contact.roleCode) || '—'}</TableCell>
                  <TableCell>{contact.organization || '—'}</TableCell>
                  <TableCell>
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Edit}
                      iconDescription="Edit"
                      hasIconOnly
                      disabled={busy}
                      onClick={() => openEdit(index)}
                    />
                    {!readOnly && (
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        renderIcon={TrashCan}
                        iconDescription="Delete"
                        hasIconOnly
                        disabled={busy}
                        onClick={() => void remove(index)}
                      />
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
