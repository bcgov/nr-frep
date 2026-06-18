import { Checkbox, Select, SelectItem, TextArea, TextInput } from '@carbon/react';

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
