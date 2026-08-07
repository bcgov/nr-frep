package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentPageResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.security.FrepAuthorities;
import ca.bc.gov.nrs.frep.struct.v1.frep.StratumComputed;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP contract for the protocol checklist read + edit/submit API. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.ProtocolChecklistApiController}.
 *
 * <p>Legacy equivalents: {@code frep210BIOOpeningAction} … {@code frep254WtrSummaryAction};
 * submit/unsubmit via {@code FrepTombstoneAction} / {@code FREP_TOMBSTONE}.
 */
@RequestMapping("/api/v1")
public interface ProtocolChecklistApiEndpoint {

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/{protocolType}/{checklistId}")
  ResponseEntity<ProtocolChecklistResponse> getChecklist(
      @PathVariable String protocolType,
      @PathVariable String checklistId);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping("/protocol-checklists/{protocolType}/{checklistId}/submit")
  ResponseEntity<?> submit(
      @PathVariable String protocolType,
      @PathVariable String checklistId);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping("/protocol-checklists/{protocolType}/{checklistId}/unsubmit")
  ResponseEntity<Void> unsubmit(
      @PathVariable String protocolType,
      @PathVariable String checklistId);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/bio/{checklistId}/opening")
  ResponseEntity<BiodiversityOpening> getBiodiversityOpening(@PathVariable String checklistId);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PutMapping("/protocol-checklists/bio/{checklistId}/opening")
  ResponseEntity<BiodiversityOpening> saveBiodiversityOpening(
      @PathVariable String checklistId,
      @RequestBody BiodiversityOpening opening);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/bio/{checklistId}/strata")
  ResponseEntity<List<BioStratumRow>> listBioStrata(@PathVariable String checklistId);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/bio/strata/{stratumId}")
  ResponseEntity<BioStratum> getBioStratum(@PathVariable String stratumId);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/bio/strata/{stratumId}/computed")
  ResponseEntity<StratumComputed> getStratumComputed(@PathVariable String stratumId);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/bio/{checklistId}/new-stratum-computed")
  ResponseEntity<StratumComputed> getNewStratumComputed(@PathVariable String checklistId);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping("/protocol-checklists/bio/{checklistId}/strata")
  ResponseEntity<BioStratum> saveBioStratum(
      @PathVariable String checklistId,
      @RequestBody BioStratum stratum);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @DeleteMapping("/protocol-checklists/bio/strata/{stratumId}")
  ResponseEntity<Void> deleteBioStratum(
      @PathVariable String stratumId,
      @RequestParam String revisionCount);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/bio/strata/{stratumId}/plots")
  ResponseEntity<List<BioPlotRow>> listBioPlots(@PathVariable String stratumId);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/bio/plots/{plotId}")
  ResponseEntity<BioPlot> getBioPlot(@PathVariable String plotId);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping("/protocol-checklists/bio/strata/{stratumId}/plots")
  ResponseEntity<BioPlot> saveBioPlot(
      @PathVariable String stratumId,
      @RequestBody BioPlot plot);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @DeleteMapping("/protocol-checklists/bio/plots/{plotId}")
  ResponseEntity<Void> deleteBioPlot(
      @PathVariable String plotId,
      @RequestParam String revisionCount);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/{protocol}/{checklistId}/notes")
  ResponseEntity<RiparianNotes> getNotes(
      @PathVariable String protocol, @PathVariable String checklistId);

  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PutMapping("/protocol-checklists/{protocol}/{checklistId}/notes")
  ResponseEntity<RiparianNotes> saveNotes(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @RequestBody RiparianNotes notes);

  /**
   * One page of attachment metadata. Paged so the response can't grow with the attachment count —
   * there is no per-checklist limit — and so the client fetches a bounded number of thumbnails.
   */
  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/{protocol}/{checklistId}/attachments")
  ResponseEntity<AttachmentPageResponse> getAttachments(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size);

  @PreAuthorize(FrepAuthorities.FREP_EDIT)
  @GetMapping("/protocol-checklists/{protocol}/{checklistId}/attachments/{attachmentId}/content")
  ResponseEntity<AttachmentContent> getAttachmentContent(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @PathVariable String attachmentId);

  /**
   * Upload one attachment as {@code multipart/form-data}. Raw bytes on the wire — no base64, so no
   * +33% inflation and no double-materialisation in heap. {@code @RequestParam} (not
   * {@code @RequestPart}) for both the file and the metadata, mirroring nr-fspts: simpler for one
   * file plus scalars, and it binds equally from a multipart form field or the query string. The
   * description is sent as a form field rather than a query parameter because it is free text up to
   * 2000 chars, which would hit URL length limits and land in access logs.
   *
   * <p>Returns {@code 204 No Content}: the attachment list is paginated, so the client re-fetches its
   * current page rather than trusting a whole-list response.
   */
  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @PostMapping(
      value = "/protocol-checklists/{protocol}/{checklistId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<Void> uploadAttachment(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(name = "description", required = false) String description);

  /** Delete an attachment. {@code 204 No Content}; the client re-fetches its page. */
  @PreAuthorize(FrepAuthorities.CONTENT_EDIT)
  @DeleteMapping("/protocol-checklists/{protocol}/{checklistId}/attachments/{attachmentId}")
  ResponseEntity<Void> deleteAttachment(
      @PathVariable String protocol,
      @PathVariable String checklistId,
      @PathVariable String attachmentId);
}
