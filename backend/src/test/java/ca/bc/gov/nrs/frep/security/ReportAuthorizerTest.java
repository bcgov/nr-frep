package ca.bc.gov.nrs.frep.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.struct.v1.report.ReportRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Protocol + district authorization for the CSV data-extract report {@code @PreAuthorize} gate. */
@ExtendWith(MockitoExtension.class)
class ReportAuthorizerTest {

  @Mock
  private LoggedUserHelper loggedUserHelper;
  @InjectMocks
  private ReportAuthorizer authorizer;

  private static ReportRequest req(String orgUnitCode) {
    return new ReportRequest(
        null, null, null, orgUnitCode, null, null, null, null, null, null, null, null);
  }

  @Test
  void chrExtractDeniedWithoutChrAccess() {
    when(loggedUserHelper.canAnyChr()).thenReturn(false);
    assertThat(authorizer.canGenerate("chr-data-extract", req("DCK"))).isFalse();
  }

  @Test
  void chrExtractDeniedWhenDistrictUserSelectsAll() {
    when(loggedUserHelper.canAnyChr()).thenReturn(true);
    when(loggedUserHelper.isSysAdmin()).thenReturn(false);
    assertThat(authorizer.canGenerate("chr-data-extract", req("*"))).isFalse();
  }

  @Test
  void chrExtractDeniedForADistrictTheUserLacks() {
    when(loggedUserHelper.canAnyChr()).thenReturn(true);
    when(loggedUserHelper.isSysAdmin()).thenReturn(false);
    when(loggedUserHelper.canChr("DCC")).thenReturn(false);
    assertThat(authorizer.canGenerate("chr-data-extract", req("DCC"))).isFalse();
  }

  @Test
  void chrExtractAllowedForTheUsersOwnDistrict() {
    when(loggedUserHelper.canAnyChr()).thenReturn(true);
    when(loggedUserHelper.isSysAdmin()).thenReturn(false);
    when(loggedUserHelper.canChr("DCK")).thenReturn(true);
    assertThat(authorizer.canGenerate("chr-data-extract", req("DCK"))).isTrue();
  }

  @Test
  void adminMayGenerateTheChrExtractForAnyDistrict() {
    when(loggedUserHelper.canAnyChr()).thenReturn(true);
    when(loggedUserHelper.isSysAdmin()).thenReturn(true);
    assertThat(authorizer.canGenerate("chr-data-extract", req("*"))).isTrue();
  }

  @Test
  void biodiversityExtractRequiresFrepEdit() {
    when(loggedUserHelper.canEdit()).thenReturn(false);
    assertThat(authorizer.canGenerate("biodiversity-extract-block", req(null))).isFalse();
  }

  @Test
  void biodiversityExtractAllowedForAnEditor() {
    when(loggedUserHelper.canEdit()).thenReturn(true);
    assertThat(authorizer.canGenerate("biodiversity-extract-block", req(null))).isTrue();
  }
}
