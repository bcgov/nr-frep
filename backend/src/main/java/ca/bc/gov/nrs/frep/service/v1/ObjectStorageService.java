package ca.bc.gov.nrs.frep.service.v1;

import java.net.URI;
import java.util.List;

import ca.bc.gov.nrs.frep.configuration.ObjectStorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
public class ObjectStorageService {

  private final ObjectStorageProperties properties;

  public ObjectStorageService(ObjectStorageProperties properties) {
    this.properties = properties;
  }

  public void syncChecklistPhotos(String checklistId, List<PhotoUpload> photos) {
    if (photos == null || photos.isEmpty()) {
      deleteObjectsWithPrefix(checklistId);
      return;
    }

    boolean filesExist = hasObjectsWithPrefix(checklistId);
    if (filesExist) {
      for (S3Object summary : listObjectsWithPrefix(checklistId)) {
        copyObject(summary.key(), "temp-" + summary.key());
        deleteObject(summary.key());
      }
    }

    for (PhotoUpload photo : photos) {
      if (photo.content() == null || photo.content().length == 0) {
        continue;
      }
      String key = checklistId + "-" + photo.fileName();
      putObject(key, photo.contentType(), photo.content());
    }

    if (filesExist) {
      for (S3Object summary : listObjectsWithPrefix("temp-")) {
        deleteObject(summary.key());
      }
    }
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
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Store {@code content} at {@code key} in the shared bucket. Generic key-based op — CHR photos use it
   * via {@link #syncChecklistPhotos}; Biodiversity attachments call it directly with an
   * {@code slr/<attachmentId>} key (see the bio-attachments object-storage migration).
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

  private void copyObject(String sourceKey, String destinationKey) {
    try (S3Client client = client()) {
      client.copyObject(CopyObjectRequest.builder()
          .sourceBucket(properties.bucket())
          .sourceKey(sourceKey)
          .destinationBucket(properties.bucket())
          .destinationKey(destinationKey)
          .build());
    }
  }

  /** Delete the object at {@code key} (no-op if absent). Generic — used by CHR prefix cleanup and by
   * Biodiversity attachment delete ({@code slr/<attachmentId>}). */
  public void deleteObject(String key) {
    try (S3Client client = client()) {
      client.deleteObject(DeleteObjectRequest.builder()
          .bucket(properties.bucket())
          .key(key)
          .build());
    }
  }

  private void deleteObjectsWithPrefix(String prefix) {
    for (S3Object summary : listObjectsWithPrefix(prefix)) {
      deleteObject(summary.key());
    }
  }

  private boolean hasObjectsWithPrefix(String prefix) {
    return !listObjectsWithPrefix(prefix).isEmpty();
  }

  private List<S3Object> listObjectsWithPrefix(String prefix) {
    try (S3Client client = client()) {
      return client.listObjectsV2(ListObjectsV2Request.builder()
              .bucket(properties.bucket())
              .prefix(prefix)
              .build())
          .contents();
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

  public record PhotoUpload(String fileName, String contentType, byte[] content) {}
}
