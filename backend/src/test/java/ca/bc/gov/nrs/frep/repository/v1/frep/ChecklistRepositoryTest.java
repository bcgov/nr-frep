package ca.bc.gov.nrs.frep.repository.v1.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import oracle.jdbc.OracleTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Guards the GET-proc call wiring for the read (load) path. */
@ExtendWith(MockitoExtension.class)
class ChecklistRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private CallableStatement cs;

  private ChecklistRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new ChecklistRepository(jdbcTemplate);
    lenient().when(connection.prepareCall(anyString())).thenReturn(cs);
    lenient().when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
      ConnectionCallback<?> callback = inv.getArgument(0);
      return callback.doInConnection(connection);
    });
  }

  // Some reads also call FREP_TOMBSTONE_GET (header merge), so capture all prepared calls and pick
  // the one for the section proc.
  private String capturePreparedCall(String procFragmentLower) throws Exception {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(connection, org.mockito.Mockito.atLeastOnce()).prepareCall(sql.capture());
    return sql.getAllValues().stream()
        .filter(s -> s.toLowerCase().contains(procFragmentLower))
        .findFirst()
        .orElseThrow();
  }

  private static long placeholderCount(String call) {
    return call.chars().filter(c -> c == '?').count();
  }

  /** 210: 35 params, resource_value_id IN @19, checklist_id IN @20, error @35 (no arrays). */
  @Test
  void getBioOpeningMatchesDeployedSignature() throws Exception {
    repository.getBioOpening("9001");
    String call = capturePreparedCall("frep_210_bio_opening.get");
    assertEquals(35, placeholderCount(call), call);
    verify(cs).setString(20, "9001");
  }

  /** 211: 82 params, checklist_id IN @17, wind-treatment CURSOR @82. */
  @Test
  void getBioStratumMatchesDeployedSignature() throws Exception {
    repository.getBioStratum("9001");
    String call = capturePreparedCall("frep_211_biostratum.get");
    assertEquals(82, placeholderCount(call), call);
    verify(cs).setString(17, "9001");
    verify(cs).registerOutParameter(82, OracleTypes.CURSOR);
  }

  /** 212: 44 params, stand/CWD VARRAYs @20/@21, error @44. */
  @Test
  void getBioPlotsMatchesDeployedSignature() throws Exception {
    repository.getBioPlots("9001");
    String call = capturePreparedCall("frep_212_bioplot.get");
    assertEquals(44, placeholderCount(call), call);
    verify(cs).registerOutParameter(20, Types.ARRAY, "THE.FREP_STAND_TABLE_VARRAY");
    verify(cs).registerOutParameter(21, Types.ARRAY, "THE.FREP_CWD_TABLE_VARRAY");
  }

}
