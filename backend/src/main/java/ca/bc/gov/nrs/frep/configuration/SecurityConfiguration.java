package ca.bc.gov.nrs.frep.configuration;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import ca.bc.gov.nrs.frep.security.ApiAuthorizationCustomizer;
// import ca.bc.gov.nrs.frep.security.CsrfCookieFilter;
// import ca.bc.gov.nrs.frep.security.CsrfSecurityCustomizer;
import ca.bc.gov.nrs.frep.security.HeadersSecurityCustomizer;
// import ca.bc.gov.nrs.frep.security.Oauth2SecurityCustomizer;

/**
 * Main security configuration.
 *
 * LOCAL DEV: Cognito JWT validation and CSRF are disabled. Re-enable the
 * commented dependencies and filter-chain steps before deploying.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  @Value("${ca.bc.gov.nrs.frontend.url:http://localhost:3000}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      HeadersSecurityCustomizer headersCustomizer,
      // CsrfSecurityCustomizer csrfCustomizer,
      // CsrfCookieFilter csrfCookieFilter,
      ApiAuthorizationCustomizer apiCustomizer
      // Oauth2SecurityCustomizer oauth2Customizer
  ) throws Exception {

    http
        .headers(headersCustomizer)
        // .csrf(csrfCustomizer)
        // .addFilterAfter(csrfCookieFilter, BasicAuthenticationFilter.class)
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(apiCustomizer)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable);
        // .oauth2ResourceServer(oauth2Customizer);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    List<String> origins = Arrays.asList(allowedOrigins.split(","));
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource() {
      @Override
      public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        return configuration;
      }
    };

    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

}
