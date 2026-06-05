package ca.bc.gov.nrs.frep.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.persistence.Entity;
import java.util.Set;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Validates the JPA mapping metadata for every {@code @Entity} in the CHR/FREP entity package
 * <em>without a database</em>. The CHR persistence layer was originally generated as unmapped
 * POJOs (only {@code @Entity}/{@code @Table}); this test fails fast if any entity is missing an
 * identifier, has a duplicate column mapping, an invalid {@code @MapsId}/composite id, or a broken
 * association — errors the Java compiler cannot catch and that would otherwise only surface when
 * the {@code EntityManagerFactory} boots against Oracle.
 */
class ChrEntityMappingTest {

  @Test
  void allEntitiesProduceValidHibernateMetadata() throws ClassNotFoundException {
    StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
        .applySetting("hibernate.dialect", "org.hibernate.dialect.OracleDialect")
        // Build the mapping model only; never attempt a JDBC connection.
        .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
        .build();
    try {
      MetadataSources sources = new MetadataSources(registry);

      ClassPathScanningCandidateComponentProvider scanner =
          new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
      Set<BeanDefinition> entities =
          scanner.findCandidateComponents("ca.bc.gov.nrs.frep.entity");

      assertFalse(entities.isEmpty(), "Expected to find @Entity classes to validate");
      for (BeanDefinition definition : entities) {
        sources.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
      }

      // buildMetadata() constructs and validates the full O/R mapping model.
      assertDoesNotThrow(() -> {
        sources.buildMetadata();
      }, "Hibernate could not build valid mapping metadata for the CHR/FREP entities");
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
