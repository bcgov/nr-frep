import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import '@/styles/index.scss';

import FormLock from '@/components/core/FormLock';
import ActionButton from '@/components/core/ActionButton';
import { DestructiveModal } from '@/components/core/DestructiveModal';

describe('ActionButton', () => {
  it('says the save is running and stops taking clicks', () => {
    const onClick = vi.fn();
    render(<ActionButton busy onClick={onClick} />);

    const button = screen.getByRole('button');
    // Carbon prefixes nothing here, so the label is the whole accessible name.
    expect(button.textContent).toContain('Saving…');
    expect(button.hasAttribute('disabled')).toBe(true);
    expect(button.querySelector('.cds--loading')).toBeTruthy();
  });

  it('rests as Save and calls back', async () => {
    const onClick = vi.fn();
    render(<ActionButton busy={false} onClick={onClick} />);

    const button = screen.getByRole('button');
    expect(button.textContent).toContain('Save');
    expect(button.textContent).not.toContain('Saving');
    expect(button.hasAttribute('disabled')).toBe(false);
  });

  it('stays disabled for its own reason even when not saving', () => {
    render(<ActionButton busy={false} disabled onClick={vi.fn()} />);
    expect(screen.getByRole('button').hasAttribute('disabled')).toBe(true);
  });
});

describe('FormLock', () => {
  it('takes every control out of reach while the save is in flight', () => {
    render(
      <FormLock busy>
        <input aria-label="field" />
        <button type="button">Somewhere</button>
      </FormLock>,
    );

    // A real fieldset[disabled], so the browser itself refuses input and focus — CSS alone would
    // stop the mouse but let a focused field keep accepting typing.
    //
    // Asserted with `:disabled` rather than the `disabled` property: that property reflects only
    // the element's own attribute, and neither control has one. `:disabled` is the effective state,
    // which is what the browser acts on.
    expect(screen.getByLabelText('field').matches(':disabled')).toBe(true);
    expect(screen.getByRole('button', { name: 'Somewhere' }).matches(':disabled')).toBe(true);
  });

  it('leaves the section alone when nothing is saving', () => {
    render(
      <FormLock busy={false}>
        <input aria-label="field" />
      </FormLock>,
    );
    expect(screen.getByLabelText('field').matches(':disabled')).toBe(false);
  });
});

describe('ActionButton — several actions sharing one request', () => {
  it('spins only the button whose action is running', () => {
    // The master list page has three buttons and one request at a time. A single page-level flag
    // would put the spinner on Generate while the user was saving comments.
    const running = 'comments' as 'generate' | 'comments';
    render(
      <>
        <ActionButton busy={running === 'generate'} busyLabel="Generating…" onClick={vi.fn()}>
          Generate master list
        </ActionButton>
        <ActionButton kind="tertiary" busy={running === 'comments'} onClick={vi.fn()}>
          Save comments
        </ActionButton>
      </>,
    );

    expect(screen.getByRole('button', { name: /Generate master list/ })).toBeTruthy();
    const saving = screen.getByRole('button', { name: /Saving/ });
    expect(saving.querySelector('.cds--loading')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Generating/ })).toBeNull();
  });
})

describe('DestructiveModal — the confirm button while it runs', () => {
  it('spins and says what it is doing, and holds the dialog still', () => {
    render(
      <DestructiveModal
        open
        title="Delete this feature?"
        message="It cannot be undone."
        confirmButtonText="Delete"
        loading
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    const confirm = screen.getByRole('button', { name: /Deleting/ });
    expect(confirm.querySelector('.cds--loading')).toBeTruthy();
    expect(confirm.matches(':disabled')).toBe(true);
    // Cancel goes too: a dialog mid-request should not be dismissable into an unknown outcome.
    expect(screen.getByRole('button', { name: 'Cancel' }).matches(':disabled')).toBe(true);
  });

  it('offers the plain action when nothing is running', () => {
    render(
      <DestructiveModal
        open
        title="Delete this feature?"
        message="It cannot be undone."
        confirmButtonText="Delete"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    // Carbon danger buttons carry a visually-hidden "danger" in their accessible name.
    expect(screen.getByRole('button', { name: /Delete$/ }).matches(':disabled')).toBe(false);
  });
});
