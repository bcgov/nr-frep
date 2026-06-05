package ca.bc.gov.nrs.frep.service.chr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.configuration.ChrObjectStorageProperties;
import ca.bc.gov.nrs.frep.service.ChrObjectStorageService;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.exception.ChrRestException;
import ca.bc.gov.nrs.frep.repository.chr.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.dto.frep.CheckList;
import ca.bc.gov.nrs.frep.validation.ChrSubmitValidationService;
import ca.bc.gov.nrs.frep.repository.chr.ChrChecklistRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChrChecklistServiceTest {

  @Mock
  private ChrChecklistPersistenceService persistenceService;
  @Mock
  private ChrChecklistRepository checklistRepository;
  @Mock
  private ChrSubmitValidationService submitValidationService;
  @Mock
  private ChrObjectStorageService objectStorageService;
  @Mock
  private LoggedUserHelper loggedUserHelper;

  private ChrChecklistService service;

  @BeforeEach
  void setUp() {
    service = new ChrChecklistService(
        persistenceService,
        checklistRepository,
        submitValidationService,
        objectStorageService,
        new ChrObjectStorageProperties("http://s3", "bucket", "key", "secret"),
        loggedUserHelper
    );
  }

  @Test
  void saveChecklistRejectsMismatchedStatusTransition() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.SUB);

    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setStatus(ChrConstants.FrepChecklistStatusCode.ACT);
    checklist.setRevisionCount("1");

    assertThrows(ChrRestException.class, () -> service.saveChecklist(checklist));
  }

  @Test
  void submitChecklistPersistsSubmittedStatusWhenValidationPasses() {
    when(loggedUserHelper.canWrite()).thenReturn(true);
    when(loggedUserHelper.getLoggedUserId()).thenReturn("IDIR\\user");
    when(checklistRepository.getChecklistStatus(1001L)).thenReturn(ChrConstants.FrepChecklistStatusCode.ACT);
    when(submitValidationService.validateBeforeSubmit(any())).thenReturn(List.of());

    ChrChecklist entity = new ChrChecklist();
    entity.setChrChecklistId(1001L);
    when(persistenceService.getAcceptedSiteForChr(1001L)).thenReturn(entity);

    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setStatus(ChrConstants.FrepChecklistStatusCode.ACT);
    checklist.setRevisionCount("1");

    assertThrows(ChrRestException.class, () -> service.submitChecklist(1001L, checklist));
    verify(persistenceService).saveChecklist(any(), eq("IDIR\\user"));
  }

  @Test
  void activateChecklistRequiresAdmin() {
    when(loggedUserHelper.isUpdate()).thenReturn(true);
    assertThrows(ChrRestException.class, () -> service.activateChecklist(1001L));
  }
}
