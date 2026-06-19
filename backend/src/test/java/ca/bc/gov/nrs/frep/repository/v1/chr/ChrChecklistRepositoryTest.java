package ca.bc.gov.nrs.frep.repository.v1.chr;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.util.UuidUtils;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ChrChecklistRepositoryTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  private ChrChecklistRepository repository;

  @BeforeEach
  void setUp() {
    repository = new ChrChecklistRepository(jdbcTemplate);
  }

  @Test
  void getChecklistStatusUsesSqlLookup() {
    when(jdbcTemplate.queryForObject(
        eq("SELECT frep_checklist_status_code FROM the.chr_checklist WHERE chr_checklist_id = ?"),
        eq(String.class),
        eq(1001L)
    )).thenReturn("ACT");

    assertEquals("ACT", repository.getChecklistStatus(1001L));
  }

  @Test
  void parseDeviceCheckoutGuidRoundTripsUuidBytes() {
    UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    byte[] bytes = UuidUtils.asBytes(uuid);
    assertEquals(uuid, repository.parseDeviceCheckoutGuid(bytes));
  }

  @Test
  void parseDeviceCheckoutGuidReturnsNullForEmptyPayload() {
    assertNull(repository.parseDeviceCheckoutGuid(null));
    assertNull(repository.parseDeviceCheckoutGuid(new byte[0]));
  }

  @Test
  void getDeviceCheckoutGuidReadsBytesFromDatabase() {
    UUID uuid = UUID.randomUUID();
    byte[] bytes = UuidUtils.asBytes(uuid);
    when(jdbcTemplate.queryForObject(any(String.class), eq(byte[].class), eq(2002L)))
        .thenReturn(bytes);

    assertEquals(uuid, repository.getDeviceCheckoutGuid(2002L));
  }
}
