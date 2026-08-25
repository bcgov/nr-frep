package ca.bc.gov.nrs.frep.repository.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks in the assumption the whole snapshot-POST design rests on: that the Bio write path becomes
 * atomic purely by adding {@code @Transactional}.
 *
 * <p><b>Question.</b> The whole snapshot-POST design rests on one unproven assumption, recorded as
 * constraint #2: the Bio write path has no {@code @Transactional} anywhere and Hikari runs at its
 * default {@code autoCommit=true}, so <em>today</em> every proc call commits independently. The
 * orchestrator is supposed to become atomic purely by adding {@code @Transactional}. That only works
 * if {@link AbstractFrepRepository#executeCall} actually <em>joins</em> the Spring transaction —
 * it calls {@code jdbcTemplate.execute(ConnectionCallback)}, and if that obtained a raw connection
 * straight from the DataSource instead of the transaction-bound one, {@code @Transactional} would be
 * decorative and a mid-chain conflict would leave the sync half-applied.
 *
 * <p><b>Why this can be proven without Oracle.</b> The mechanism under test is Spring's
 * ({@code DataSourceUtils} binding the connection, the tx manager flipping {@code autoCommit} off,
 * {@code JdbcTemplate} joining) — not Oracle's. The Oracle-specific half of constraint #2, "the procs
 * do not self-COMMIT", was already established by reading all 9 package bodies. So this runs on H2,
 * through the <b>real</b> {@code executeCall}, and the answer transfers.
 *
 * <p>Kept when the rest of the SLR-offline spike harness was deleted: it costs nothing, needs no
 * database, and is the only thing standing between a refactor of {@code executeCall} and a sync that
 * silently half-applies. The Oracle-side proofs it was written alongside (token threading, tombstone
 * ordering, tmp-id assignment) are now covered by ProtocolChecklistServiceTest.
 */
class ProcChainAtomicityTest {

  /** Minimal stand-in for a save unit: reuses the production {@code executeCall} verbatim. */
  static class ChainRepo extends AbstractFrepRepository {
    ChainRepo(JdbcTemplate jdbcTemplate) {
      super(jdbcTemplate);
    }

    /**
     * One "save unit" call. Plain SQL rather than {@code {call PKG.PROC(?)}} because H2 has no
     * packages — irrelevant to the question, which is how the connection is obtained, not what runs
     * on it. Everything else is the production path: {@code jdbcTemplate.execute(ConnectionCallback)}
     * → {@code conn.prepareCall} → {@code cs.execute()}.
     */
    void write(String id) {
      executeCall("INSERT INTO CHAIN_ROW (ID) VALUES (?)",
          cs -> cs.setString(1, id),
          cs -> null);
    }
  }

  /** Stands in for the snapshot-POST orchestrator chaining several save units. */
  static class ChainOrchestrator {
    private final ChainRepo repo;

    ChainOrchestrator(ChainRepo repo) {
      this.repo = repo;
    }

    /** The proposed orchestrator: one transaction spanning the whole chain. */
    @Transactional
    public void atomicChain(boolean failMidway) {
      repo.write("A");
      if (failMidway) {
        // Stands in for a mid-chain record.modified2 surfacing as StoredProcedureException.
        throw new IllegalStateException("simulated mid-chain conflict");
      }
      repo.write("B");
    }

    /** Today's shape: no transaction, so each call commits on its own. */
    public void unmanagedChain(boolean failMidway) {
      repo.write("A");
      if (failMidway) {
        throw new IllegalStateException("simulated mid-chain conflict");
      }
      repo.write("B");
    }
  }

  @Configuration
  @EnableTransactionManagement
  static class ChainConfig {
    @Bean
    DataSource dataSource() {
      // autoCommit is left at the JDBC default (true), mirroring Hikari's unset auto-commit in
      // application.yml — the exact condition constraint #2 warns about.
      DriverManagerDataSource ds = new DriverManagerDataSource(
          "jdbc:h2:mem:procchain;DB_CLOSE_DELAY=-1", "sa", "");
      ds.setDriverClassName("org.h2.Driver");
      return ds;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource ds) {
      return new JdbcTemplate(ds);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource ds) {
      return new DataSourceTransactionManager(ds);
    }

    @Bean
    ChainRepo chainRepo(JdbcTemplate jdbcTemplate) {
      return new ChainRepo(jdbcTemplate);
    }

    /** A real proxied bean, so {@code @Transactional} is exercised via AOP, not simulated. */
    @Bean
    ChainOrchestrator chainOrchestrator(ChainRepo repo) {
      return new ChainOrchestrator(repo);
    }
  }

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private ChainOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    context = new AnnotationConfigApplicationContext(ChainConfig.class);
    jdbc = context.getBean(JdbcTemplate.class);
    orchestrator = context.getBean(ChainOrchestrator.class);
    jdbc.execute("DROP TABLE IF EXISTS CHAIN_ROW");
    jdbc.execute("CREATE TABLE CHAIN_ROW (ID VARCHAR(10) PRIMARY KEY)");
  }

  @AfterEach
  void tearDown() {
    context.close();
  }

  private List<String> rows() {
    return jdbc.queryForList("SELECT ID FROM CHAIN_ROW ORDER BY ID", String.class);
  }

  // ── The proof ────────────────────────────────────────────────────────

  @Test
  void transactionalChainRollsBackEveryWriteOnAMidChainFailure() {
    assertThrows(IllegalStateException.class, () -> orchestrator.atomicChain(true));

    assertEquals(List.of(), rows(),
        "@Transactional must roll the whole chain back — a half-applied sync is the failure mode "
            + "the snapshot POST exists to prevent");
  }

  @Test
  void transactionalChainCommitsWhenItSucceeds() {
    // Control: proves the rollback test above is not passing simply because nothing ever commits.
    orchestrator.atomicChain(false);

    assertEquals(List.of("A", "B"), rows());
  }

  @Test
  void withoutTransactionalTheFirstWriteSurvivesTheFailure() {
    // Control, and the reason the annotation is mandatory rather than tidy: this is exactly what the
    // orchestrator would do today. The first save unit is already committed when the second fails.
    assertThrows(IllegalStateException.class, () -> orchestrator.unmanagedChain(true));

    assertEquals(List.of("A"), rows(),
        "with autoCommit=true and no transaction, each executeCall commits independently");
  }
}
