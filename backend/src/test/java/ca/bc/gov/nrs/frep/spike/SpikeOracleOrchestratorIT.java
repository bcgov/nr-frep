package ca.bc.gov.nrs.frep.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import ca.bc.gov.nrs.frep.repository.v1.impl.ProtocolChecklistWriteRepositoryImpl;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import java.util.List;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SPIKE — throwaway. Proofs #2–#4 of the SLR-offline week-1 spike
 * (see {@code bio-offline-mode.local.md} § Execution plan). These exercise real PL/SQL, so unlike
 * {@link SpikeTxChainTest} they cannot run on H2 and are <b>skipped by default</b>.
 *
 * <h2>How to run</h2>
 * Needs a route to Oracle (VPN or port-forward to {@code nrcdb03.bcgov:1543}). Credentials come from
 * the environment — the same names {@code application.yml} uses — so they never land in shell history
 * or a process list. The connection is TCPS, so the JKS truststore is required
 * ({@code src/main/resources/cert/jssecacerts}, already gitignored and on disk).
 * <pre>
 * export DATABASE_USER=...  DATABASE_PASSWORD=...        # values are in application-local.yml
 *
 * mvn -o test -Dtest=SpikeOracleOrchestratorIT \
 *     -Dspike.oracle=true \
 *     -Dspike.checklistId=&lt;an ACT SLR checklist in DEV, with a stratum that has plots&gt; \
 *     -Djavax.net.ssl.trustStore=src/main/resources/cert/jssecacerts
 * </pre>
 * Host/service default to the DEV values in {@code application-local.yml}; override with
 * {@code -Dspike.host} / {@code -Dspike.service} if needed.
 *
 * <h2>Why this is safe to run against DEV</h2>
 * <b>Every proof runs inside a transaction that is always rolled back</b> — nothing is left behind,
 * including the rows the tmp-id proof creates. That is only sound because the Bio save procs do not
 * self-COMMIT (verified across all 9 package bodies) and because {@code executeCall} joins the Spring
 * transaction ({@link SpikeTxChainTest} proof #1). So proof #1 is what makes this harness safe, and
 * running it here also re-confirms proof #1 against real Oracle rather than H2.
 *
 * <p><b>Use DEV.</b> If any proc turns out to self-commit, rows will persist.
 */
@EnabledIfSystemProperty(named = "spike.oracle", matches = "true")
class SpikeOracleOrchestratorIT {

  private static ProtocolChecklistWriteRepositoryImpl repo;
  private static TransactionTemplate tx;
  private static String checklistId;

  @BeforeAll
  static void wire() {
    // Same TCPS descriptor application.yml builds, so the spike talks to Oracle exactly as the app
    // does. Credentials from the environment only — never a -D, which would leak into shell history.
    String host = System.getProperty("spike.host", "nrcdb03.bcgov");
    String service = System.getProperty("spike.service", "fortmp1.nrs.bcgov");
    String url = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=" + host
        + ")(PORT=1543))(CONNECT_DATA=(SERVICE_NAME=" + service + ")(SERVER=DEDICATED)))";
    String user = System.getenv("DATABASE_USER");
    String password = System.getenv("DATABASE_PASSWORD");
    assertNotNull(user, "export DATABASE_USER (see application-local.yml)");
    assertNotNull(password, "export DATABASE_PASSWORD (see application-local.yml)");

    DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
    ds.setDriverClassName("oracle.jdbc.OracleDriver");
    ds.setConnectionProperties(SpikeDevDiscoveryIT.trustStoreProperties());
    DataSource dataSource = ds;
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // Attachments are out of scope for these proofs; the mock is never called.
    repo = new ProtocolChecklistWriteRepositoryImpl(jdbc, mock(ObjectStorageService.class));
    tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    checklistId = System.getProperty("spike.checklistId");
    assertNotNull(checklistId, "-Dspike.checklistId is required");
  }

  /** Run work in a transaction that is always rolled back, so DEV is left untouched. */
  private static void inRolledBackTx(Consumer<Void> work) {
    tx.execute(status -> {
      try {
        work.accept(null);
        return null;
      } finally {
        status.setRollbackOnly();
      }
    });
  }

  // ── Proof #2 — the opening/notes token collision ─────────────────────
  //
  // Both write BIODIVERSITY_CHECKLIST and share its single REVISION_COUNT. If the orchestrator
  // chains them without re-threading the token returned by the first, the second must fail
  // record.modified2 — deterministically, not as a race. If this does NOT fail, the doc's analysis
  // is wrong and the design gets simpler; that is worth knowing either way.

  @Test
  void openingThenNotesWithAStaleTokenIsRejected() {
    inRolledBackTx(ignored -> {
      BiodiversityOpening before = repo.getBiodiversityOpening(checklistId);
      String staleToken = before.revisionCount();

      BiodiversityOpening saved = repo.saveBiodiversityOpening(before, "IDIR\\SPIKE");
      assertNotEquals(staleToken, saved.revisionCount(),
          "the opening save must advance the shared token");

      RiparianNotes withStale =
          new RiparianNotes(checklistId, "spike note", staleToken);
      RuntimeException ex = assertThrows(RuntimeException.class,
          () -> repo.saveNotes(withStale, "SLR", "IDIR\\SPIKE"));
      assertTrue(ex.getMessage().toLowerCase().contains("modified"),
          "expected record.modified2, got: " + ex.getMessage());
    });
  }

  @Test
  void openingThenNotesWithTheThreadedTokenSucceeds() {
    inRolledBackTx(ignored -> {
      BiodiversityOpening before = repo.getBiodiversityOpening(checklistId);
      BiodiversityOpening saved = repo.saveBiodiversityOpening(before, "IDIR\\SPIKE");

      // The fix the orchestrator must implement: carry the returned token forward.
      RiparianNotes threaded =
          new RiparianNotes(checklistId, "spike note", saved.revisionCount());
      RiparianNotes result = repo.saveNotes(threaded, "SLR", "IDIR\\SPIKE");

      assertNotNull(result);
      assertEquals("spike note", result.noteDescription());
    });
  }

  // ── Proof #3 — tombstone ordering ────────────────────────────────────
  //
  // frep_biodiversity_stratum.validate_remove refuses while any plot references the stratum, so the
  // orchestrator MUST apply plot tombstones before the stratum's. Wrong order = refused = the whole
  // @Transactional sync rolls back.

  @Test
  void deletingAStratumThatStillHasPlotsIsRefused() {
    inRolledBackTx(ignored -> {
      BioStratumRow stratum = aStratumWithPlots();
      String error = repo.deleteBioStratum(stratum.stratumId(), stratum.revisionCount());

      assertTrue(error != null && !error.isBlank(),
          "expected the childexists refusal, got no error");
    });
  }

  @Test
  void deletingThePlotsFirstThenTheStratumSucceeds() {
    inRolledBackTx(ignored -> {
      BioStratumRow stratum = aStratumWithPlots();
      for (BioPlotRow plot : repo.listBioPlots(stratum.stratumId())) {
        String plotError = repo.deleteBioPlot(plot.plotId(), plot.revisionCount());
        assertTrue(plotError == null || plotError.isBlank(),
            "plot delete failed: " + plotError);
      }

      String error = repo.deleteBioStratum(stratum.stratumId(), stratum.revisionCount());
      assertTrue(error == null || error.isBlank(),
          "stratum delete should succeed once its plots are gone, got: " + error);
    });
  }

  private static BioStratumRow aStratumWithPlots() {
    List<BioStratumRow> strata = repo.listBioStrata(checklistId);
    return strata.stream()
        .filter(s -> !repo.listBioPlots(s.stratumId()).isEmpty())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "spike.checklistId must name a checklist with at least one stratum that has plots"));
  }

  // ── Proof #4 — tmp-id assignment through the IN OUT params ───────────
  //
  // An offline-created stratum/plot has no real id until upload. The replay chain depends on the
  // save units accepting "no id yet", assigning one from the sequence, and returning it.

  @Test
  void savingAStratumWithNoIdAssignsAndReturnsOne() {
    inRolledBackTx(ignored -> {
      BioStratum blank = newStratumForSpike();
      BioStratum saved = repo.saveBioStratum(blank, "IDIR\\SPIKE");

      assertNotNull(saved.stratumId(), "the proc must assign a stratum id");
      assertTrue(saved.stratumId().matches("\\d+"),
          "expected a numeric sequence id, got: " + saved.stratumId());
      assertNotNull(saved.revisionCount(), "and return the new row's token");
    });
  }

  /**
   * A new stratum, shaped like one created offline: clone an existing row and null out the identity.
   * No id + no revision count = "insert me", which is exactly what a {@code tmp:} row looks like once
   * the facade strips its temporary id at sync assembly.
   *
   * <p><b>Finding while writing this (good news for the offline design):</b> {@code stratumNumber} is
   * <b>user-entered</b>, not server-assigned — {@code BioStratumView.tsx:356} validates it as 1–3
   * letters then 0–2 digits ("AB12") and nothing calls the legacy {@code FREP_211_BIOSTRATUM.add_new}
   * proc that returns a sequence value. So an offline-created stratum needs <b>no server round-trip</b>
   * to get its number; the field evaluator supplies it exactly as they do online.
   *
   * <p>The clone therefore carries the template's number, which will collide. Whoever runs this
   * against DEV should give it a free number — {@code BioStratum} has no {@code withStratumNumber},
   * so either add one locally for the spike or build the record directly.
   */
  private static BioStratum newStratumForSpike() {
    BioStratumRow existing = repo.listBioStrata(checklistId).stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("spike.checklistId has no strata to clone"));
    BioStratum template = repo.getBioStratum(existing.stratumId());
    return template.withIdentity(null, null);
  }
}
