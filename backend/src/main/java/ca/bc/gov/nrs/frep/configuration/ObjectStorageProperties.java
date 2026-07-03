package ca.bc.gov.nrs.frep.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "frep.object-storage")
public record ObjectStorageProperties(
    String host,
    String bucket,
    String accessKey,
    String secretKey
) {}
