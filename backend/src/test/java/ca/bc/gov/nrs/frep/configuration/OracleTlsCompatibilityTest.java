package ca.bc.gov.nrs.frep.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Security;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Re-enabling the RSA key exchange for Oracle, and nothing else. */
class OracleTlsCompatibilityTest {

  /** Java 21.0.13's default {@code jdk.tls.disabledAlgorithms}, as {@code java.security} lists it. */
  private static final String JAVA_21_DEFAULTS =
      "SSLv3, TLSv1, TLSv1.1, DTLSv1.0, RC4, DES, MD5withRSA, DH keySize < 1024, "
          + "EC keySize < 224, 3DES_EDE_CBC, anon, NULL, ECDH, TLS_RSA_*, "
          + "rsa_pkcs1_sha1 usage HandshakeSignature, ecdsa_sha1 usage HandshakeSignature, "
          + "dsa_sha1 usage HandshakeSignature";

  @Test
  @DisplayName("removes TLS_RSA_* and keeps every other default, in order")
  void removesOnlyRsaKeyExchange() {
    assertThat(OracleTlsCompatibility.withoutRsaKeyExchange(JAVA_21_DEFAULTS))
        .isEqualTo("SSLv3, TLSv1, TLSv1.1, DTLSv1.0, RC4, DES, MD5withRSA, DH keySize < 1024, "
            + "EC keySize < 224, 3DES_EDE_CBC, anon, NULL, ECDH, "
            + "rsa_pkcs1_sha1 usage HandshakeSignature, ecdsa_sha1 usage HandshakeSignature, "
            + "dsa_sha1 usage HandshakeSignature");
  }

  @Test
  @DisplayName("leaves a list without TLS_RSA_* as it is")
  void leavesOtherListsAlone() {
    assertThat(OracleTlsCompatibility.withoutRsaKeyExchange("SSLv3, TLSv1, RC4"))
        .isEqualTo("SSLv3, TLSv1, RC4");
  }

  @Test
  @DisplayName("does not mistake a longer name that starts with TLS_RSA for the wildcard")
  void keepsSimilarNames() {
    assertThat(OracleTlsCompatibility.withoutRsaKeyExchange("TLS_RSA_PSK_*, TLS_RSA_*, RC4"))
        .isEqualTo("TLS_RSA_PSK_*, RC4");
  }

  @Test
  @DisplayName("updates the JVM's security property")
  void updatesTheSecurityProperty() {
    String original = Security.getProperty(OracleTlsCompatibility.DISABLED_ALGORITHMS);
    try {
      Security.setProperty(OracleTlsCompatibility.DISABLED_ALGORITHMS, "SSLv3, TLS_RSA_*, RC4");

      OracleTlsCompatibility.allowRsaKeyExchange();

      assertThat(Security.getProperty(OracleTlsCompatibility.DISABLED_ALGORITHMS))
          .isEqualTo("SSLv3, RC4");
    } finally {
      Security.setProperty(OracleTlsCompatibility.DISABLED_ALGORITHMS, original);
    }
  }
}
