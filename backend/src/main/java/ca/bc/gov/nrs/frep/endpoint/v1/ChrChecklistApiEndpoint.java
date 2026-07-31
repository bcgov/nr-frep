package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.ChrChecklistAuthorizer;
import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.ReleaseCheckoutRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for the Cultural Heritage (CHR) checklist API. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.ChrChecklistApiController}. Reads require the coarse
 * {@link FrepAuthorities#CHR_EDIT} (sys-admin or any CHR district editor); writes require the
 * per-district gate {@code @chrAuth.canEditChecklist(...)} ({@link ChrChecklistAuthorizer}), which
 * resolves the checklist's district; activation is {@link FrepAuthorities#ADMIN} (legacy
 * {@code ACTIVATECHECKLIST} is sys-admin only).
 */
@RequestMapping("/api/v1/chr")
public interface ChrChecklistApiEndpoint {

  @PreAuthorize(FrepAuthorities.CHR_EDIT)
  @GetMapping("/checklists/{id}")
  ResponseEntity<CheckList> getChecklist(@PathVariable long id);

  @PreAuthorize("@chrAuth.canEditChecklist(#checklist)")
  @PostMapping("/checklists")
  ResponseEntity<CheckList> saveChecklist(@RequestBody CheckList checklist);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/opening")
  ResponseEntity<CheckList> saveOpening(@PathVariable long id, @RequestBody CheckList checklist);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/block-summary")
  ResponseEntity<CheckList> saveBlockSummary(@PathVariable long id, @RequestBody CheckList checklist);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/contacts")
  ResponseEntity<CheckList> saveContacts(@PathVariable long id, @RequestBody CheckList checklist);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/features")
  ResponseEntity<CheckList> saveFeatures(@PathVariable long id, @RequestBody CheckList checklist);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/photos")
  ResponseEntity<CheckList> savePhotos(@PathVariable long id, @RequestBody CheckList checklist);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/submit")
  ResponseEntity<?> submitChecklist(@PathVariable long id, @RequestBody CheckList checklist);

  @PreAuthorize(FrepAuthorities.ADMIN)
  @PostMapping("/checklists/{id}/activate")
  ResponseEntity<CheckList> activateChecklist(@PathVariable long id);

  // Self-service release of an offline checkout (RDO → ACT): editor-callable, but only succeeds when
  // the request's deviceCheckoutGuid matches the server's, so it releases only the caller's own
  // checkout. Admin activate above is the fallback for a checkout stranded on another device.
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/release")
  ResponseEntity<CheckList> releaseCheckout(@PathVariable long id, @RequestBody ReleaseCheckoutRequest body);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/offline")
  ResponseEntity<CheckList> takeOffline(@PathVariable long id);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/unsubmit")
  ResponseEntity<CheckList> unsubmitChecklist(@PathVariable long id);
}
