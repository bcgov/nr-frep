package ca.bc.gov.nrs.frep.service.v1;

import java.net.URI;

import ca.bc.gov.nrs.frep.configuration.ObjectStorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ObjectStorageService {

  private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

  private final ObjectStorageProperties properties;

  public ObjectStorageService(ObjectStorageProperties properties) {
    this.properties = properties;
  }


  public byte[] getObjectBytes(String key) {
    try (S3Client client = client()) {
      return client.getObjectAsBytes(builder -> builder.bucket(properties.bucket()).key(key)).asByteArray();
    }
  }

  public boolean objectExists(String key) {
    try (S3Client client = client()) {
      client.headObject(builder -> builder.bucket(properties.bucket()).key(key));
      return true;
    } catch (NoSuchKeyException ex) {
      return false;
    } catch (Exception ex) {
      // A missing object and an unreachable bucket both answered "false" silently, so a storage
      // outage was indistinguishable from a not-yet-migrated attachment — and the caller served an
      // empty file as though that were normal. Still false (the caller's contract), but recorded.
      log.error("Object storage check failed for key :: {}", key, ex);
      return false;
    }
  }

  /**
   * The stored size of {@code key} in bytes, or {@code -1} when the object is missing.
   *
   * <p>A HEAD per object, called only for the rows on the page being returned. Deliberately not a
   * prefix listing: Biodiversity keys are flat ({@code slr/<id>}) so a prefix would sweep every
   * checklist's attachments, and the listing API caps at 1000 keys per response.
   */
  public long getObjectSize(String key) {
    try (S3Client client = client()) {
      return client.headObject(builder -> builder.bucket(properties.bucket()).key(key))
          .contentLength();
    } catch (NoSuchKeyException ex) {
      return -1;
    } catch (Exception ex) {
      // -1 renders as a blank size in the attachments table. Without this the only symptom of a
      // storage problem was a column full of blanks and no explanation anywhere.
      log.error("Object storage size lookup failed for key :: {}", key, ex);
      return -1;
    }
  }

  /**
   * Store {@code content} at {@code key} in the shared bucket. Generic key-based op — CHR photos use it
   * with a {@code {checklistId}-{attachmentId}.{ext}} key; Biodiversity attachments use
   * {@code slr/<attachmentId>}.
   */
  public void putObject(String key, String contentType, byte[] content) {
    try (S3Client client = client()) {
      client.putObject(
          PutObjectRequest.builder()
              .bucket(properties.bucket())
              .key(key)
              .contentType(contentType)
              .build(),
          RequestBody.fromBytes(content)
      );
    }
  }


  /** Delete the object at {@code key} (no-op if absent). Used by the CHR photo and Biodiversity
   * attachment delete paths, each with its own exact key. */
  public void deleteObject(String key) {
    try (S3Client client = client()) {
      client.deleteObject(DeleteObjectRequest.builder()
          .bucket(properties.bucket())
          .key(key)
          .build());
    }
  }




  private S3Client client() {
    return S3Client.builder()
        .region(Region.US_EAST_1)
        .endpointOverride(URI.create(properties.host()))
        .forcePathStyle(true)
        // AWS SDK 2.30+ defaults to adding flexible (CRC32) checksums and aws-chunked
        // trailers on every request. The BC Gov NRS object store (S3-compatible gateway)
        // rejects these with a "Content-SHA256 did not match" 400, so only send/validate
        // checksums when the operation explicitly requires them.
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
        ))
        .build();
  }

}
