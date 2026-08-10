package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.ChrChecklistAuthorizer;
import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.PhotoPageResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.ReleaseCheckoutRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for the Cultural Heritage (CHR) checklist API. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.ChrChecklistApiController}. CHR is strictly
 * district-scoped: every endpoint that names a checklist — <em>reads included</em> — uses
 * {@code @chrAuth.canEditChecklist(...)} ({@link ChrChecklistAuthorizer}), which resolves the
 * checklist's district and checks it against the caller's (sys-admins pass for any district).
 * Activation is {@link FrepAuthorities#ADMIN} (legacy {@code ACTIVATECHECKLIST} is sys-admin only).
 */
@RequestMapping("/api/v1/chr")
public interface ChrChecklistApiEndpoint {

  // District-scoped like every other CHR endpoint: CHR data is visible only to editors who hold the
  // checklist's district (sys-admins see all). The coarse "any CHR" gate used here previously left a
  // direct read open — search and accepted-sites were district-filtered by the same change that
  // introduced per-district access, so the id couldn't be discovered, but it could still be guessed.
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
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

  /**
   * Attach one photo as {@code multipart/form-data}. A leaf resource, not a checklist section: it
   * writes only the attachment row and the stored object, never the checklist entity, so it does not
   * advance the shared {@code revision_count} and cannot conflict with an in-flight checklist edit.
   *
   * <p>Replaces the old whole-{@code CheckList} photos save, which reconciled the entire picture set
   * and therefore deleted every photo whenever a payload arrived without them.
   *
   * <p>{@code deviceCheckoutGuid} is required only while the checklist is checked out (RDO) — the
   * offline check-in flush sends it to prove it holds the checkout. Ignored when the checklist is
   * ACT.
   *
   * <p>{@code featureId} is optional and records which of the checklist's features the photo
   * documents. It is write-once, set here at upload: there is no photo metadata-edit flow, which is
   * also why photo rows carry no optimistic lock.
   *
   * <p>{@code id} must stay named {@code id} and typed {@code long}: the {@code @PreAuthorize}
   * expression binds {@code #id} by parameter name, and {@link ChrChecklistAuthorizer} has a second
   * overload taking a {@code CheckList} that falls back to the coarse "any CHR district" check —
   * a rename or a {@code String} here would silently downgrade authorization rather than fail.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping(value = "/checklists/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<Void> addPhoto(
      @PathVariable long id,
      @RequestParam("file") MultipartFile file,
      @RequestParam("description") String description,
      @RequestParam(name = "date", required = false) String date,
      @RequestParam(name = "featureId", required = false) Long featureId,
      @RequestParam(name = "deviceCheckoutGuid", required = false) String deviceCheckoutGuid);

  /**
   * One page of the checklist's photo metadata — no bytes. Paged so the response can't grow with the
   * photo count, and so the client fetches a bounded number of images per page.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @GetMapping("/checklists/{id}/photos")
  ResponseEntity<PhotoPageResponse> getPhotos(
      @PathVariable long id,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size);

  /**
   * One photo's stored bytes, as a binary download.
   *
   * <p>Replaces embedding every photo's base64 in the checklist GET, which made a single response
   * carry the whole set at ~2.33x stored size. Take-offline uses this too: the client downloads each
   * photo before taking the checkout, so a failed download costs nothing.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @GetMapping("/checklists/{id}/photos/{photoId}/content")
  ResponseEntity<byte[]> getPhotoContent(@PathVariable long id, @PathVariable long photoId);

  /** Remove one photo (row + stored object). Token-neutral, as {@link #addPhoto}. */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @DeleteMapping("/checklists/{id}/photos/{photoId}")
  ResponseEntity<Void> deletePhoto(
      @PathVariable long id,
      @PathVariable long photoId,
      @RequestParam(name = "deviceCheckoutGuid", required = false) String deviceCheckoutGuid);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/submit")
  ResponseEntity<?> submitChecklist(@PathVariable long id, @RequestBody CheckList checklist);

  @PreAuthorize(FrepAuthorities.ADMIN)
  @PostMapping("/checklists/{id}/activate")
  ResponseEntity<Void> activateChecklist(@PathVariable long id);

  // Self-service release of an offline checkout (RDO → ACT): editor-callable, but only succeeds when
  // the request's deviceCheckoutGuid matches the server's, so it releases only the caller's own
  // checkout. Admin activate above is the fallback for a checkout stranded on another device.
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/release")
  ResponseEntity<Void> releaseCheckout(@PathVariable long id, @RequestBody ReleaseCheckoutRequest body);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/offline")
  ResponseEntity<CheckList> takeOffline(@PathVariable long id);

  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/unsubmit")
  ResponseEntity<CheckList> unsubmitChecklist(@PathVariable long id);
}
