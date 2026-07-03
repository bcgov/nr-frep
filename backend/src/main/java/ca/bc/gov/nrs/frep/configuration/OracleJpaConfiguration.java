package ca.bc.gov.nrs.frep.configuration;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Oracle DataSource configuration. Spring Boot's standard DataSourceAutoConfiguration
 * builds the {@code DataSource}, {@code JdbcTemplate}, {@code EntityManagerFactory},
 * and {@code PlatformTransactionManager} from {@code spring.datasource.*} in
 * application.yml (URL, credentials, Hikari pool tuning, TCPS truststore properties).
 *
 * <p>This class adds two things on top:
 * <ul>
 *   <li>{@link #warmOraclePool} — forces eager pool initialization at startup so
 *       connectivity failures are caught at boot rather than deferred to the first request.</li>
 *   <li>{@link #oracleJdbcTemplate} — a named {@code JdbcTemplate} alias that repositories
 *       inject via {@code @Qualifier("oracleJdbcTemplate")}. Wraps the same auto-configured
 *       {@code DataSource} that the default {@code jdbcTemplate} bean uses.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
@Slf4j
public class OracleJpaConfiguration {

  @Bean
  public InitializingBean warmOraclePool(DataSource dataSource) {
    return () -> {
      try (var ignored = dataSource.getConnection()) {
        // first getConnection() triggers Hikari pool initialization
      } catch (SQLException ex) {
        throw new IllegalStateException("Failed to validate Oracle DataSource at startup", ex);
      }
    };
  }

  @Bean(name = "oracleJdbcTemplate")
  public JdbcTemplate oracleJdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }
}
