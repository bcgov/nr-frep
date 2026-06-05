package ca.bc.gov.nrs.frep.repository.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.dto.frep.SiteResourceSaveRequest;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Struct;
import java.sql.Types;
import java.util.List;
import oracle.jdbc.OracleConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SiteDetailRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private CallableStatement cs;
  @Mock private OracleConnection oracleConnection;
  @Mock private Array resourceArray;

  private SiteDetailRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new SiteDetailRepository(jdbcTemplate);
    lenient().when(connection.prepareCall(anyString())).thenReturn(cs);
    lenient().when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
      ConnectionCallback<?> callback = inv.getArgument(0);
      return callback.doInConnection(connection);
    });
    lenient().when(cs.getConnection()).thenReturn(connection);
    lenient().when(connection.unwrap(OracleConnection.class)).thenReturn(oracleConnection);
    lenient().when(oracleConnection.createOracleArray(anyString(), any())).thenReturn(resourceArray);
  }

  @Test
  void fromResourceStructParsesLegacyObjectAttributes() throws Exception {
    Object[] attrs = {
        "8001", "R", "SLB", "Biodiversity", "ACC", "RDY", "Within stratum", "", "Field note", "3"
    };
    Struct struct = Mockito.mock(Struct.class);
    Mockito.when(struct.getAttributes()).thenReturn(attrs);

    SiteResourceRow row = SiteDetailRepository.fromResourceStruct(struct);

    assertEquals("8001", row.resourceValueId());
    assertEquals("SLB", row.resourceType());
    assertEquals("ACC", row.statusCode());
    assertEquals("RDY", row.checklistStatusCode());
    assertEquals("Within stratum", row.rationale());
    assertEquals("Field note", row.otherComments());
    assertEquals("3", row.revisionCount());
  }

  @Test
  void saveResourcesWiresSevenParamsAndBuildsResourceArray() throws Exception {
    when(cs.getString(7)).thenReturn(null); // no error
    when(cs.getString(1)).thenReturn("1001"); // site id echoed

    String siteId = repository.saveResources(
        "1001", "987654", "43", "2019",
        List.of(new SiteResourceSaveRequest("8001", "SLB", "ACC", null, "ok", null, "3")),
        "idir");

    verify(connection).prepareCall("{call FREP_110_SITE_DETAILS.SAVE (?,?,?,?,?,?,?)}");
    verify(cs).setString(2, "987654"); // opening id
    verify(cs).setString(3, "43"); // org unit no
    verify(cs).setString(4, "2019"); // effective year
    verify(oracleConnection).createStruct(eq("THE.FREP_RESOURCE_OBJECT"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_RESOURCE_VARRAY"), any());
    assertEquals("1001", siteId);
  }

  @Test
  void saveResourcesNullsBlankNumericAttributesForNewResource() throws Exception {
    when(cs.getString(7)).thenReturn(null);
    when(cs.getString(1)).thenReturn("1001");

    // New resource: blank resourceValueId + blank revisionCount must marshal to null (not ""),
    // or Oracle raises 17059 "fail to convert to internal representation" on the NUMBER attrs.
    repository.saveResources(
        "1001", "987654", "43", "2019",
        List.of(new SiteResourceSaveRequest("", "SLB", "ACC", null, null, null, "")),
        "idir");

    ArgumentCaptor<Object[]> attrs = ArgumentCaptor.forClass(Object[].class);
    verify(oracleConnection).createStruct(eq("THE.FREP_RESOURCE_OBJECT"), attrs.capture());
    Object[] values = attrs.getValue();
    assertNull(values[0]); // resource_id (NUMBER)
    assertEquals("SLB", values[2]); // resource_type
    assertEquals("ACC", values[4]); // stat_code
    assertNull(values[9]); // revision_count (NUMBER)
  }
}
