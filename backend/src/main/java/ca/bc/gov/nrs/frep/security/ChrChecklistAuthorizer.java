package ca.bc.gov.nrs.frep.security;

import ca.bc.gov.nrs.frep.service.v1.ChrChecklistPersistenceService;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import org.springframework.stereotype.Component;

/**
 * Resource-aware CHR authorization for {@code @PreAuthorize} expressions (registered as the
 * {@code @chrAuth} bean). Resolves a checklist's district from its persisted org unit and checks the
 * caller's CHR district access, so per-district write gating lives on the endpoint annotation rather
 * than in the service. Sys-admins pass for any district (see {@link LoggedUserHelper#canChr}).
 */
@Component("chrAuth")
public class ChrChecklistAuthorizer {

  private final ChrChecklistPersistenceService persistenceService;
  private final LoggedUserHelper loggedUserHelper;

  public ChrChecklistAuthorizer(
      ChrChecklistPersistenceService persistenceService, LoggedUserHelper loggedUserHelper) {
    this.persistenceService = persistenceService;
    this.loggedUserHelper = loggedUserHelper;
  }

  /** True if the caller may edit the CHR checklist whose id is {@code checklistId}. */
  public boolean canEditChecklist(long checklistId) {
    return loggedUserHelper.canChr(persistenceService.getChecklistOrgUnitCode(checklistId));
  }

  /**
   * Save variant where the id rides in the request body. With no usable id, defer to the coarse
   * "any CHR" gate so the service returns its own 400 (missing id) rather than a 403.
   */
  public boolean canEditChecklist(CheckList checklist) {
    String id = checklist == null ? null : checklist.getChecklistID();
    if (id == null || id.isBlank()) {
      return loggedUserHelper.canAnyChr();
    }
    try {
      return canEditChecklist(Long.parseLong(id.trim()));
    } catch (NumberFormatException ex) {
      return loggedUserHelper.canAnyChr();
    }
  }
}
