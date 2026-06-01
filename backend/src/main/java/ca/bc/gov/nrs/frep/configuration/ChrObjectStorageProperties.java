package ca.bc.gov.nrs.frep.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "frep.chr.object-storage")
public record ChrObjectStorageProperties(
    String host,
    String bucket,
    String accessKey,
    String secretKey
) {}
