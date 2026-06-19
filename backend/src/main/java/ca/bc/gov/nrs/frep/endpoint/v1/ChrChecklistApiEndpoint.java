package ca.bc.gov.nrs.frep.endpoint.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for the Cultural Heritage (CHR) checklist API. Implemented by
 * {@link ca.bc.gov.nrs.frep.controller.v1.ChrChecklistApiController}.
 */
@RequestMapping("/api/v1/chr")
public interface ChrChecklistApiEndpoint {

  @GetMapping("/checklists/{id}")
  ResponseEntity<CheckList> getChecklist(@PathVariable long id);

  @PostMapping("/checklists")
  ResponseEntity<CheckList> saveChecklist(@RequestBody CheckList checklist);

  @PostMapping("/checklists/{id}/opening")
  ResponseEntity<CheckList> saveOpening(@PathVariable long id, @RequestBody CheckList checklist);

  @PostMapping("/checklists/{id}/block-summary")
  ResponseEntity<CheckList> saveBlockSummary(@PathVariable long id, @RequestBody CheckList checklist);

  @PostMapping("/checklists/{id}/contacts")
  ResponseEntity<CheckList> saveContacts(@PathVariable long id, @RequestBody CheckList checklist);

  @PostMapping("/checklists/{id}/features")
  ResponseEntity<CheckList> saveFeatures(@PathVariable long id, @RequestBody CheckList checklist);

  @PostMapping("/checklists/{id}/photos")
  ResponseEntity<CheckList> savePhotos(@PathVariable long id, @RequestBody CheckList checklist);

  @PostMapping("/checklists/{id}/submit")
  ResponseEntity<?> submitChecklist(@PathVariable long id, @RequestBody CheckList checklist);

  @PostMapping("/checklists/{id}/activate")
  ResponseEntity<CheckList> activateChecklist(@PathVariable long id);

  @PostMapping("/checklists/{id}/offline")
  ResponseEntity<CheckList> takeOffline(@PathVariable long id);

  @PostMapping("/checklists/{id}/unsubmit")
  ResponseEntity<CheckList> unsubmitChecklist(@PathVariable long id);
}
