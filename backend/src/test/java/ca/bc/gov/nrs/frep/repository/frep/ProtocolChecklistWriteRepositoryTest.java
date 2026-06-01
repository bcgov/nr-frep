package ca.bc.gov.nrs.frep.repository.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the proc name + positional parameter wiring for the protocol write procedures. */
@ExtendWith(MockitoExtension.class)
class ProtocolChecklistWriteRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private CallableStatement cs;

  private ProtocolChecklistWriteRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new ProtocolChecklistWriteRepository(jdbcTemplate);
    lenient().when(connection.prepareCall(anyString())).thenReturn(cs);
    // Run the ConnectionCallback against the mocked connection/statement.
    lenient().when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
      ConnectionCallback<?> callback = inv.getArgument(0);
      return callback.doInConnection(connection);
    });
  }

  @Test
  void submitCallsSubmissionValidationWithResourceType() throws Exception {
    when(cs.getString(4)).thenReturn("");

    String error = repository.submit("SLB", "1001", "idir");

    verify(connection).prepareCall("{call FREP_TOMBSTONE.FREP_SUBMISSION_VALIDATION(?,?,?,?)}");
    verify(cs).setString(1, "SLB");
    verify(cs).setString(2, "1001");
    verify(cs).setString(3, "idir");
    verify(cs).registerOutParameter(4, Types.VARCHAR);
    assertEquals("", error);
  }

  @Test
  void unsubmitCallsUnsubmitProc() throws Exception {
    when(cs.getString(4)).thenReturn("");

    repository.unsubmit("RIP", "2002", "idir");

    verify(connection).prepareCall("{call FREP_TOMBSTONE.UNSUBMIT(?,?,?,?)}");
    verify(cs).setString(1, "RIP");
    verify(cs).setString(2, "2002");
  }

  @Test
  void saveBiodiversityOpeningWiresSixteenParamsAndEchoesIdentity() throws Exception {
    when(cs.getString(16)).thenReturn(null); // no error
    when(cs.getString(1)).thenReturn("1001"); // checklist id echoed
    when(cs.getString(14)).thenReturn("6"); // revision incremented

    BiodiversityOpening in = new BiodiversityOpening(
        "1001", "500", "ACT", "N", "loc", "Y", "N", "Y", "innov", "N", "inv", "W", "opinion", "5");

    BiodiversityOpening out = repository.saveBiodiversityOpening(in, "idir");

    verify(connection).prepareCall("{call frep_210_bio_opening.SAVE(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
    verify(cs).setString(3, "ACT"); // status
    verify(cs).setString(5, "loc"); // location description
    verify(cs).setString(12, "W"); // site evaluation code
    verify(cs).setString(15, "idir"); // update userid
    verify(cs).registerOutParameter(16, Types.VARCHAR); // error message
    assertEquals("1001", out.checklistId());
    assertEquals("6", out.revisionCount());
  }
}
