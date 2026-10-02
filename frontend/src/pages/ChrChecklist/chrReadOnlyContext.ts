import { createContext } from 'react';

/**
 * Read-only display for a whole CHR form. Inside it, every field in `fields.tsx` renders as a label
 * over its value — the SLR read-only look (`protocol-checklist__field`) — instead of a disabled
 * control. Disabled Carbon inputs read as "unavailable", not "this is the answer": greyed text,
 * faded labels, and empty boxes where a value is blank.
 *
 * A context rather than a prop so a form opts in once at its root; `disabled` keeps its own meaning
 * (a control that is closed for now, in a form that is otherwise editable).
 */
export const ChrReadOnlyContext = createContext(false);
