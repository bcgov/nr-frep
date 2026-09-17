import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

// The dialog's own layout is under test, so it needs the real stylesheets.
import '@/styles/index.scss';

import { DestructiveModal } from './index';

const open = () =>
  render(
    <DestructiveModal
      open
      title="Are you sure you want to delete this feature?"
      message="Feature 1 will be permanently deleted from this checklist."
      onConfirm={vi.fn()}
      onCancel={vi.fn()}
    />,
  );

describe('DestructiveModal', () => {
  it('closes on the bottom right, with Delete last', () => {
    open();
    const actions = document.querySelector('.destructive-modal__actions') as HTMLElement;
    const buttons = Array.from(actions.querySelectorAll('button'));

    // Carbon prefixes a danger button's text with a visually-hidden "danger" for screen readers.
    expect(buttons.map((b) => b.textContent?.replace(/^danger/, '').trim())).toEqual([
      'Cancel',
      'Delete',
    ]);
    expect(getComputedStyle(actions).justifyContent).toBe('flex-end');
  });

  it('gives Cancel the outlined treatment so it does not compete with Delete', () => {
    // `secondary` renders a solid dark button — two heavy buttons side by side, neither obviously
    // the safe one.
    open();

    expect(screen.getByRole('button', { name: 'Cancel' }).className).toMatch(/--btn--tertiary/);
    expect(screen.getByRole('button', { name: /Delete/ }).className).toMatch(/--btn--danger/);
  });
});
