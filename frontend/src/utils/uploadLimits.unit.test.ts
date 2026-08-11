import { describe, expect, it } from 'vitest';

import { MAX_UPLOAD_BYTES, MAX_UPLOAD_MB, dataUrlByteLength, formatMb } from './uploadLimits';

describe('uploadLimits', () => {
  it('exposes the byte limit derived from the MB limit', () => {
    expect(MAX_UPLOAD_MB).toBe(15);
    expect(MAX_UPLOAD_BYTES).toBe(15 * 1024 * 1024);
  });

  it('formats sizes to one decimal place', () => {
    expect(formatMb(15 * 1024 * 1024)).toBe('15.0');
    expect(formatMb(1_572_864)).toBe('1.5');
    expect(formatMb(0)).toBe('0.0');
  });
});

describe('dataUrlByteLength', () => {
  // The decoded length, not the string length — base64 inflates by ~33%, so measuring the string
  // would reject files a third smaller than the real limit.
  it('returns the decoded byte length, not the base64 length', () => {
    // "hello world" -> 11 bytes, 16 base64 chars.
    const url = 'data:text/plain;base64,aGVsbG8gd29ybGQ=';
    expect(dataUrlByteLength(url)).toBe(11);
    expect(url.length).toBeGreaterThan(11);
  });

  it('accounts for both padding lengths', () => {
    expect(dataUrlByteLength('data:x;base64,QQ==')).toBe(1); // 'A'
    expect(dataUrlByteLength('data:x;base64,QUI=')).toBe(2); // 'AB'
    expect(dataUrlByteLength('data:x;base64,QUJD')).toBe(3); // 'ABC'
  });

  it('handles a bare base64 payload with no data: prefix', () => {
    expect(dataUrlByteLength('QUJD')).toBe(3);
  });

  // Picture.code is optional, and a saved photo carries no code at all — those must read as 0
  // rather than throwing, or the upload guard would blow up on an ordinary payload.
  it('treats missing or empty values as zero', () => {
    expect(dataUrlByteLength(undefined)).toBe(0);
    expect(dataUrlByteLength(null)).toBe(0);
    expect(dataUrlByteLength('')).toBe(0);
  });

  it('is under the limit for a typical downscaled photo and over for an un-resized original', () => {
    const base64Chars = (bytes: number) => 'A'.repeat(Math.ceil(bytes / 3) * 4);
    expect(dataUrlByteLength(`data:image/jpeg;base64,${base64Chars(400_000)}`)).toBeLessThan(
      MAX_UPLOAD_BYTES,
    );
    expect(
      dataUrlByteLength(`data:image/jpeg;base64,${base64Chars(20 * 1024 * 1024)}`),
    ).toBeGreaterThan(MAX_UPLOAD_BYTES);
  });
});
