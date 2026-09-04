package ca.bc.gov.nrs.frep.configuration;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A file type FREP knows how to label — its extension and the media type it is served as.
 *
 * <p>Deliberately broader than any allow-list: a constant here permits nothing on its own, it only
 * says how a file is labelled <em>if</em> {@link AttachmentTypes} enables it. Keeping the table
 * generous is what makes adding a format a one-word configuration edit rather than a code change.
 *
 * <p>The enum constant IS the extension, uppercased — {@code AttachmentType.PDF.extension()} is
 * {@code "PDF"}. That keeps the two from disagreeing and makes the whole table iterable, which the
 * tests use to pin every constant to a plausible media type.
 *
 * <p>{@link #FALLBACK_MEDIA_TYPE} is what an unknown extension is labelled with. It is never null:
 * the client builds a {@code data:} URL from this value, and {@code data:null;base64,...} is
 * undecodable.
 */
public enum AttachmentType {

  BMP("image/bmp"),
  CSV("text/csv"),
  DOC("application/msword"),
  DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
  GIF("image/gif"),
  GPX("application/gpx+xml"),
  HEIC("image/heic"),
  HTM("text/html"),
  HTML("text/html"),
  // IFM / JPK / OBD / RPT / SHP / XLD are legacy MoF formats with no registered media type.
  IFM(AttachmentType.FALLBACK_MEDIA_TYPE),
  JPEG("image/jpeg"),
  JPG("image/jpeg"),
  JPK(AttachmentType.FALLBACK_MEDIA_TYPE),
  KML("application/vnd.google-earth.kml+xml"),
  KMZ("application/vnd.google-earth.kmz"),
  MDB("application/vnd.ms-access"),
  MDE("application/vnd.ms-access"),
  MOV("video/quicktime"),
  MP3("audio/mpeg"),
  MP4("video/mp4"),
  OBD(AttachmentType.FALLBACK_MEDIA_TYPE),
  ODS("application/vnd.oasis.opendocument.spreadsheet"),
  ODT("application/vnd.oasis.opendocument.text"),
  PDF("application/pdf"),
  PNG("image/png"),
  PPS("application/vnd.ms-powerpoint"),
  PPT("application/vnd.ms-powerpoint"),
  PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
  RPT(AttachmentType.FALLBACK_MEDIA_TYPE),
  RTF("application/rtf"),
  SHP(AttachmentType.FALLBACK_MEDIA_TYPE),
  TIF("image/tiff"),
  TIFF("image/tiff"),
  TXT("text/plain"),
  WAV("audio/wav"),
  WEBP("image/webp"),
  XLD(AttachmentType.FALLBACK_MEDIA_TYPE),
  XLS("application/vnd.ms-excel"),
  XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
  XML("application/xml"),
  ZIP("application/zip");

  /** What an extension with no entry here is labelled with. */
  public static final String FALLBACK_MEDIA_TYPE = "application/octet-stream";

  private static final Map<String, AttachmentType> BY_EXTENSION = Arrays.stream(values())
      .collect(Collectors.toMap(AttachmentType::extension, Function.identity()));

  private final String mediaType;

  AttachmentType(String mediaType) {
    this.mediaType = mediaType;
  }

  /** The uppercased file extension, which is also the code stored in {@code MIME_TYPE_CODE}. */
  public String extension() {
    return name();
  }

  /** The media type this format is served as. */
  public String mediaType() {
    return mediaType;
  }

  /** The constant for {@code extension} (any case), or {@code null} when the table has none. */
  public static AttachmentType fromExtension(String extension) {
    return extension == null ? null : BY_EXTENSION.get(extension.trim().toUpperCase());
  }

  /**
   * The media type for {@code extension} (any case), falling back to
   * {@link #FALLBACK_MEDIA_TYPE} — never null.
   *
   * <p>Callers outside {@link AttachmentTypes} use this when they need a media type for a value that
   * is already known to be legal (a stored {@code MIME_TYPE_CODE}, say), rather than to decide
   * whether a file may be uploaded — that decision belongs to the configured allow-list.
   */
  public static String mediaTypeFor(String extension) {
    AttachmentType type = fromExtension(extension);
    return type == null ? FALLBACK_MEDIA_TYPE : type.mediaType();
  }
}
