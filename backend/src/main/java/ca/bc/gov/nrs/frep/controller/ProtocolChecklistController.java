package ca.bc.gov.nrs.frep.controller;

import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.service.frep.ProtocolChecklistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for protocol checklists.
 *
 * <p>Legacy equivalents: {@code frep210BIOOpeningAction} … {@code frep254WtrSummaryAction}.
 */
@RestController
@RequestMapping("/api/v1")
public class ProtocolChecklistController {

  private final ProtocolChecklistService protocolChecklistService;

  public ProtocolChecklistController(ProtocolChecklistService protocolChecklistService) {
    this.protocolChecklistService = protocolChecklistService;
  }

  @GetMapping("/protocol-checklists/{protocolType}/{checklistId}")
  public ResponseEntity<ProtocolChecklistResponse> getChecklist(
      @PathVariable String protocolType,
      @PathVariable String checklistId
  ) {
    return protocolChecklistService.findChecklist(protocolType, checklistId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
