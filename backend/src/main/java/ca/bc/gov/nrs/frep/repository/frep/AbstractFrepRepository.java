package ca.bc.gov.nrs.frep.repository.frep;

import ca.bc.gov.nrs.frep.exception.StoredProcedureException;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import oracle.jdbc.OracleTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Common helpers for calling Oracle PL/SQL packages exactly as the legacy
 * adaptors did: positional CallableStatement parameters and REF CURSORs read
 * via {@code cs.getObject(int)}.
 */
public abstract class AbstractFrepRepository {

  private static final Logger log = LoggerFactory.getLogger(AbstractFrepRepository.class);

  protected final JdbcTemplate jdbcTemplate;

  protected AbstractFrepRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @FunctionalInterface
  protected interface CallStatementSetter {
    void apply(CallableStatement cs) throws SQLException;
  }

  @FunctionalInterface
  protected interface CallResultExtractor<T> {
    T apply(CallableStatement cs) throws SQLException;
  }

  protected <T> T executeCall(String sql, CallStatementSetter setter, CallResultExtractor<T> extractor) {
    return jdbcTemplate.execute((ConnectionCallback<T>) conn -> {
      try (CallableStatement cs = conn.prepareCall(sql)) {
        cs.setFetchSize(CURSOR_FETCH_SIZE);
        setter.apply(cs);

        long t0 = System.nanoTime();
        cs.execute();
        long execNs = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        T out = extractor.apply(cs);
        long extractNs = System.nanoTime() - t1;

        if (log.isDebugEnabled()) {
          log.debug("Proc {} timing: exec={} ms, extract={} ms",
              shortSql(sql),
              execNs / 1_000_000,
              extractNs / 1_000_000);
        } else if (execNs + extractNs > 5_000_000_000L) {
          log.warn("Slow proc {}: exec={} ms, extract={} ms",
              shortSql(sql),
              execNs / 1_000_000,
              extractNs / 1_000_000);
        }
        return out;
      }
    });
  }

  private static String shortSql(String sql) {
    int open = sql.indexOf('(');
    int call = sql.indexOf("call ");
    if (call >= 0 && open > call) {
      return sql.substring(call + 5, open).trim();
    }
    return sql;
  }

  protected void throwIfError(String packageName, String procedureName, @Nullable String pErrorMessage) {
    if (StringUtils.hasText(pErrorMessage)) {
      throw new StoredProcedureException(packageName, procedureName, pErrorMessage);
    }
  }

  protected void setInOutString(CallableStatement cs, int index, @Nullable String value) throws SQLException {
    if (value == null) {
      cs.setNull(index, Types.VARCHAR);
    } else {
      cs.setString(index, value);
    }
    cs.registerOutParameter(index, Types.VARCHAR);
  }

  protected void registerOutCursor(CallableStatement cs, int index) throws SQLException {
    cs.registerOutParameter(index, OracleTypes.CURSOR);
  }

  private static final int CURSOR_FETCH_SIZE = 500;

  protected <T> List<T> readCursor(CallableStatement cs, int index, CursorRowReader<T> reader) throws SQLException {
    return readCursor(cs, index, reader, -1);
  }

  protected <T> List<T> readCursor(
      CallableStatement cs,
      int index,
      CursorRowReader<T> reader,
      int maxRows
  ) throws SQLException {
    List<T> out = maxRows > 0 ? new ArrayList<>(maxRows) : new ArrayList<>();
    Object obj = cs.getObject(index);
    if (!(obj instanceof ResultSet rs)) {
      return out;
    }
    try (ResultSet auto = rs) {
      auto.setFetchSize(CURSOR_FETCH_SIZE);
      while (auto.next()) {
        out.add(reader.read(auto));
        if (maxRows > 0 && out.size() >= maxRows) {
          break;
        }
      }
    }
    return out;
  }

  @FunctionalInterface
  protected interface CursorRowReader<T> {
    T read(ResultSet rs) throws SQLException;
  }

  protected static String placeholders(int n) {
    StringBuilder sb = new StringBuilder(2 * n);
    for (int i = 0; i < n; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('?');
    }
    return sb.toString();
  }

  protected static String callSql(String packageName, String procedureName, int paramCount) {
    return "{call " + packageName + "." + procedureName + "(" + placeholders(paramCount) + ")}";
  }

  protected static String emptyIfNull(@Nullable String s) {
    return s == null ? "" : s;
  }
}
