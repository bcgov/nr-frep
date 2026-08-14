import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ConfirmProvider } from '@/context/confirm/ConfirmProvider';

import Contacts from './Contacts';

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
