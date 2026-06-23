package ca.bc.gov.nrs.frep.service.v1.report;

import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/**
 * Bounds the number of concurrent streaming CSV exports. Each streaming export holds a database
 * connection open for the full duration of the download, so an unbounded number of them could exhaust
 * the Hikari pool and starve normal traffic. Callers {@link #tryAcquire()} a slot before starting the
 * stream (so an over-limit request can be rejected with 429 while the response is still uncommitted)
 * and {@link #release()} it when the stream finishes.
 */
@Component
public class ExportSlotLimiter {

  static final int MAX_CONCURRENT_EXPORTS = 3;

  private final Semaphore permits = new Semaphore(MAX_CONCURRENT_EXPORTS, true);

  /** Tries to take an export slot without blocking. Returns {@code false} if all slots are in use. */
  public boolean tryAcquire() {
    return permits.tryAcquire();
  }

  /** Returns a previously-acquired slot. Always call this from a {@code finally} after a successful acquire. */
  public void release() {
    permits.release();
  }
}
