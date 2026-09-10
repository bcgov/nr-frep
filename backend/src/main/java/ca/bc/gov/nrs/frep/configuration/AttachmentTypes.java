package ca.bc.gov.nrs.frep.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The file types FREP accepts as Biodiversity attachments, and the media type each downloads as.
 *
 * <p>Configuration, not code: the list comes from {@code frep.attachments.allowed-types}, backed by
 * the {@code ATTACHMENT_ALLOWED_TYPES} environment variable, which the deployment templates fill
 * from a GitHub repository variable. Adding a format is a variable edit plus a redeploy — there is
 * no database change to make (FREP no longer validates the type against {@code THE.MIME_TYPE_CODE};
 * the extension we send is stored verbatim) and no code change either.
 *
 * <p><b>Format.</b> A comma-separated list of extensions, case-insensitive:
 *
 * <pre>
 *   BMP,CSV,DOCX,PDF,PNG,WEBP
 * </pre>
 *
 * <p>Extensions only — the media type each downloads as comes from {@link AttachmentType}. A format
 * that enum does not know can still be enabled here, but it downloads as
 * {@code application/octet-stream} until a constant is added for it; startup logs a warning naming
 * any such extension.
 *
 * <p><b>There is no in-code default.</b> The variable is the single source of truth, so a blank one
 * is a deployment error and this bean refuses to construct — the pod fails to start with a message
 * naming the variable, rather than silently accepting nothing or silently accepting a stale list
 * nobody remembered was there. In practice a deployment never gets that far: the parameter is
 * {@code required: true} in both openshift.deploy.yml templates, so an unset GitHub variable fails
 * at {@code oc process} time, before anything is rolled out.
 *
 * <p>Two lesser problems are logged rather than thrown, since neither stops uploads working: an
 * extension longer than the {@code VARCHAR2(10 BYTE)} column (which would fail at insert with
 * ORA-12899) and an extension with no media type (which downloads as
 * {@code application/octet-stream} and, if it is an image, cannot render a thumbnail).
 */
@Component
public class AttachmentTypes {

  private static final Logger log = LoggerFactory.getLogger(AttachmentTypes.class);

  /**
   * Width of {@code MIME_TYPE_CODE} on {@code BIODIVERSITY_CHKLST_ATTACH} — the one database limit
   * left on an attachment type now that the code table and its foreign key are out of the picture.
   */
  private static final int MAX_EXTENSION_LENGTH = 10;


  private final Map<String, String> mediaTypes;
  private final Set<String> allowed;
  private final String display;

  public AttachmentTypes(@Value("${frep.attachments.allowed-types:}") String configured) {
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException(
          "frep.attachments.allowed-types is not set. The list of file types FREP accepts has no "
              + "in-code default — set the ATTACHMENT_ALLOWED_TYPES environment variable (from the "
              + "GitHub repository variable of the same name) to a comma-separated extension list, "
              + "e.g. BMP,CSV,DOC,DOCX,GIF,JPG,PDF,PNG,WEBP.");
    }
    this.mediaTypes = parse(configured);
    // TreeSet so the display string and the rejection message it feeds are stable and alphabetical
    // regardless of how the variable was ordered.
    this.allowed = new TreeSet<>(mediaTypes.keySet());
    this.display = String.join(", ", allowed);
    // One list for both protocols: Biodiversity attachments and CHR photos accept exactly this set.
    log.info("Attachment types enabled ({}): {}", allowed.size(), display);
  }

  private static Map<String, String> parse(String source) {
    Map<String, String> parsed = new LinkedHashMap<>();
    for (String rawEntry : source.split(",")) {
      String entry = rawEntry.trim();
      if (entry.isEmpty()) {
        continue;
      }
      String extension = entry.toUpperCase();
      if (extension.length() > MAX_EXTENSION_LENGTH) {
        log.warn("Attachment type '{}' is longer than the {}-character MIME_TYPE_CODE column; "
            + "uploads of this type will fail on save", extension, MAX_EXTENSION_LENGTH);
      }
      if (AttachmentType.fromExtension(extension) == null) {
        log.warn("No media type known for attachment type '{}'; it will download as {}. Add it to "
            + "the AttachmentType enum if that matters (it does for an image — the thumbnail cannot "
            + "render without a real type).", extension, AttachmentType.FALLBACK_MEDIA_TYPE);
      }
      parsed.put(extension, AttachmentType.mediaTypeFor(extension));
    }
    if (parsed.isEmpty()) {
      throw new IllegalStateException(
          "frep.attachments.allowed-types contained no usable entries: '" + source + "'");
    }
    return parsed;
  }

  /** Uppercased extensions accepted for upload. */
  public Set<String> allowed() {
    return allowed;
  }

  /** True when {@code extension} (any case) is accepted. */
  public boolean isAllowed(String extension) {
    return extension != null && allowed.contains(extension.toUpperCase());
  }

  /** Alphabetical, comma-separated — for the "unsupported type" message. */
  public String display() {
    return display;
  }

  /**
   * The media type to label a download with. Never null: an unmapped extension downloads as a
   * generic binary rather than as nothing, since the client builds a {@code data:} URL from this.
   */
  public String mediaType(String extension) {
    if (extension == null) {
      return AttachmentType.FALLBACK_MEDIA_TYPE;
    }
    return mediaTypes.getOrDefault(extension.toUpperCase(), AttachmentType.FALLBACK_MEDIA_TYPE);
  }

  /** The configured list, as the frontend receives it — for the smoke-test/debug surface. */
  public String asConfiguredString() {
    return mediaTypes.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining(","));
  }
}
