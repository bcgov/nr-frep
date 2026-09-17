/**
 * Byte-accurate length measurement for the free-text fields backed by Oracle columns.
 *
 * <p>Those columns use **byte** semantics (`CHAR_USED = 'B'`), so on a UTF-8 database a character
 * is not worth one unit: an accented letter costs 2 and a curly quote, em-dash or ellipsis costs 3
 * — exactly what Word and Outlook substitute when text is pasted in. A plain `maxLength` would
 * therefore stop the obvious overflow and still let a 500-"character" entry full of smart quotes
 * fail at the database, which is the confusing case these helpers exist to prevent. Every count
 * here is bytes, measured the same way Oracle will.
 *
 * <p>The per-form limit tables live next to the forms that use them (e.g.
 * `pages/ChrChecklist/textLimits.ts`), each entry naming the column it came from.
 */

/** UTF-8 byte length — the unit the database actually enforces. */
export const byteLength = (value: string | undefined): number =>
  value ? new TextEncoder().encode(value).length : 0;

/**
 * Bytes as `multipart/form-data` will actually send them.
 *
 * <p>The multipart encoding algorithm rewrites every lone LF as CRLF, so a value with line breaks
 * arrives at the database **one byte longer per break** than {@link byteLength} reports. Verified,
 * not assumed: `FormData.append('d', 'a\nb')` serialises as `a\r\nb` — 18 bytes on the wire for a
 * 17-byte string. A 2000-byte description containing a single newline is therefore 2001 bytes in a
 * `VARCHAR2(2000 BYTE)` column, and fails with ORA-12899 after passing a counter that read 2000.
 *
 * <p>Use this for any field submitted as a multipart form part — the two upload descriptions. JSON
 * bodies are unaffected: a `\n` there decodes back to a single byte, so they keep {@link byteLength}.
 */
export const multipartByteLength = (value: string | undefined): number =>
  byteLength(value?.replace(/\r\n|\r|\n/g, "\r\n"));

/**
 * The over-limit message, or '' when the value fits. Phrased in the same units as the counter, and
 * without the word "characters" — the limit is bytes, so "480 characters" can legitimately be over
 * a 500 limit and telling the user otherwise would look like a bug.
 */
export const overLimitError = (
  value: string | undefined,
  limit: number,
  measure: (v: string | undefined) => number = byteLength,
): string => {
  const used = measure(value);
  return used > limit ? `Too long — the limit is ${limit} and this entry uses ${used}.` : '';
};

/** Adds over-limit errors for every field in `limits` that has one. Mutates `errors`. */
export const addTextLimitErrors = (
  errors: Record<string, string>,
  values: Record<string, unknown>,
  limits: Record<string, number>,
): void => {
  Object.entries(limits).forEach(([key, limit]) => {
    // A required-field error on the same key is the more useful one; don't overwrite it.
    if (errors[key]) return;
    const value = values[key];
    if (typeof value !== 'string') return;
    const error = overLimitError(value, limit);
    if (error) errors[key] = error;
  });
};
