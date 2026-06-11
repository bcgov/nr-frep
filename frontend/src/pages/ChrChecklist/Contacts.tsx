import { Add, TrashCan } from '@carbon/icons-react';
import { Button } from '@carbon/react';

import { CodeSelect, IndicatorCheckbox, TextField } from '@/pages/ChrChecklist/fields';

import type { Contact } from '@/types/chrChecklist';
import type { FC } from 'react';

import { CONTACT_ROLE_CODES } from '@/pages/ChrChecklist/codeLists';

/** Section 2 — First Nation / proponent contacts (add/edit/remove rows). */
const Contacts: FC<{
  contacts: Contact[];
  onChange: (contacts: Contact[]) => void;
  onSave: () => Promise<boolean>;
  readOnly: boolean;
  busy: boolean;
}> = ({ contacts, onChange, onSave, readOnly, busy }) => {
  const patchAt = (index: number, patch: Partial<Contact>) => {
    onChange(contacts.map((c, i) => (i === index ? { ...c, ...patch } : c)));
  };
  const removeAt = (index: number) => onChange(contacts.filter((_, i) => i !== index));
  const add = () => onChange([...contacts, { contactedInd: 'false', attendingOnSiteInd: 'false' }]);

  return (
    <div className="rip-form">
      {!readOnly && (
        <div className="protocol-checklist__section-actions">
          <Button size="lg" disabled={busy} onClick={() => void onSave()}>
            Save
          </Button>
        </div>
      )}
      {contacts.length === 0 && <p>No contacts recorded.</p>}
      {contacts.map((contact, index) => (
        <fieldset key={contact.id ?? `contact-${index}`} className="rip-form__group">
          <legend>Contact {index + 1}</legend>
          <div className="rip-form__grid">
            <TextField
              id={`contact-first-${index}`}
              labelText="First name"
              value={contact.firstName}
              disabled={readOnly}
              onChange={(v) => patchAt(index, { firstName: v })}
            />
            <TextField
              id={`contact-last-${index}`}
              labelText="Last name"
              value={contact.lastName}
              disabled={readOnly}
              onChange={(v) => patchAt(index, { lastName: v })}
            />
            <CodeSelect
              id={`contact-role-${index}`}
              labelText="Role"
              value={contact.roleCode}
              options={CONTACT_ROLE_CODES}
              disabled={readOnly}
              onChange={(v) => patchAt(index, { roleCode: v })}
            />
            <TextField
              id={`contact-org-${index}`}
              labelText="Organization"
              value={contact.organization}
              disabled={readOnly}
              onChange={(v) => patchAt(index, { organization: v })}
            />
            <TextField
              id={`contact-date-${index}`}
              labelText="Contacted date"
              placeholder="YYYY-MM-DD"
              value={contact.contactedDate}
              disabled={readOnly}
              onChange={(v) => patchAt(index, { contactedDate: v })}
            />
            <IndicatorCheckbox
              id={`contact-contacted-${index}`}
              labelText="Contacted"
              value={contact.contactedInd}
              disabled={readOnly}
              onToggle={(v) => patchAt(index, { contactedInd: v })}
            />
            <IndicatorCheckbox
              id={`contact-attending-${index}`}
              labelText="Attending on site"
              value={contact.attendingOnSiteInd}
              disabled={readOnly}
              onToggle={(v) => patchAt(index, { attendingOnSiteInd: v })}
            />
          </div>
          {!readOnly && (
            <Button
              kind="danger--ghost"
              size="sm"
              renderIcon={TrashCan}
              onClick={() => removeAt(index)}
            >
              Remove contact
            </Button>
          )}
        </fieldset>
      ))}
      {!readOnly && (
        <Button kind="tertiary" size="lg" renderIcon={Add} onClick={add}>
          Add contact
        </Button>
      )}
    </div>
  );
};

export default Contacts;
