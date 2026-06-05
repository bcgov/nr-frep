package ca.bc.gov.nrs.frep.repository.frep;

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

  /**
   * Regression: {@code getRipFieldData} must match the deployed FREP_231_FIELD_DATA.GET signature
   * (per legacy RiparianChecklistDataManager): 26 params, with the point/continuous indicator
   * VARRAYs at params 25/26. A prior version declared 26 placeholders but registered param 27, and
   * a later one declared 27 placeholders — both mismatch the real 26-param proc.
   */
  @Test
  void getRipFieldDataCallsProcWith26ParamsAndArraysAt25And26() throws Exception {
    repository.getRipFieldData("2002");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareCall(sql.capture());
    String call = sql.getValue();
    assertTrue(call.contains("FREP_231_FIELD_DATA.GET"), call);
    assertEquals(26, call.chars().filter(c -> c == '?').count(), call);
    verify(cs).registerOutParameter(25, java.sql.Types.ARRAY, "THE.FREP_POINT_INDICATOR_VARRAY");
    verify(cs).registerOutParameter(26, java.sql.Types.ARRAY, "THE.FREP_CONTINUOUS_IND_VARRAY");
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

  /** 230: 69 params, stream-edge VARRAY @31, checklist_id IN @23 (params 19-21 must be bound). */
  @Test
  void getRipStreamOpeningMatchesDeployedSignature() throws Exception {
    repository.getRipStreamOpening("2002");
    String call = capturePreparedCall("frep_230_strm_open.get");
    assertEquals(69, placeholderCount(call), call);
    verify(cs).setString(23, "2002");
    verify(cs).registerOutParameter(31, Types.ARRAY, "THE.FREP_STRM_EDGE_MEASMNT_VARRAY");
    // param 21 now bound (the tombstone-merge call also binds 1-23, so allow >1)
    verify(cs, org.mockito.Mockito.atLeastOnce()).registerOutParameter(21, Types.VARCHAR);
  }

  /** 235: 30 params, checklist_id IN @21, error @30. */
  @Test
  void getRipFinalCommentsMatchesDeployedSignature() throws Exception {
    repository.getRipFinalComments("2002");
    String call = capturePreparedCall("frep_235_final_cmts.get");
    assertEquals(30, placeholderCount(call), call);
    verify(cs).setString(21, "2002");
  }
}
