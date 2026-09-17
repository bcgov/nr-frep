import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { page } from '@vitest/browser/context';
import { describe, expect, it, vi } from 'vitest';

import { ConfirmProvider } from '@/context/confirm/ConfirmProvider';

// Geometry, so the real stylesheets have to be loaded — a browser spec loads none by default.
import '@/styles/index.scss';
import '@/pages/ProtocolChecklist/protocolChecklist.scss';
import './chrChecklist.scss';

import Contacts from './Contacts';

// The Role dropdown reads CHR_PARTICIPANT_ROLE_CODE now, so the component fetches on mount.
vi.mock('@/services/APIs', async () => {
  const { chrCodeListApi } = await import('@/testing/chrCodeListApi');
  return { default: { configuration: chrCodeListApi() } };
});

const BLANK_ERROR = 'Enter a name, role, or organization before saving the contact.';

const renderContacts = (onSave = vi.fn().mockResolvedValue(true)) => {
  render(
    <ConfirmProvider>
      <Contacts contacts={[]} onSave={onSave} readOnly={false} busy={false} />
    </ConfirmProvider>,
  );
  return onSave;
};

describe('Contacts — blank-contact guard', () => {
  it('opens the new-contact form clean, then shows the error inline once Save is clicked', async () => {
    const onSave = renderContacts();
    await userEvent.click(screen.getByRole('button', { name: /Add contact/ }));

    // Nothing entered yet, but the user has not asked to save — the form must be quiet.
    expect(screen.queryByText(BLANK_ERROR)).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    // The message is rendered in the form (next to the fields), not raised as a toast.
    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();
    expect(onSave).not.toHaveBeenCalled();
  });

  it('clears the error and saves once any one of the four fields is filled', async () => {
    const onSave = renderContacts();
    await userEvent.click(screen.getByRole('button', { name: /Add contact/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();

    // Organization alone is enough — no single field is required, only "at least one of them".
    await userEvent.type(screen.getByLabelText('Organization'), 'Example Nation');
    expect(screen.queryByText(BLANK_ERROR)).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(onSave).toHaveBeenCalledWith([
      expect.objectContaining({ organization: 'Example Nation' }),
    ]);
  });

  it('does not carry the error over to the next contact the user opens', async () => {
    renderContacts();
    await userEvent.click(screen.getByRole('button', { name: /Add contact/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();

    // ConfirmProvider keeps its own (hidden) modal mounted, which also has a Cancel — take the
    // form's, which is the first in document order.
    await userEvent.click(screen.getAllByRole('button', { name: 'Cancel' })[0]);
    await userEvent.click(screen.getByRole('button', { name: /Add contact/ }));

    expect(screen.queryByText(BLANK_ERROR)).toBeNull();
  });
});

describe('Contacts — edit form layout', () => {
  const centre = (sel: string): number => {
    const r = document.querySelector(sel)!.getBoundingClientRect();
    return Math.round(r.top + r.height / 2);
  };

  it('breaks the row before Organization and sits both checkboxes on its line', async () => {
    await page.viewport(1500, 800);
    renderContacts();
    await userEvent.click(screen.getByRole('button', { name: /add contact/i }));

    const first = document.querySelector('#contact-first')!.getBoundingClientRect();
    const role = document.querySelector('#contact-role')!.getBoundingClientRect();
    const org = document.querySelector('#contact-org')!.getBoundingClientRect();

    // Who the contact is on one row; what has happened with them on the next.
    expect(Math.round(role.top)).toBe(Math.round(first.top));
    expect(org.top).toBeGreaterThan(first.bottom);

    // A checkbox carries no label above it, so without an offset it floats level with its
    // neighbours' labels rather than their inputs.
    const box = (id: string) =>
      document.querySelector(id)!.closest('.cds--checkbox-wrapper')!.getBoundingClientRect();
    const contacted = box('#contact-contacted');
    const attending = box('#contact-attending');
    expect(Math.round(contacted.top + contacted.height / 2)).toBe(centre('#contact-org'));
    expect(Math.round(attending.top + attending.height / 2)).toBe(centre('#contact-org'));
  });
});
