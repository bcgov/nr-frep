package ca.bc.gov.nrs.frep.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** {@link AttachmentType} — the shared extension/media-type table. */
class AttachmentTypeTest {

  @ParameterizedTest
  @CsvSource({
      "permit.pdf,         PDF",
      "report.docx,        DOCX",
      "site.JPG,           JPG",
      "site.jpeg,          JPEG",
      "archive.tar.gz,     GZ",
      "'my report v2.pdf', PDF",
  })
  void extensionOfTakesTheLastExtensionUppercased(String fileName, String expected) {
    assertEquals(expected, AttachmentType.extensionOf(fileName));
  }

  @ParameterizedTest
  @CsvSource({"README", "'archive.'", "''"})
  void extensionOfIsEmptyWhenThereIsNoUsableExtension(String fileName) {
    // "" rather than null: callers compare it against the allow-list, and the one caller that needs
    // a null (the Biodiversity proc, whose column is nullable) translates it itself.
    assertEquals("", AttachmentType.extensionOf(fileName));
  }

  @Test
  void extensionOfHandlesANullFileName() {
    assertEquals("", AttachmentType.extensionOf(null));
  }

  @Test
  void everyExtensionFitsTheMimeTypeCodeColumn() {
    // Both MIME_TYPE_CODE columns (CHR_CHECKLIST_ATTACHMENT and BIODIVERSITY_CHKLST_ATTACH) are
    // VARCHAR2(10 BYTE) and store the extension verbatim. A constant that does not fit cannot be
    // uploaded at all — it fails with ORA-12899 at insert.
    for (AttachmentType type : AttachmentType.values()) {
      assertTrue(type.extension().length() <= 10,
          type.extension() + " is longer than the 10-character MIME_TYPE_CODE column");
    }
  }

  @ParameterizedTest
  @EnumSource(AttachmentType.class)
  void theConstantIsTheExtensionAndRoundTrips(AttachmentType type) {
    assertEquals(type.name(), type.extension());
    assertEquals(type, AttachmentType.fromExtension(type.extension().toLowerCase()));
    assertEquals(type.mediaType(), AttachmentType.mediaTypeFor(type.extension()));
  }

  @Test
  void anUnknownExtensionFallsBackRatherThanReturningNull() {
    // The client builds a data: URL from this value, and data:null;base64,... is undecodable.
    assertNull(AttachmentType.fromExtension("nope"));
    assertEquals(AttachmentType.FALLBACK_MEDIA_TYPE, AttachmentType.mediaTypeFor("nope"));
    assertEquals(AttachmentType.FALLBACK_MEDIA_TYPE, AttachmentType.mediaTypeFor(null));
  }
}
