import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { clearCodeListCache, primeCodeList, useCodeList } from './useCodeList';

import type { CodeOption } from '@/types/configuration';

const LIST: CodeOption[] = [{ code: 'ARCH', description: 'Archaeological Resource' }];

const setOnline = (online: boolean) =>
  Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: online });

const Probe = ({ fetcher }: { fetcher: () => Promise<CodeOption[]> }) => {
  const options = useCodeList('feature-class', fetcher);
  return <span data-testid="codes">{options.map((o) => o.code).join(',') || 'empty'}</span>;
};

describe('useCodeList', () => {
  beforeEach(() => {
    clearCodeListCache();
    setOnline(true);
  });
  afterEach(() => {
    clearCodeListCache();
    setOnline(true);
  });

  it('keeps the fetched list so it survives a page load', async () => {
    const fetcher = vi.fn().mockResolvedValue(LIST);
    render(<Probe fetcher={fetcher} />);

    await waitFor(() => expect(screen.getByTestId('codes').textContent).toBe('ARCH'));
    expect(window.localStorage.getItem('frep.codeList.feature-class')).toContain('ARCH');
  });

  it('serves the stored list offline without calling the API', async () => {
    // The field case: the checklist is device-local, so this was the only request still going out —
    // and with no network it left every dropdown empty.
    window.localStorage.setItem('frep.codeList.feature-class', JSON.stringify(LIST));
    setOnline(false);
    const fetcher = vi.fn();

    render(<Probe fetcher={fetcher} />);

    await waitFor(() => expect(screen.getByTestId('codes').textContent).toBe('ARCH'));
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('does not call the API offline even with nothing stored', async () => {
    setOnline(false);
    const fetcher = vi.fn();

    render(<Probe fetcher={fetcher} />);

    await waitFor(() => expect(screen.getByTestId('codes').textContent).toBe('empty'));
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('refetches when online so a changed table is picked up', async () => {
    window.localStorage.setItem('frep.codeList.feature-class', JSON.stringify(LIST));
    const fetcher = vi
      .fn()
      .mockResolvedValue([{ code: 'CMT', description: 'Culturally Modified Tree(s)' }]);

    render(<Probe fetcher={fetcher} />);

    await waitFor(() => expect(screen.getByTestId('codes').textContent).toBe('CMT'));
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('ignores a corrupted stored value and fetches instead', async () => {
    window.localStorage.setItem('frep.codeList.feature-class', 'not json');
    const fetcher = vi.fn().mockResolvedValue(LIST);

    render(<Probe fetcher={fetcher} />);

    await waitFor(() => expect(screen.getByTestId('codes').textContent).toBe('ARCH'));
  });
});

/**
 * Warming the cache before the connection goes away — what "take offline" does, so a checklist
 * carried into the field still has dropdowns to fill in.
 */
describe('primeCodeList', () => {
  beforeEach(() => clearCodeListCache());
  afterEach(() => clearCodeListCache());

  it('stores a list so a later offline mount can serve it', async () => {
    await primeCodeList('feature-class', vi.fn().mockResolvedValue(LIST));

    expect(window.localStorage.getItem('frep.codeList.feature-class')).toContain('ARCH');
  });

  it('never fails the operation it rides along with', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('Network request failed'));

    await expect(primeCodeList('feature-class', fetcher)).resolves.toBeUndefined();
    expect(window.localStorage.getItem('frep.codeList.feature-class')).toBeNull();
  });

  it('leaves an already-cached list alone', async () => {
    const fetcher = vi.fn().mockResolvedValue(LIST);
    await primeCodeList('feature-class', fetcher);
    await primeCodeList('feature-class', fetcher);

    expect(fetcher).toHaveBeenCalledTimes(1);
  });
});

/**
 * The background fill: the caller gets a promise it never has to wait on, and the lists land in
 * their own time. Nothing in the UI should block on it.
 */
describe('primeCodeList runs in the background', () => {
  beforeEach(() => clearCodeListCache());
  afterEach(() => clearCodeListCache());

  it('returns before the fetch settles, then fills the cache', async () => {
    let release: (value: CodeOption[]) => void = () => {};
    const pending = new Promise<CodeOption[]>((resolve) => {
      release = resolve;
    });

    const warming = primeCodeList('feature-class', () => pending);
    // Not settled yet: the caller carries on while this is still in flight.
    expect(window.localStorage.getItem('frep.codeList.feature-class')).toBeNull();

    release(LIST);
    await warming;

    expect(window.localStorage.getItem('frep.codeList.feature-class')).toContain('ARCH');
  });
});
