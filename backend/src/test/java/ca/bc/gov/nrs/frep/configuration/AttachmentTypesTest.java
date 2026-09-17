package ca.bc.gov.nrs.frep.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AttachmentTypesTest {

  /**
   * What ATTACHMENT_ALLOWED_TYPES is expected to be set to. Not a default — the code has none — but
   * the tests still need a realistic value, and pinning it here catches an extension being enabled
   * in the variable that the enum has no media type for.
   */
  private static final String SHIPPED_LIST =
      "BMP,CSV,DOC,DOCX,GIF,HTM,IFM,JPG,JPK,MDB,MDE,MP4,OBD,PDF,PNG,PPS,PPT,PPTX,RPT,RTF,TIF,"
          + "TIFF,TXT,WAV,WEBP,XLD,XLS,XLSX,XML,ZIP";

  @Test
  void readsTheConfiguredList() {
    AttachmentTypes types = new AttachmentTypes("PDF,PNG,WEBP");

    assertEquals(Set.of("PDF", "PNG", "WEBP"), types.allowed());
    assertTrue(types.isAllowed("pdf"), "the check must be case-insensitive");
    assertFalse(types.isAllowed("DOCX"), "a type left off the list must not be accepted");
  }

  @Test
  void refusesToStartWithoutTheVariable() {
    // No in-code default: the GitHub variable is the single source of truth. A blank value is a
    // deployment error, so fail loudly at startup rather than silently accepting nothing (every
    // upload rejected with an empty "Allowed types:" message) or silently accepting a stale list.
    for (String value : new String[] {null, "", "   "}) {
      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> new AttachmentTypes(value),
              "should have thrown for input '" + value + "'");
      assertTrue(ex.getMessage().contains("ATTACHMENT_ALLOWED_TYPES"), ex.getMessage());
    }
  }

  @Test
  void refusesAValueThatParsesToNothing() {
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> new AttachmentTypes(",,, ,"));
    assertTrue(ex.getMessage().contains("no usable entries"), ex.getMessage());
  }

  @Test
  void resolvesMediaTypesFromTheBuiltInTable() {
    AttachmentTypes types = new AttachmentTypes("PDF,WEBP,MP4");

    assertEquals("application/pdf", types.mediaType("PDF"));
    // WEBP is the case that motivated the map: it is previewable, so a missing media type produced
    // an undecodable data: URL rather than a thumbnail.
    assertEquals("image/webp", types.mediaType("webp"));
    assertEquals("video/mp4", types.mediaType("MP4"));
  }

  @Test
  void neverReturnsANullMediaType() {
    // The client builds a data: URL from this value; null would yield "data:null;base64,...".
    AttachmentTypes types = new AttachmentTypes("PDF,ZZZ");

    assertEquals("application/octet-stream", types.mediaType("ZZZ"),
        "an extension with no known media type downloads as a generic binary");
    assertEquals("application/octet-stream", types.mediaType("NOT_CONFIGURED"));
    assertEquals("application/octet-stream", types.mediaType(null));
  }

  @Test
  void normalisesCaseAndWhitespaceAndDuplicates() {
    AttachmentTypes types = new AttachmentTypes(" pdf , PNG,  pdf ,png ");

    assertEquals(Set.of("PDF", "PNG"), types.allowed());
  }

  @Test
  void displayIsAlphabeticalRegardlessOfHowTheVariableWasOrdered() {
    // This string is what the user sees in the "unsupported type" message, so it should not change
    // shape just because someone appended to the variable rather than inserting in order.
    assertEquals("PDF, PNG, WEBP", new AttachmentTypes("WEBP,PDF,PNG").display());
  }

  @Test
  void keepsAnOverLongExtensionRatherThanSilentlyDroppingIt() {
    // MIME_TYPE_CODE is VARCHAR2(10 BYTE). An over-long entry is logged as a warning and left in
    // place: dropping it would make the picker and the validator disagree with the configuration,
    // and the failure it causes (ORA-12899 at insert) is already reported as a clean 400.
    AttachmentTypes types = new AttachmentTypes("PDF,VERYLONGEXTENSION");

    assertTrue(types.isAllowed("VERYLONGEXTENSION"));
  }

  @Test
  void resolvesMediaTypesThroughTheEnumTable() {
    // The table is AttachmentType; AttachmentTypes only decides which of its constants are enabled.
    assertEquals("image/webp", AttachmentType.mediaTypeFor("WEBP"));
    assertEquals("image/jpeg", AttachmentType.mediaTypeFor("jpg"), "case-insensitive");
    assertEquals(AttachmentType.FALLBACK_MEDIA_TYPE, AttachmentType.mediaTypeFor("ZZZ"));
    assertEquals(AttachmentType.FALLBACK_MEDIA_TYPE, AttachmentType.mediaTypeFor(null));
  }

  @Test
  void everyEnumConstantCarriesAUsableMediaType() {
    // A constant with a blank or malformed media type would be served as-is and break the client's
    // data: URL; the enum is the one place this can be checked exhaustively.
    for (AttachmentType type : AttachmentType.values()) {
      assertFalse(type.mediaType().isBlank(), type + " has no media type");
      assertTrue(type.mediaType().contains("/"), type + " has a malformed media type");
      assertEquals(type.name(), type.extension(), "the constant name IS the extension");
    }
  }

  @Test
  void theListWeExpectToBeConfiguredIsCoveredByTheEnum() {
    // The value we expect ATTACHMENT_ALLOWED_TYPES to hold must resolve to specific media types —
    // an entry falling back to the generic binary type would download unlabelled.
    AttachmentTypes types = new AttachmentTypes(SHIPPED_LIST);
    for (String extension : types.allowed()) {
      assertTrue(AttachmentType.fromExtension(extension) != null,
          "configured type " + extension + " is missing from AttachmentType");
    }
  }
}
