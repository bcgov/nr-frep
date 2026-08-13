import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RipNotesView from './RipNotesView';

import API from '@/services/APIs';

vi.mock('@/services/APIs', () => ({
  default: {
    protocolChecklist: {
      getNotes: vi.fn(),
      saveNotes: vi.fn(),
    },
  },
}));

const { display } = vi.hoisted(() => ({ display: vi.fn() }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

const api = API.protocolChecklist as unknown as {
  getNotes: ReturnType<typeof vi.fn>;
  saveNotes: ReturnType<typeof vi.fn>;
};

const BLANK_ERROR = 'Enter a note before saving.';

const renderView = () =>
  render(<RipNotesView protocol="bio" checklistId="1001" canEdit submitted={false} />);

const beginEdit = async () => {
  await userEvent.click(await screen.findByRole('button', { name: /Edit/ }));
  await screen.findByRole('textbox', { name: /Notes/ });
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('RipNotesView — empty note guard', () => {
  it('blocks a save when the box is empty and no note was stored', async () => {
    api.getNotes.mockResolvedValue({ checklistId: '1001', noteDescription: '' });
    renderView();
    await beginEdit();

    // Nothing typed, but the user has not asked to save yet — the field must be quiet.
    expect(screen.queryByText(BLANK_ERROR)).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();
    // The point of the guard: no round trip, so nothing writes NULL and bumps revision_count.
    expect(api.saveNotes).not.toHaveBeenCalled();
  });

  it('saves once text is entered, and clears the error as soon as it is', async () => {
    api.getNotes.mockResolvedValue({ checklistId: '1001', noteDescription: '' });
    api.saveNotes.mockResolvedValue({ checklistId: '1001', noteDescription: 'Site visited.' });
    renderView();
    await beginEdit();
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();

    await userEvent.type(screen.getByRole('textbox', { name: /Notes/ }), 'Site visited.');
    expect(screen.queryByText(BLANK_ERROR)).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(api.saveNotes).toHaveBeenCalledTimes(1));
    expect(api.saveNotes.mock.calls[0][2]).toMatchObject({ noteDescription: 'Site visited.' });
  });

  it('still allows an existing note to be cleared', async () => {
    // Emptying a note that exists is a real edit, not a no-op — the guard must not block it.
    api.getNotes.mockResolvedValue({ checklistId: '1001', noteDescription: 'Old note.' });
    api.saveNotes.mockResolvedValue({ checklistId: '1001', noteDescription: '' });
    renderView();
    await beginEdit();

    await userEvent.clear(screen.getByRole('textbox', { name: /Notes/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(api.saveNotes).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(BLANK_ERROR)).toBeNull();
  });

  it('treats whitespace as empty', async () => {
    api.getNotes.mockResolvedValue({ checklistId: '1001', noteDescription: '' });
    renderView();
    await beginEdit();

    await userEvent.type(screen.getByRole('textbox', { name: /Notes/ }), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(BLANK_ERROR)).toBeTruthy();
    expect(api.saveNotes).not.toHaveBeenCalled();
  });
});
