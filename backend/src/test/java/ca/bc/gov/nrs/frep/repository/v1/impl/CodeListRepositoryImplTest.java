package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the proc name + positional parameter wiring for code-list cursor procedures. */
@ExtendWith(MockitoExtension.class)
class CodeListRepositoryImplTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private CallableStatement cs;

  private CodeListRepositoryImpl repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new CodeListRepositoryImpl(jdbcTemplate);
    lenient().when(connection.prepareCall(anyString())).thenReturn(cs);
    lenient().when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
      ConnectionCallback<?> callback = inv.getArgument(0);
      return callback.doInConnection(connection);
    });
  }

  @Test
  void searchBecCallsBgcSearchMainlineWithGetActionAndCursor() throws Exception {
    // No cursor object is stubbed → readCursor returns an empty list; we only assert the binding.
    var result = repository.searchBec("idf", null, null, null, null, null, null);

    assertTrue(result.isEmpty());
    verify(connection).prepareCall("{call FREP_52_BGC_SEARCH.mainline(?,?,?,?,?,?,?,?,?,?)}");
    verify(cs).setString(1, "GET"); // p_action
    verify(cs).setString(2, "idf"); // p_bgc_zone_code
    verify(cs).setNull(3, Types.VARCHAR); // null criterion bound as NULL
    verify(cs).registerOutParameter(9, Types.VARCHAR); // p_error_message (IN OUT)
    verify(cs).registerOutParameter(10, OracleTypes.CURSOR); // p_results
  }

  @Test
  void getFrepSpeciesCodeCallsProcWithCursor() throws Exception {
    assertTrue(repository.getFrepSpeciesCode().isEmpty());
    verify(connection).prepareCall("{call FREP_CODE_LISTS.get_frep_species_code(?)}");
    verify(cs).registerOutParameter(1, OracleTypes.CURSOR);
  }

  @Test
  void getWildlifeTreeDecayCodeCallsProcWithCursor() throws Exception {
    assertTrue(repository.getWildlifeTreeDecayCode().isEmpty());
    verify(connection).prepareCall("{call FREP_CODE_LISTS.get_wildlife_tree_decay_code(?)}");
    verify(cs).registerOutParameter(1, OracleTypes.CURSOR);
  }

  @Test
  void getCwdDecayClassCodeCallsProcWithCursor() throws Exception {
    assertTrue(repository.getCwdDecayClassCode().isEmpty());
    verify(connection).prepareCall("{call FREP_CODE_LISTS.get_cwd_decay_class_code(?)}");
    verify(cs).registerOutParameter(1, OracleTypes.CURSOR);
  }

  @Test
  void getEvaluatorCodeBindsChecklistAndResourceTypeThenCursor() throws Exception {
    assertTrue(repository.getEvaluatorCode("1001", "SLB").isEmpty());
    verify(connection).prepareCall("{call FREP_CODE_LISTS.get_evaluator_code(?,?,?)}");
    verify(cs).setString(1, "1001"); // p_checklist_id
    verify(cs).setString(2, "SLB"); // p_resource_type_code
    verify(cs).registerOutParameter(3, OracleTypes.CURSOR); // p_evaluator_code
  }
}
