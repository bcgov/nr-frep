/**
 * Best-effort human-readable message from a thrown API error. Backend errors (ResponseStatusException
 * etc.) carry the useful text in the JSON response body's `message` field (server.error.include-message
 * is enabled), which the ApiError exposes as `.body` — its top-level `.message` is just the status
 * phrase (e.g. "Bad Request"). Prefer the body message, then the Error message, then a fallback.
 */
export function apiErrorMessage(err: unknown, fallback = 'Unknown error'): string {
  const body = (err as { body?: unknown })?.body;
  if (body && typeof body === 'object' && 'message' in body) {
    const message = (body as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return fallback;
}
