package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.client.UserLookupClient;
import ca.bc.gov.nrs.frep.client.UserLookupClient.IdirUser;
import ca.bc.gov.nrs.frep.struct.v1.frep.EvaluatorSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the directory service now that it sits on nr-user-lookup-api rather than FAM.
 *
 * <p>The behaviour that matters to callers is unchanged — same two methods, same
 * {@code "First Last (userid)"} display format, and the same degrade-to-the-raw-userid fallback —
 * so the on-screen result did not move when the source did.
 */
class UserDirectoryServiceTest {

  private UserLookupClient client;
  private UserDirectoryService service;

  private static IdirUser user(String id, String first, String last) {
    return new IdirUser(id, "guid-" + id, first, last, id.toLowerCase() + "@gov.bc.ca");
  }

  @BeforeEach
  void setUp() {
    client = mock(UserLookupClient.class);
    service = new UserDirectoryService(client, 25);
  }

  // ── resolveName ───────────────────────────────────────────────────

  @Test
  void resolvesAUseridToTheDisplayFormat() {
    when(client.getIdirDetail("JSMITH")).thenReturn(Optional.of(user("JSMITH", "Jane", "Smith")));

    assertThat(service.resolveName("JSMITH")).contains("Jane Smith (JSMITH)");
  }

  /** nr-user-lookup-api keys on the bare IDIR name, so the stored prefix has to come off. */
  @Test
  void stripsTheDirectoryPrefixBeforeLookingUp() {
    when(client.getIdirDetail("JSMITH")).thenReturn(Optional.of(user("JSMITH", "Jane", "Smith")));

    assertThat(service.resolveName("IDIR\\JSMITH")).contains("Jane Smith (JSMITH)");
    verify(client).getIdirDetail("JSMITH");
  }

  @Test
  void returnsEmptyWhenTheUserIsNotFound() {
    when(client.getIdirDetail("GHOST")).thenReturn(Optional.empty());

    assertThat(service.resolveName("IDIR\\GHOST")).isEmpty();
  }

  /** An upstream failure must not surface — callers show the raw userid instead. */
  @Test
  void returnsEmptyRatherThanThrowingWhenTheLookupFails() {
    when(client.getIdirDetail("JSMITH")).thenThrow(new RuntimeException("connection refused"));

    assertThat(service.resolveName("IDIR\\JSMITH")).isEmpty();
  }

  @Test
  void cachesHitsAndMissesByUserid() {
    when(client.getIdirDetail("JSMITH")).thenReturn(Optional.of(user("JSMITH", "Jane", "Smith")));
    when(client.getIdirDetail("GHOST")).thenReturn(Optional.empty());

    service.resolveName("IDIR\\JSMITH");
    service.resolveName("idir\\jsmith");   // same person, different case
    service.resolveName("IDIR\\GHOST");
    service.resolveName("IDIR\\GHOST");

    verify(client, times(1)).getIdirDetail("JSMITH");
    verify(client, times(1)).getIdirDetail("GHOST");
  }

  /** A transient failure is NOT cached — otherwise one blip hides a name until the pod restarts. */
  @Test
  void doesNotCacheATransientFailure() {
    when(client.getIdirDetail("JSMITH"))
        .thenThrow(new RuntimeException("timeout"))
        .thenReturn(Optional.of(user("JSMITH", "Jane", "Smith")));

    assertThat(service.resolveName("IDIR\\JSMITH")).isEmpty();
    assertThat(service.resolveName("IDIR\\JSMITH")).contains("Jane Smith (JSMITH)");
    verify(client, times(2)).getIdirDetail("JSMITH");
  }

  @Test
  void fallsBackToTheUseridWhenTheDirectoryHasNoName() {
    when(client.getIdirDetail("JSMITH")).thenReturn(Optional.of(user("JSMITH", null, null)));

    assertThat(service.resolveName("JSMITH")).contains("JSMITH");
  }

  // ── searchEvaluators ──────────────────────────────────────────────

  @Test
  void searchesTheDirectoryAndMapsToOptions() {
    when(client.searchIdir(eq("smi"), any(), any(), anyInt()))
        .thenReturn(List.of(user("JSMITH", "Jane", "Smith"), user("ASMITH", "Alan", "Smith")));

    EvaluatorSearchResponse response = service.searchEvaluators("smi", null, null, 1, 25);

    assertThat(response.users()).extracting("code").containsExactly("ASMITH", "JSMITH");
    assertThat(response.users()).extracting("description")
        .containsExactly("Alan Smith (ASMITH)", "Jane Smith (JSMITH)");
    assertThat(response.total()).isEqualTo(2);
  }

  /**
   * No role filtering any more. The FAM integration queried {@code role=FREP_EDITOR}, so the modal
   * only offered people who already held FREP access; nr-user-lookup-api is a plain IDIR directory
   * with no notion of application roles, so anyone in IDIR can be returned and picked.
   */
  @Test
  void returnsAnyIdirUserRatherThanOnlyFrepEditors() {
    when(client.searchIdir(any(), any(), any(), anyInt()))
        .thenReturn(List.of(user("NOROLE", "No", "Role")));

    assertThat(service.searchEvaluators("norole", null, null, 1, 25).users())
        .extracting("code").containsExactly("NOROLE");
  }

  /** The API has no "list everyone" mode, and an unfiltered search helps nobody. */
  @Test
  void returnsAnEmptyPageWhenNoCriteriaAreGiven() {
    EvaluatorSearchResponse response = service.searchEvaluators(" ", null, "", 1, 25);

    assertThat(response.users()).isEmpty();
    assertThat(response.total()).isZero();
    verify(client, never()).searchIdir(any(), any(), any(), anyInt());
  }

  @Test
  void returnsAnEmptyPageRatherThanThrowingWhenTheSearchFails() {
    when(client.searchIdir(any(), any(), any(), anyInt()))
        .thenThrow(new RuntimeException("lookup unavailable"));

    assertThat(service.searchEvaluators("smith", null, null, 1, 25).users()).isEmpty();
  }

  @Test
  void clampsThePageSizeToTheApiBounds() {
    when(client.searchIdir(any(), any(), any(), anyInt())).thenReturn(List.of());

    assertThat(service.searchEvaluators("x", null, null, 1, 5).size()).isEqualTo(10);
    assertThat(service.searchEvaluators("x", null, null, 1, 500).size()).isEqualTo(100);
    assertThat(service.searchEvaluators("x", null, null, 1, 0).size()).isEqualTo(25);
  }
}
