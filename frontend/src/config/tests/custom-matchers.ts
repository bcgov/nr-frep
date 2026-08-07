import { expect } from 'vitest';

expect.extend({
  toContainText(received: HTMLElement, expected: string) {
    // Vitest's matcher contract requires a definite boolean; textContent is nullable, so an element
    // with no text is a plain "does not contain" rather than an undefined result.
    const pass = received.textContent?.includes(expected) ?? false;
    return {
      pass,
      message: () =>
        `expected element ${pass ? 'not ' : ''}to contain text "${expected}", but got "${received.textContent}"`,
    };
  },
});
