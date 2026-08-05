package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Struct;
import java.sql.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MasterListRepositoryImplTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private CallableStatement cs;

  private MasterListRepositoryImpl repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new MasterListRepositoryImpl(jdbcTemplate);
    lenient().when(connection.prepareCall(anyString())).thenReturn(cs);
    lenient().when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
      ConnectionCallback<?> callback = inv.getArgument(0);
      return callback.doInConnection(connection);
    });
  }

  @Test
  void fromGenerationStructParsesLegacyObjectAttributes() throws Exception {
    Object[] attrs = {
        "56", "DCK - Chilliwack Forest District", "3", "5", "38", "12", "N"
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    MasterListGenerationRow row = MasterListRepositoryImpl.fromGenerationStruct(struct);

    assertEquals("56", row.orgUnitNo());
    assertEquals("DCK - Chilliwack Forest District", row.orgUnitDisplay());
    assertEquals(38, row.totalSites());
    assertEquals(12, row.totalAvailableSites());
    assertEquals("N", row.resourceValueInd());
  }

  @Test
  void saveCommentsWiresProc() throws Exception {
    when(cs.getString(4)).thenReturn(null);

    repository.saveComments("2025", "note", "idir");

    verify(connection).prepareCall("{call FREP_700_GEN_MASTER.save_comments (?,?,?,?)}");
    verify(cs).setString(1, "2025");
    verify(cs).setString(2, "note");
    verify(cs).setString(3, "idir");
    verify(cs).registerOutParameter(4, Types.VARCHAR);
  }

  @Test
  void deleteListWiresProc() throws Exception {
    when(cs.getString(2)).thenReturn(null);

    repository.deleteList("2025");

    verify(connection).prepareCall("{call FREP_700_GEN_MASTER.delete_list (?,?)}");
    verify(cs).setString(1, "2025");
    verify(cs).registerOutParameter(2, Types.VARCHAR);
  }
}
