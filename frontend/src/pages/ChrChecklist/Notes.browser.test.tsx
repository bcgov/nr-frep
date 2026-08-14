import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import Notes from './Notes';

import type { CheckList } from '@/types/chrChecklist';

const BLANK_ERROR = 'Enter a note before saving.';

const renderNotes = (commentaires?: string, onSave = vi.fn().mockResolvedValue(true)) => {
  render(
    <Notes
      value={{ checklistID: '1001', commentaires } as CheckList}
      onSave={onSave}
      readOnly={false}
      busy={false}
    />,
  );
  return onSave;
};

const beginEdit = async () => {
  await userEvent.click(screen.getByRole('button', { name: /Edit/ }));
  await screen.findByRole('textbox', { name: /Notes/ });
};

describe('Notes — empty note guard', () => {
  it('blocks a save when the box is empty and no note was stored', async () => {
    const onSave = renderNotes(undefined);
    await beginEdit();

    // Nothing typed, but the user has not asked to save yet — the field must be quiet.
    expect(screen.queryByText(BLANK_ERROR)).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();
    expect(onSave).not.toHaveBeenCalled();
  });

  it('saves once text is entered, and clears the error as soon as it is', async () => {
    const onSave = renderNotes(undefined);
    await beginEdit();
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();

    await userEvent.type(screen.getByRole('textbox', { name: /Notes/ }), 'Block reviewed.');
    expect(screen.queryByText(BLANK_ERROR)).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ commentaires: 'Block reviewed.' }));
  });

  it('still allows an existing note to be cleared', async () => {
    // Emptying a note that exists is a real edit, not a no-op — the guard must not block it.
    const onSave = renderNotes('Old note.');
    await beginEdit();

    await userEvent.clear(screen.getByRole('textbox', { name: /Notes/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ commentaires: '' }));
    expect(screen.queryByText(BLANK_ERROR)).toBeNull();
  });

  it('treats whitespace as empty', async () => {
    const onSave = renderNotes(undefined);
    await beginEdit();

    await userEvent.type(screen.getByRole('textbox', { name: /Notes/ }), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();
    expect(onSave).not.toHaveBeenCalled();
  });
});
