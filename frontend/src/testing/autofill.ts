/**
 * The fields on screen that a browser could autofill, and the ones it still can.
 *
 * Checklist fields keep a stable `id` across records, so without `autocomplete="off"` the browser
 * treats the next stratum / plot / feature's field as one it has seen before and offers what was
 * typed last time — and accepting a single suggestion cascades into the rest of the group it
 * infers. See {@link import('@/utils/autofill').NO_AUTOFILL}.
 *
 * Checkboxes, radios, hidden and file inputs are not autofilled, and flatpickr builds the calendar
 * popup's own month/year spinners itself — they take no props from us and hold no form data.
 */
const autofillable = (): HTMLElement[] =>
  Array.from(document.querySelectorAll<HTMLElement>('input, textarea, select')).filter((el) => {
    if (['checkbox', 'radio', 'hidden', 'file'].includes((el as HTMLInputElement).type)) {
      return false;
    }
    return el.closest('.flatpickr-calendar') === null;
  });

/** How many fields on screen the browser could autofill — guards against a vacuous pass. */
export const autofillableCount = (): number => autofillable().length;

/** Fields still open to autofill, named so a failure says which. Empty when every field is off. */
export const stillAutofillable = (): string[] =>
  autofillable()
    .filter((el) => el.getAttribute('autocomplete') !== 'off')
    .map((el) => `${el.tagName}#${el.id || '(no id)'}`);
