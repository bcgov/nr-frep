package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.security.ChrChecklistAuthorizer;
import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.AssociationsRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeCreateRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeUngroupRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.CompositeUpdateRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.FeatureSaveRequest;
import ca.bc.gov.nrs.frep.struct.v1.frep.FeatureSaveResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.PhotoPageResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.ReleaseCheckoutRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

  /**
   * Remove one feature and everything that hangs off it.
   *
   * <p>Deletion used to be expressed by a feature's <em>absence</em> from the features-section
   * payload. That still holds for the offline check-in path, which posts the whole document; this
   * endpoint is the online equivalent, so the editor no longer has to resend every feature to
   * remove one.
   *
   * <p>{@code revisionCount} travels as a query parameter because DELETE carries no body — the same
   * shape the BIO stratum and plot deletes use. It is the <em>checklist's</em> token, as every other
   * CHR save uses, so a stale one fails the same way a stale section save does.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @DeleteMapping("/checklists/{id}/features/{featureId}")
  ResponseEntity<Void> deleteFeature(
      @PathVariable long id,
      @PathVariable long featureId,
      @RequestParam String revisionCount);

  /**
   * Create a composite over two or more features.
   *
   * <p>A {@code POST} to the collection rather than a feature save naming a parent: the anchor does
   * not exist until this call, so nothing can reference it beforehand.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/composites")
  ResponseEntity<FeatureSaveResponse> createComposite(
      @PathVariable long id, @RequestBody CompositeCreateRequest request);

  /**
   * Re-point an existing composite at a new set of members, and update its own class and source.
   *
   * <p>Separate from {@link #createComposite} because the two differ in more than the anchor: this
   * one can take a feature from another composite, has members to release, and does not require a
   * class or source.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PutMapping("/checklists/{id}/composites/{anchorId}")
  ResponseEntity<FeatureSaveResponse> updateComposite(
      @PathVariable long id,
      @PathVariable long anchorId,
      @RequestBody CompositeUpdateRequest request);

  /**
   * Dissolve a composite: the anchor row goes, its members are released, and any named in the
   * request are deleted.
   *
   * <p>A {@code POST} action rather than {@code DELETE} on the composite: the operation carries a
   * body (which members to delete) and has to stay atomic — one gesture removes 1 + N rows, and
   * splitting it would leave a half-ungrouped composite behind on a failure.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/composites/{anchorId}/ungroup")
  ResponseEntity<FeatureSaveResponse> ungroupComposite(
      @PathVariable long id,
      @PathVariable long anchorId,
      @RequestBody CompositeUngroupRequest request);

  /**
   * Add one standalone feature to a checklist — the editor's Save on a new feature.
   *
   * <p>The path carries {@code /new} because {@code POST /checklists/{id}/features} is already the
   * whole-section save, which cannot be moved: cached frontend bundles still call it, and it is the
   * fallback every per-feature write uses when the checklist is an offline copy.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PostMapping("/checklists/{id}/features/new")
  ResponseEntity<FeatureSaveResponse> createFeature(
      @PathVariable long id, @RequestBody FeatureSaveRequest request);

  /**
   * Save one feature's own fields — the feature editor's Save.
   *
   * <p>Replaces resending every feature to change one. Relationships are left to their own
   * endpoints: see {@link FeatureSaveRequest} for what this deliberately does not touch.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PutMapping("/checklists/{id}/features/{featureId}")
  ResponseEntity<FeatureSaveResponse> saveFeature(
      @PathVariable long id,
      @PathVariable long featureId,
      @RequestBody FeatureSaveRequest request);

  /**
   * Replace the set of features one feature is associated with.
   *
   * <p>Its own endpoint because associating is its own gesture in the UI, with its own dialog and
   * its own save — and because it is not a single-feature write: an association names two features,
   * so the server writes and removes <b>both</b> directions. That symmetry used to be the client's
   * job (it put each label in the other feature's list and posted the whole array), which held only
   * while every feature was in the payload.
   *
   * <p>Returns the features the write touched — the subject and every partner gained or lost — so
   * the caller does not have to re-read the whole checklist to refresh two rows.
   */
  @PreAuthorize("@chrAuth.canEditChecklist(#id)")
  @PutMapping("/checklists/{id}/features/{featureId}/associations")
  ResponseEntity<FeatureSaveResponse> saveFeatureAssociations(
      @PathVariable long id,
      @PathVariable long featureId,
      @RequestBody AssociationsRequest request);

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
