import {
  Checkbox,
  DatePicker,
  DatePickerInput,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';

import type { CodeOption } from '@/pages/ChrChecklist/codeLists';
import type { Indicator } from '@/types/chrChecklist';
import type { FC } from 'react';

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
  labelText: string;
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
  labelText: string;
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
  labelText: string;
  value: string | undefined;
  onChange: (next: string) => void;
  disabled?: boolean;
  rows?: number;
}> = ({ id, labelText, value, onChange, disabled, rows = 3 }) => (
  <TextArea
    id={id}
    labelText={labelText}
    value={value ?? ''}
    rows={rows}
    disabled={disabled}
    onChange={(e) => onChange(e.target.value)}
  />
);

/** Carbon select backed by a CodeOption[] list. */
export const CodeSelect: FC<{
  id: string;
  labelText: string;
  value: string | undefined;
  options: CodeOption[];
  onChange: (next: string) => void;
  disabled?: boolean;
  includeBlank?: boolean;
}> = ({ id, labelText, value, options, onChange, disabled, includeBlank }) => (
  <Select
    id={id}
    labelText={labelText}
    value={value ?? ''}
    disabled={disabled}
    onChange={(e) => onChange(e.target.value)}
  >
    {includeBlank && <SelectItem value="" text="—" />}
    {options.map((opt) => (
      <SelectItem key={`${id}-${opt.code}`} value={opt.code} text={opt.label} />
    ))}
  </Select>
);
