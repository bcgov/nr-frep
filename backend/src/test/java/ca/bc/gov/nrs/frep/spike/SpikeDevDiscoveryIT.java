package ca.bc.gov.nrs.frep.spike;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * SPIKE — throwaway, and <b>strictly read-only</b>. Confirms the DEV connection works and finds a
 * checklist suitable for {@link SpikeOracleOrchestratorIT}, which needs an <b>ACT SLR</b> checklist
 * with at least one stratum that <b>has plots</b> (proof #3's refusal case is otherwise unreachable).
 *
 * <pre>
 * export DATABASE_USER=...  DATABASE_PASSWORD=...
 * mvn -o test -Dtest=SpikeDevDiscoveryIT -Dspike.oracle=true \
 *     -Djavax.net.ssl.trustStore=src/main/resources/cert/jssecacerts
 * </pre>
 */
@EnabledIfSystemProperty(named = "spike.oracle", matches = "true")
class SpikeDevDiscoveryIT {

  static JdbcTemplate connect() {
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
    ds.setConnectionProperties(trustStoreProperties());
    return new JdbcTemplate(ds);
  }

  /**
   * The TCPS truststore, passed as <b>driver connection properties</b> — which is how
   * {@code application.yml} supplies them (under {@code hikari.data-source-properties}), not as JVM
   * {@code -D} system properties. Same values as {@code TRUSTSTORE_PATH} / {@code KEYSTORE_SECRET}.
   */
  static java.util.Properties trustStoreProperties() {
    java.util.Properties props = new java.util.Properties();
    props.setProperty("javax.net.ssl.trustStoreType", "JKS");
    props.setProperty("javax.net.ssl.trustStore",
        System.getProperty("spike.truststore",
            System.getenv().getOrDefault("TRUSTSTORE_PATH",
                "src/main/resources/cert/jssecacerts")));
    props.setProperty("javax.net.ssl.trustStorePassword",
        System.getenv().getOrDefault("KEYSTORE_SECRET", "changeit"));
    // The listener negotiates TLSv1.2 (verified with openssl: ECDHE-RSA-AES256-GCM-SHA384). Pin it,
    // or a JDK 21 client offering 1.3 first gets a server-side handshake_failure.
    props.setProperty("oracle.net.ssl_version", "1.2");
    return props;
  }

  @Test
  void findCandidateChecklists() {
    JdbcTemplate jdbc = connect();

    System.out.println("\n=== connectivity ===");
    System.out.println("db time: " + jdbc.queryForObject("SELECT SYSDATE FROM DUAL", String.class));

    // Resource type lives on frep_resource_value, not the URL — SLB is historical/read-only and must
    // never be picked. Status must be ACT so the save units accept writes.
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT bc.biodiversity_checklist_id AS checklist_id, "
            + "       rv.frep_resource_value_type_code AS type, "
            + "       bc.frep_checklist_status_code AS status, "
            + "       COUNT(DISTINCT bs.stratum_id) AS strata, "
            + "       COUNT(bp.biodiversity_plot_id) AS plots "
            + "FROM the.biodiversity_checklist bc "
            + "JOIN the.frep_resource_value rv "
            + "  ON rv.frep_resource_value_id = bc.frep_resource_value_id "
            + "JOIN the.biodiversity_stratum bs "
            + "  ON bs.biodiversity_checklist_id = bc.biodiversity_checklist_id "
            + "JOIN the.biodiversity_plot bp "
            + "  ON bp.stratum_id = bs.stratum_id "
            + "WHERE bc.frep_checklist_status_code = 'ACT' "
            + "GROUP BY bc.biodiversity_checklist_id, rv.frep_resource_value_type_code, "
            + "         bc.frep_checklist_status_code "
            + "ORDER BY 5 DESC, 1");

    System.out.println("\n=== ACT checklists with strata that have plots ===");
    System.out.printf("%-14s %-6s %-7s %-7s %s%n", "CHECKLIST", "TYPE", "STATUS", "STRATA", "PLOTS");
    rows.stream().limit(25).forEach(r -> System.out.printf("%-14s %-6s %-7s %-7s %s%n",
        r.get("CHECKLIST_ID"), r.get("TYPE"), r.get("STATUS"), r.get("STRATA"), r.get("PLOTS")));
    System.out.println("\ncandidates: " + rows.size()
        + "  (use an SLR row — SLB is historical and read-only)\n");
  }
}
