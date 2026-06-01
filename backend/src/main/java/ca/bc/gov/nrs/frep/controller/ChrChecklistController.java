package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.CheckList;
import ca.bc.gov.nrs.frep.service.chr.ChrChecklistService;
import ca.bc.gov.nrs.frep.service.chr.ChrChecklistService.ChrSubmitValidationException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chr")
@Profile("oracle")
public class ChrChecklistController {

  private final ChrChecklistService chrChecklistService;

  public ChrChecklistController(ChrChecklistService chrChecklistService) {
    this.chrChecklistService = chrChecklistService;
  }

  @GetMapping("/checklists/{id}")
  public ResponseEntity<CheckList> getChecklist(@PathVariable long id) {
    return ResponseEntity.ok(chrChecklistService.getChecklist(id));
  }

  @PostMapping("/checklists")
  public ResponseEntity<CheckList> saveChecklist(@RequestBody CheckList checklist) {
    return ResponseEntity.ok(chrChecklistService.saveChecklist(checklist));
  }

  @PostMapping("/checklists/{id}/submit")
  public ResponseEntity<?> submitChecklist(
      @PathVariable long id,
      @RequestBody CheckList checklist
  ) {
    try {
      return ResponseEntity.ok(chrChecklistService.submitChecklist(id, checklist));
    } catch (ChrSubmitValidationException ex) {
      return ResponseEntity.badRequest().body(ex.getValidationErrors());
    }
  }

  @PostMapping("/checklists/{id}/activate")
  public ResponseEntity<CheckList> activateChecklist(@PathVariable long id) {
    return ResponseEntity.ok(chrChecklistService.activateChecklist(id));
  }

  @PostMapping("/checklists/{id}/offline")
  public ResponseEntity<CheckList> takeOffline(@PathVariable long id) {
    return ResponseEntity.ok(chrChecklistService.takeOffline(id));
  }

  @PostMapping("/checklists/{id}/unsubmit")
  public ResponseEntity<CheckList> unsubmitChecklist(@PathVariable long id) {
    return ResponseEntity.ok(chrChecklistService.unsubmitChecklist(id));
  }
}
