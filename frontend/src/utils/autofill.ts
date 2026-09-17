/**
 * Turns the browser's form autofill off for a field.
 *
 * Checklist fields keep a stable `id` across strata, plots and features, so the browser treats the
 * next record's field as the same one it saw before and offers what was typed last time — and
 * accepting a single suggestion cascades into the rest of the group it has inferred. These are
 * per-record evaluation values, so a repeat of the previous record is always wrong.
 *
 * Most Carbon inputs take `autoComplete="off"` directly. `DatePickerInput` is the exception: it
 * spreads unknown props onto its `<input>` at runtime but does not declare them, so passing the
 * attribute literally fails to type-check. Spreading this constant does not, because TypeScript
 * skips excess-property checking on a JSX spread of a non-literal value.
 */
export const NO_AUTOFILL = { autoComplete: 'off' } as { autoComplete?: string };
