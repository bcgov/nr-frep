package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.AdministrationData;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.struct.v1.frep.StratumComputed;
import java.util.List;

/**
 * Contract for protocol-checklist writes (FREP210/211/212 + administration/notes/attachments +
 * submit/unsubmit). Implemented by
 * {@link ca.bc.gov.nrs.frep.repository.v1.impl.ProtocolChecklistWriteRepositoryImpl}.
 */
public interface ProtocolChecklistWriteRepository {
  String submit(String resourceValueType, String checklistId, String userId);
  String unsubmit(String resourceValueType, String checklistId, String userId);
  BiodiversityOpening getBiodiversityOpening(String checklistId);
  BiodiversityOpening saveBiodiversityOpening(BiodiversityOpening o, String userId);
  List<BioStratumRow> listBioStrata(String checklistId);
  BioStratum getBioStratum(String stratumId);
  BioStratum saveBioStratum(BioStratum s, String userId);
  String deleteBioStratum(String stratumId, String revisionCount);
  StratumComputed getStratumComputed(String stratumId);
  StratumComputed getNewStratumComputed(String checklistId);
  List<BioPlotRow> listBioPlots(String stratumId);
  BioPlot getBioPlot(String plotId);
  BioPlot saveBioPlot(BioPlot p, String userId);
  String deleteBioPlot(String plotId, String revisionCount);
  /** Resolve the parent biodiversity checklist id for a stratum / plot — for the view-only guard. */
  String checklistIdForStratum(String stratumId);
  String checklistIdForPlot(String plotId);
  AdministrationData getAdministration(String checklistId, String resourceType);
  AdministrationData saveAdministration(AdministrationData o, String userId);
  AdministrationData addTeamMember(
      String checklistId, String resourceType, String evaluator, boolean teamLead, String userId);
  AdministrationData deleteTeamMember(
      String checklistId, String resourceType, String evaluatorUserid, String revisionCount);
  RiparianNotes getNotes(String checklistId, String resourceType);
  RiparianNotes saveNotes(RiparianNotes o, String resourceType, String userId);
  List<AttachmentRow> getAttachments(String checklistId, String resourceType);
  AttachmentContent getAttachmentContent(
      String checklistId, String resourceType, String attachmentId);
  void saveAttachment(
      String checklistId, String resourceType, String fileName, String description, String mimeType,
      byte[] bytes, String userId);
  void deleteAttachment(String checklistId, String resourceType, String attachmentId);
}
