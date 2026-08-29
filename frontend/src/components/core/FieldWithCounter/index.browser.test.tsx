import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { TextAreaField } from '@/pages/ChrChecklist/fields';

import FieldWithCounter from './index';

/** Mirrors how the checklist forms drive the field: controlled value, byte limit, no maxLength. */
const Harness = ({ limit = 10, initial = '' }: { limit?: number; initial?: string }) => {
  const [value, setValue] = useState(initial);
  return (
    <TextAreaField
      id="probe"
      labelText="Comments"
      value={value}
      onChange={setValue}
      limit={limit}
    />
  );
};

describe('FieldWithCounter', () => {
  it('shows used / limit', () => {
    render(
      <FieldWithCounter used={3} limit={50}>
        <input aria-label="x" />
      </FieldWithCounter>,
    );
    expect(screen.getByText('3 / 50')).toBeTruthy();
  });

  it('marks the count as over once it exceeds the limit', () => {
    render(
      <FieldWithCounter used={51} limit={50}>
        <input aria-label="x" />
      </FieldWithCounter>,
    );
    expect(screen.getByText('51 / 50').className).toContain('frep-field__counter--over');
  });
});

describe('the byte counter on text areas', () => {
  it('counts bytes, not characters', async () => {
    // The whole point of the counter: an em dash is one character but three UTF-8 bytes, and it is
    // bytes the VARCHAR2(n BYTE) column measures.
    render(<Harness limit={10} initial="a—b" />);

    expect(screen.getByText('5 / 10')).toBeTruthy();
  });

  it('updates live as the user types', async () => {
    render(<Harness limit={10} />);
    await userEvent.type(screen.getByLabelText('Comments'), 'abc');

    expect(screen.getByText('3 / 10')).toBeTruthy();
  });

  it('lets the value exceed the limit rather than truncating it', async () => {
    // A limit-carrying field must NOT also have maxLength: truncation would silently drop the tail
    // of pasted text. Going over is allowed, shown as over, and blocked at Save by the caller.
    render(<Harness limit={5} />);
    const field = screen.getByLabelText('Comments') as HTMLTextAreaElement;

    expect(field.getAttribute('maxlength')).toBeNull();

    await userEvent.type(field, 'abcdefgh');
    expect(field.value).toBe('abcdefgh');
    expect(screen.getByText('8 / 5').className).toContain('frep-field__counter--over');
  });
});

// Geometry, so the real stylesheet has to be loaded — a browser spec loads none by default, and
// without it every measurement reports the unstyled layout.
import '@/styles/index.scss';

describe('FieldWithCounter — placement', () => {
  it('sits the count at the end of the label’s line, not below the field', () => {
    render(<Harness limit={2000} />);

    const field = document.querySelector('.frep-field')!;
    const label = field.querySelector('.cds--label')!.getBoundingClientRect();
    const counter = field.querySelector('.frep-field__counter')!.getBoundingClientRect();
    const input = document.querySelector('textarea')!.getBoundingClientRect();

    // Same line as the label…
    expect(Math.round(counter.top)).toBe(Math.round(label.top));
    // …at the far end of it, clear of the label text…
    expect(counter.left).toBeGreaterThan(label.left);
    // …and above the field, not stranded in the gap beneath it.
    expect(counter.bottom).toBeLessThanOrEqual(input.top);
  });
});
