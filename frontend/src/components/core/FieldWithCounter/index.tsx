import type { FC, ReactNode } from 'react';

/**
 * Wraps a length-limited input with a live "used / limit" counter at its bottom-right.
 *
 * <p>The caller supplies `used`, and every caller passes a **byte** count (`byteLength` from
 * `@/utils/textLimits`). Every backing column is declared byte-semantic — `VARCHAR2(n BYTE)`
 * throughout nr-mof-db `scripts/THE/TABLES/` — so bytes are the only unit that predicts whether the
 * save will succeed. A character count would read `50 / 50` in black on a value that the database
 * then rejects, which is precisely the confusion this counter exists to remove.
 *
 * <p>Purely presentational: it never truncates and never sets `invalid` — the caller owns both,
 * so the counter can never disagree with the error text beside it.
 */
const FieldWithCounter: FC<{ used: number; limit: number; children: ReactNode }> = ({
  used,
  limit,
  children,
}) => (
  // aria-live="polite" so a screen reader hears the count update without it interrupting typing.
  <div className="frep-field">
    {children}
    <div className="frep-field__footer">
      <span
        className={
          used > limit ? 'frep-field__counter frep-field__counter--over' : 'frep-field__counter'
        }
        aria-live="polite"
      >
        {used} / {limit}
      </span>
    </div>
  </div>
);

export default FieldWithCounter;
