import {
  Checkbox,
  DatePicker,
  DatePickerInput,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';

import FieldWithCounter from '@/components/core/FieldWithCounter';

import type { CodeOption } from '@/pages/ChrChecklist/codeLists';
import type { Indicator } from '@/types/chrChecklist';
import type { FC, ReactNode } from 'react';

import { byteLength, overLimitError } from '@/utils/textLimits';

/** Carbon checkbox bound to a backend "true"/"false" string indicator. */
export const IndicatorCheckbox: FC<{
  id: string;
  labelText: string;
  value: Indicator | undefined;
  onToggle: (next: Indicator) => void;
  disabled?: boolean;
}> = ({ id, labelText, value, onToggle, disabled }) => (
  <Checkbox
    id={id}
    labelText={labelText}
    checked={value === 'true'}
    disabled={disabled}
    onChange={(_evt, { checked }) => onToggle(checked ? 'true' : 'false')}
  />
);

export const TextField: FC<{
  id: string;
  labelText: ReactNode;
  value: string | undefined;
  onChange: (next: string) => void;
  disabled?: boolean;
  placeholder?: string;
  helperText?: string;
  invalid?: boolean;
  invalidText?: string;
  maxLength?: number;
}> = ({
  id,
  labelText,
  value,
  onChange,
  disabled,
  placeholder,
  helperText,
  invalid,
  invalidText,
  maxLength,
}) => (
  <TextInput
    id={id}
    labelText={labelText}
    value={value ?? ''}
    disabled={disabled}
    placeholder={placeholder}
    helperText={helperText}
    invalid={invalid}
    invalidText={invalidText}
    maxLength={maxLength}
    onChange={(e) => onChange(e.target.value)}
  />
);

/**
 * Calendar date field bound to a backend {@code YYYY-MM-DD} string. Drop-in replacement for a
 * {@link TextField} that holds a date — same prop shape ({@code value}/{@code onChange(next)}), but
 * renders a Carbon single-mode {@code DatePicker}. Fills its container via the global date-picker
 * override in {@code styles/_overrides.scss}.
 */
export const DateField: FC<{
  id: string;
  labelText: ReactNode;
  value: string | undefined;
  onChange: (next: string) => void;
  disabled?: boolean;
  placeholder?: string;
  invalid?: boolean;
  invalidText?: string;
}> = ({
  id,
  labelText,
  value,
  onChange,
  disabled,
  placeholder = 'YYYY-MM-DD',
  invalid,
  invalidText,
}) => (
  <DatePicker
    className="frep-date-picker"
    datePickerType="single"
    dateFormat="Y-m-d"
    // Pass a plain string (or undefined), never an empty array — Carbon's `setDate([])` re-sync
    // effect on an empty array drives an update loop when the picker mounts mid-render.
    value={value || undefined}
    // Only propagate a real change. flatpickr fires onChange on mount/re-sync; the guard keeps that
    // spurious call from feeding a parent draft-sync effect into an update loop.
    onChange={(dates: Date[]) => {
      const next = dates[0] ? dates[0].toISOString().slice(0, 10) : '';
      if (next !== (value ?? '')) {
        onChange(next);
      }
    }}
  >
    <DatePickerInput
      id={id}
      labelText={labelText}
      placeholder={placeholder}
      disabled={disabled}
      invalid={invalid}
      invalidText={invalidText}
    />
  </DatePicker>
);

export const TextAreaField: FC<{
  id: string;
  labelText: ReactNode;
  value: string | undefined;
  onChange: (next: string) => void;
  disabled?: boolean;
  rows?: number;
  maxLength?: number;
  /**
   * Byte limit of the backing column. Shows a live "used / limit" counter beside the label and
   * marks the field invalid once exceeded — it does NOT truncate. Deliberately not Carbon's
   * `enableCounter`/`maxCount`, which count characters and pair with `maxLength` truncation: the
   * columns are byte-limited (see textLimits.ts), and silently dropping the tail of pasted text is
   * how an evaluator loses a paragraph without noticing. Blocking Save is the caller's job.
   */
  limit?: number;
  invalid?: boolean;
  invalidText?: string;
}> = ({
  id,
  labelText,
  value,
  onChange,
  disabled,
  rows = 3,
  maxLength,
  limit,
  invalid,
  invalidText,
}) => {
  const used = limit === undefined ? 0 : byteLength(value);
  const over = limit !== undefined && used > limit;
  const field = (
    <TextArea
      id={id}
      labelText={labelText}
      value={value ?? ''}
      rows={rows}
      disabled={disabled}
      maxLength={maxLength}
      invalid={invalid || over}
      invalidText={over ? overLimitError(value, limit) : invalidText}
      onChange={(e) => onChange(e.target.value)}
    />
  );
  if (limit === undefined) {
    return field;
  }
  return (
    <FieldWithCounter used={used} limit={limit}>
      {field}
    </FieldWithCounter>
  );
};

/** Carbon select backed by a CodeOption[] list. */
export const CodeSelect: FC<{
  id: string;
  labelText: ReactNode;
  value: string | undefined;
  options: CodeOption[];
  onChange: (next: string) => void;
  disabled?: boolean;
  includeBlank?: boolean;
  invalid?: boolean;
  invalidText?: string;
  /** Keep the label for screen readers but take it out of the layout — for a select in a table
   *  cell, where the column header already names the field. */
  hideLabel?: boolean;
}> = ({
  id,
  labelText,
  value,
  options,
  onChange,
  disabled,
  includeBlank,
  invalid,
  invalidText,
  hideLabel,
}) => (
  <Select
    id={id}
    labelText={labelText}
    hideLabel={hideLabel}
    value={value ?? ''}
    disabled={disabled}
    invalid={invalid}
    invalidText={invalidText}
    onChange={(e) => onChange(e.target.value)}
  >
    {includeBlank && <SelectItem value="" text="—" />}
    {options.map((opt) => (
      <SelectItem key={`${id}-${opt.code}`} value={opt.code} text={opt.label} />
    ))}
  </Select>
);
