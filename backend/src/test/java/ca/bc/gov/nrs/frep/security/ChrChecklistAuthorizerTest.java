package ca.bc.gov.nrs.frep.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.service.v1.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Per-district CHR write authorization used by the {@code @chrAuth} {@code @PreAuthorize} gate. */
@ExtendWith(MockitoExtension.class)
class ChrChecklistAuthorizerTest {

  @Mock
  private ChrChecklistPersistenceService persistenceService;
  @Mock
  private LoggedUserHelper loggedUserHelper;
  @InjectMocks
  private ChrChecklistAuthorizer authorizer;

  @Test
  void allowsWhenTheCallerHasTheChecklistDistrict() {
    when(persistenceService.getChecklistOrgUnitCode(5L)).thenReturn("DCK");
    when(loggedUserHelper.canChr("DCK")).thenReturn(true);

    assertThat(authorizer.canEditChecklist(5L)).isTrue();
  }

  @Test
  void deniesWhenTheCallerLacksTheChecklistDistrict() {
    when(persistenceService.getChecklistOrgUnitCode(5L)).thenReturn("DCC");
    when(loggedUserHelper.canChr("DCC")).thenReturn(false);

    assertThat(authorizer.canEditChecklist(5L)).isFalse();
  }

  @Test
  void bodyVariantResolvesTheIdFromTheRequestBody() {
    CheckList checklist = new CheckList();
    checklist.setChecklistID("5");
    when(persistenceService.getChecklistOrgUnitCode(5L)).thenReturn("DCK");
    when(loggedUserHelper.canChr("DCK")).thenReturn(true);

    assertThat(authorizer.canEditChecklist(checklist)).isTrue();
  }

  @Test
  void bodyVariantWithNoIdFallsBackToTheCoarseAnyChrGate() {
    // No id yet — let the service return its own 400 (missing id) instead of a 403.
    when(loggedUserHelper.canAnyChr()).thenReturn(true);

    assertThat(authorizer.canEditChecklist(new CheckList())).isTrue();
  }
}
