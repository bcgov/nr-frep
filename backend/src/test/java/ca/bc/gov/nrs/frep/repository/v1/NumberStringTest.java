package ca.bc.gov.nrs.frep.repository.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AbstractFrepRepository#numberString} exists because of a real row: a HEIGHT value in
 * BIODIVERSITY_STAND_DETAIL that {@code rs.getString(...)} could not read.
 */
class NumberStringTest {

  /** Exposes the protected static helper without needing a JdbcTemplate. */
  private static class Probe extends AbstractFrepRepository {
    Probe() {
      super(null);
    }

    static String read(ResultSet rs, String column) throws SQLException {
      return numberString(rs, column);
    }
  }

  private static String read(ResultSet rs, String column) throws SQLException {
    return Probe.read(rs, column);
  }

  @Test
  @DisplayName("reads a NUMBER the Oracle driver refuses to stringify")
  void readsNumberThatGetStringRejects() throws SQLException {
    // oracle.sql.NUMBER.toString() throws this for bytes that are not a canonical NUMBER; the same
    // value reads fine as a BigDecimal, which is why every other client displays it.
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString(anyString()))
        .thenThrow(new IllegalArgumentException("Invalid Input Number"));
    when(rs.getBigDecimal("height")).thenReturn(new BigDecimal("11.0"));

    assertThat(read(rs, "height")).isEqualTo("11.0");
    verify(rs, never()).getString(anyString());
  }

  @Test
  @DisplayName("keeps the column's scale rather than switching to scientific notation")
  void keepsScale() throws SQLException {
    // The value goes straight into a form field, so "1.1E+1" would be shown to the evaluator.
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBigDecimal("height")).thenReturn(new BigDecimal("11.0"));
    when(rs.getBigDecimal("utm_northing")).thenReturn(new BigDecimal("7654321"));

    assertThat(read(rs, "height")).isEqualTo("11.0");
    assertThat(read(rs, "utm_northing")).isEqualTo("7654321");
  }

  @Test
  @DisplayName("passes a null column through as null")
  void nullStaysNull() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBigDecimal("basal_area_factor")).thenReturn(null);

    assertThat(read(rs, "basal_area_factor")).isNull();
  }
}
