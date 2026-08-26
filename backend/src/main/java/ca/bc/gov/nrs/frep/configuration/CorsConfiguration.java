package ca.bc.gov.nrs.frep.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * This class holds the configuration for CORS handling.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CorsConfiguration implements WebMvcConfigurer {

  private final HrsConfiguration configuration;

  @Override
  public void addCorsMappings(@NonNull CorsRegistry registry) {
    var frontendConfig = configuration.getFrontend();
    var cors = frontendConfig.getCors();
    String origins = frontendConfig.getUrl();
    List<String> allowedOrigins = new ArrayList<>();

    if (StringUtils.isNotBlank(origins) && origins.contains(",")) {
      allowedOrigins.addAll(Arrays.asList(origins.split(",")));
    } else {
      allowedOrigins.add(origins);
    }

    log.info("Allowed origins: {} {}", allowedOrigins, allowedOrigins.toArray(new String[0]));

    registry
        .addMapping("/api/**")
        .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
        .allowedMethods(cors.getMethods().toArray(new String[0]))
        .allowedHeaders(cors.getHeaders().toArray(new String[0]))
        .exposedHeaders(cors.getHeaders().toArray(new String[0]))
        .maxAge(cors.getAge().getSeconds())
        .allowCredentials(true);

    // No CORS mapping for /actuator: the probes and the Prometheus scrape are not browsers, so
    // they never send an Origin. The previous `allowedOrigins("*")` granted browser read access
    // that nothing asked for. Matches nr-fspts, which has no actuator CORS at all.

    WebMvcConfigurer.super.addCorsMappings(registry);
  }
}
