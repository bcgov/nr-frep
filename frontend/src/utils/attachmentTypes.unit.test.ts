import { describe, expect, it } from 'vitest';

import {
  isAllowedAttachmentExtension,
  isPreviewableFile,
  isPreviewableRecord,
  parseAttachmentTypes,
} from './attachmentTypes';

describe('parseAttachmentTypes', () => {
  it('reads a configured list', () => {
    expect(parseAttachmentTypes('PDF,PNG,WEBP')).toEqual(['pdf', 'png', 'webp']);
  });

  it('returns nothing when unset — there is no in-code default', () => {
    // The GitHub variable is the single source of truth. Deployments make it a required template
    // parameter, so an empty list only occurs in local dev — where nothing may be attached, since
    // the client enforces the list rather than deferring to the server.
    for (const value of [undefined, '', '   ', ',,, ,']) {
      expect(parseAttachmentTypes(value)).toEqual([]);
    }
  });

  it('normalises case and whitespace, drops duplicates, and sorts', () => {
    expect(parseAttachmentTypes(' pdf , PNG,  pdf ,png ')).toEqual(['pdf', 'png']);
    expect(parseAttachmentTypes('webp,pdf,png')).toEqual(['pdf', 'png', 'webp']);
  });
});

describe('isAllowedAttachmentExtension', () => {
  // Bound to the module-level list, which the test env populates from VITE_ATTACHMENT_TYPES in
  // .env — so these assert the configured behaviour rather than a hardcoded default.
  it('accepts a configured extension, case-insensitively', () => {
    expect(isAllowedAttachmentExtension('pdf')).toBe(true);
    expect(isAllowedAttachmentExtension('PDF')).toBe(true);
  });

  it('rejects anything not on the list', () => {
    expect(isAllowedAttachmentExtension('exe')).toBe(false);
    expect(isAllowedAttachmentExtension('')).toBe(false);
  });
});

describe('previewability', () => {
  // Separate from the allow-list: CHR photos and Biodiversity attachments accept exactly the same
  // types, and this only decides which of them can show a picture instead of a type placeholder.
  it('is not an allow-list — everything configured still uploads', () => {
    expect(isAllowedAttachmentExtension('pdf')).toBe(true);
    expect(isPreviewableFile('permit.pdf')).toBe(false);
  });

  it('recognises the formats a browser renders', () => {
    for (const name of ['a.jpg', 'a.JPG', 'a.jpeg', 'a.png', 'a.gif', 'a.bmp', 'a.webp']) {
      expect(isPreviewableFile(name)).toBe(true);
    }
  });

  it('excludes TIFF, which uploads fine but can never render', () => {
    // Mirrors isImage in RipAttachmentsView: no mainstream browser decodes TIFF, so fetching one to
    // build a thumbnail downloads the largest file on the list to show a placeholder anyway.
    expect(isAllowedAttachmentExtension('tif')).toBe(true);
    expect(isAllowedAttachmentExtension('tiff')).toBe(true);
    expect(isPreviewableFile('scan.tif')).toBe(false);
    expect(isPreviewableFile('scan.tiff')).toBe(false);
  });

  it('treats a file with no extension as not previewable', () => {
    expect(isPreviewableFile('README')).toBe(false);
    expect(isPreviewableFile(undefined)).toBe(false);
  });
});

describe('isPreviewableRecord', () => {
  // Tolerant by design: a stored photo may carry a media type, a legacy "image/<code>", a file name,
  // a self-describing data URL, or only some of those. Missing a thumbnail because one field was
  // absent is a bug we actually hit while building this.
  it('reads a self-describing data URL first', () => {
    expect(isPreviewableRecord({ code: 'data:image/png;base64,XYZ' })).toBe(true);
    expect(isPreviewableRecord({ code: 'data:application/pdf;base64,XYZ' })).toBe(false);
    expect(isPreviewableRecord({ code: 'data:image/tiff;base64,XYZ' })).toBe(false);
  });

  it('uses the real media type when present', () => {
    expect(isPreviewableRecord({ mediaType: 'image/jpeg' })).toBe(true);
    expect(isPreviewableRecord({ mediaType: 'application/pdf' })).toBe(false);
  });

  it('accepts the legacy "image/<code>" shape', () => {
    expect(isPreviewableRecord({ mimeTypeCode: 'image/jpg' })).toBe(true);
    // A legacy value can be "image/pdf" for a non-image saved before mediaType existed; the file
    // name settles it.
    expect(isPreviewableRecord({ mimeTypeCode: 'image/pdf', fileName: 'permit.pdf' })).toBe(false);
  });

  it('falls back to the file name, and to "not previewable" when nothing identifies it', () => {
    expect(isPreviewableRecord({ fileName: 'site.jpg' })).toBe(true);
    expect(isPreviewableRecord({ fileName: 'scan.tiff' })).toBe(false);
    expect(isPreviewableRecord({})).toBe(false);
  });
});
