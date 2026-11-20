package ca.bc.gov.nrs.frep.configuration;

import java.security.Security;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Re-enables the RSA key exchange the shared Oracle database needs, and nothing else.
 *
 * <p>Once the listener hands a TCPS connection over, the database accepts only
 * {@code TLS_RSA_WITH_AES_256_CBC_SHA}. Java 21.0.12 and later list {@code TLS_RSA_*} in
 * {@code jdk.tls.disabledAlgorithms} by default, so without this the backend cannot connect. Every
 * other default stays disabled. Remove this class once the database offers ECDHE cipher suites.
 */
public final class OracleTlsCompatibility {

  static final String DISABLED_ALGORITHMS = "jdk.tls.disabledAlgorithms";
  static final String RSA_KEY_EXCHANGE = "TLS_RSA_*";

  private OracleTlsCompatibility() {
  }

  /**
   * Takes {@code TLS_RSA_*} out of the disabled list. Call before the first TLS connection: Java
   * reads the property once, when TLS is first used.
   */
  public static void allowRsaKeyExchange() {
    String disabledAlgorithms = Security.getProperty(DISABLED_ALGORITHMS);
    if (disabledAlgorithms != null) {
      Security.setProperty(DISABLED_ALGORITHMS, withoutRsaKeyExchange(disabledAlgorithms));
    }
  }

  /** The list without the {@code TLS_RSA_*} entry, everything else in its order. */
  static String withoutRsaKeyExchange(String disabledAlgorithms) {
    return Arrays.stream(disabledAlgorithms.split(","))
        .map(String::trim)
        .filter(algorithm -> !algorithm.isEmpty())
        .filter(algorithm -> !algorithm.equals(RSA_KEY_EXCHANGE))
        .collect(Collectors.joining(", "));
  }
}
