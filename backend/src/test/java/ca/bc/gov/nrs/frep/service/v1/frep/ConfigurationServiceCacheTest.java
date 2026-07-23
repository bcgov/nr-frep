package ca.bc.gov.nrs.frep.service.v1.frep;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Caching needs the Spring proxy (@Cacheable is a no-op via a plain constructor), so this loads a
 * minimal @EnableCaching context (default ConcurrentMapCacheManager, mirroring the app) with a mocked
 * repository, and asserts the static code lists are cached while the dynamic method is not.
 */
@SpringJUnitConfig(ConfigurationServiceCacheTest.CacheConfig.class)
class ConfigurationServiceCacheTest {

  @Configuration
  @EnableCaching
  static class CacheConfig {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager();
    }

    @Bean
    CodeListRepository codeListRepository() {
      return mock(CodeListRepository.class);
    }

    @Bean
    ConfigurationService configurationService(CodeListRepository repository) {
      return new ConfigurationService(repository);
    }
  }

  @Autowired
  private ConfigurationService service;

  @Autowired
  private CodeListRepository repository;

  @Test
  void staticCodeListIsServedFromCacheOnSecondCall() {
    when(repository.getStreamClassCode())
        .thenReturn(List.of(Map.of("code", "A", "description", "Stream A")));

    service.getStreamClasses();
    service.getStreamClasses();

    // Second call is served from cache, so the (Oracle stored-proc) repository is invoked only once.
    verify(repository, times(1)).getStreamClassCode();
  }

  @Test
  void perChecklistEvaluatorsAreNotCached() {
    // getEvaluators is per-checklist (mutable) and intentionally uncached — every call hits the repo.
    when(repository.getEvaluatorCode("c1", "SLR")).thenReturn(List.of());

    service.getEvaluators("c1", "SLR");
    service.getEvaluators("c1", "SLR");

    verify(repository, times(2)).getEvaluatorCode("c1", "SLR");
  }
}
