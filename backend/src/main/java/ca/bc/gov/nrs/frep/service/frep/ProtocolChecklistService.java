package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.BioPlot;
import ca.bc.gov.nrs.frep.dto.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.dto.frep.BioStratum;
import ca.bc.gov.nrs.frep.dto.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.dto.frep.RiparianFieldData;
import ca.bc.gov.nrs.frep.dto.frep.RiparianFinalComments;
import ca.bc.gov.nrs.frep.dto.frep.RiparianOtherIndicators;
import ca.bc.gov.nrs.frep.dto.frep.RiparianQuestions;
import ca.bc.gov.nrs.frep.dto.frep.RiparianSpecificImpacts;
import ca.bc.gov.nrs.frep.dto.frep.RiparianStreamOpening;
import ca.bc.gov.nrs.frep.dto.frep.WaterAssessment;
import ca.bc.gov.nrs.frep.dto.frep.WaterRange;
import ca.bc.gov.nrs.frep.dto.frep.WaterSampleArea;
import ca.bc.gov.nrs.frep.dto.frep.WaterSampleSite;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistField;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistSection;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistHeaderData;
import ca.bc.gov.nrs.frep.dto.frep.AdministrationData;
import ca.bc.gov.nrs.frep.dto.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.dto.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.dto.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistRepository;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistSectionData;
import ca.bc.gov.nrs.frep.repository.frep.CodeListRepository;
import ca.bc.gov.nrs.frep.repository.frep.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Protocol checklist lookup backed by legacy Oracle GET procedures via
 * {@link ChecklistRepository}.
 */
@Service
@Profile("oracle")
public class ProtocolChecklistService {

  private final ChecklistRepository checklistRepository;
  private final CodeListRepository codeListRepository;
  private final ProtocolChecklistWriteRepository writeRepository;
  private final LoggedUserHelper loggedUserHelper;

  public ProtocolChecklistService(
      ChecklistRepository checklistRepository,
      CodeListRepository codeListRepository,
      ProtocolChecklistWriteRepository writeRepository,
      LoggedUserHelper loggedUserHelper
  ) {
    this.checklistRepository = checklistRepository;
    this.codeListRepository = codeListRepository;
    this.writeRepository = writeRepository;
    this.loggedUserHelper = loggedUserHelper;
  }

  /** Submit a protocol checklist (server-side DB validation + status to SUB). */
  public void submit(String protocolType, String checklistId) {
    String resourceType = resolveResourceType(protocolType);
    assertCanWrite();
    String error = writeRepository.submit(resourceType, checklistId, loggedUserHelper.getLoggedUserId());
    if (StringUtils.isNotBlank(error)) {
      throw new ProtocolSubmitValidationException(splitValidationMessages(error));
    }
  }

  /** Revert a submitted checklist to ACT. */
  public void unsubmit(String protocolType, String checklistId) {
    String resourceType = resolveResourceType(protocolType);
    assertCanWrite();
    String error = writeRepository.unsubmit(resourceType, checklistId, loggedUserHelper.getLoggedUserId());
    if (StringUtils.isNotBlank(error)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
  }

  /** Typed read of the Biodiversity Opening screen for editing. */
  public BiodiversityOpening getBiodiversityOpening(String checklistId) {
    BiodiversityOpening opening = writeRepository.getBiodiversityOpening(checklistId);
    if (opening == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Biodiversity checklist not found: " + checklistId);
    }
    return opening;
  }

  /** Save the Biodiversity Opening screen via FREP_210_BIO_OPENING.SAVE. */
  public BiodiversityOpening saveBiodiversityOpening(String checklistId, BiodiversityOpening opening) {
    assertCanWrite();
    BiodiversityOpening toSave = opening.checklistId() == null
        ? opening.withIdentity(checklistId, opening.revisionCount())
        : opening;
    return writeRepository.saveBiodiversityOpening(toSave, loggedUserHelper.getLoggedUserId());
  }

  public List<BioStratumRow> listBioStrata(String checklistId) {
    return writeRepository.listBioStrata(checklistId);
  }

  public BioStratum getBioStratum(String stratumId) {
    BioStratum stratum = writeRepository.getBioStratum(stratumId);
    if (stratum == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stratum not found: " + stratumId);
    }
    return stratum;
  }

  public BioStratum saveBioStratum(BioStratum stratum) {
    assertCanWrite();
    return writeRepository.saveBioStratum(stratum, loggedUserHelper.getLoggedUserId());
  }

  public void deleteBioStratum(String stratumId, String revisionCount) {
    assertCanWrite();
    String error = writeRepository.deleteBioStratum(stratumId, revisionCount);
    if (StringUtils.isNotBlank(error)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
  }

  public String nextStratumNumber() {
    assertCanWrite();
    return writeRepository.nextStratumNumber();
  }

  // --- Biodiversity plots (FREP screen 212) ---

  public List<BioPlotRow> listBioPlots(String stratumId) {
    return writeRepository.listBioPlots(stratumId);
  }

  public BioPlot getBioPlot(String plotId) {
    BioPlot plot = writeRepository.getBioPlot(plotId);
    if (plot == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plot not found: " + plotId);
    }
    return plot;
  }

  public BioPlot saveBioPlot(BioPlot plot) {
    assertCanWrite();
    return writeRepository.saveBioPlot(plot, loggedUserHelper.getLoggedUserId());
  }

  public void deleteBioPlot(String plotId, String revisionCount) {
    assertCanWrite();
    String error = writeRepository.deleteBioPlot(plotId, revisionCount);
    if (StringUtils.isNotBlank(error)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
  }

  // --- Riparian stream opening (FREP screen 230) ---

  public RiparianStreamOpening getRipStreamOpening(String checklistId) {
    RiparianStreamOpening opening = writeRepository.getRipStreamOpening(checklistId);
    if (opening == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Riparian checklist not found: " + checklistId);
    }
    return opening;
  }

  public RiparianStreamOpening saveRipStreamOpening(String checklistId, RiparianStreamOpening opening) {
    assertCanWrite();
    return writeRepository.saveRipStreamOpening(
        opening.withChecklist(checklistId), loggedUserHelper.getLoggedUserId());
  }

  // --- Administration / Notes / Attachments (shared across bio / riparian / water) ---
  //
  // The {protocol} path segment ('bio'/'rip'/'wat') maps to the resource value type used by the
  // shared procs.

  static String resourceTypeForProtocol(String protocol) {
    return normalizeProtocolType(protocol)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Unknown protocol: " + protocol));
  }

  public AdministrationData getAdministration(String protocol, String checklistId) {
    return writeRepository.getAdministration(checklistId, resourceTypeForProtocol(protocol));
  }

  public AdministrationData saveAdministration(String protocol, AdministrationData admin) {
    assertCanWrite();
    return writeRepository.saveAdministration(admin, loggedUserHelper.getLoggedUserId());
  }

  public AdministrationData addTeamMember(
      String protocol, String checklistId, String evaluator, boolean teamLead) {
    assertCanWrite();
    return writeRepository.addTeamMember(checklistId, resourceTypeForProtocol(protocol), evaluator,
        teamLead, loggedUserHelper.getLoggedUserId());
  }

  public AdministrationData removeTeamMember(
      String protocol, String checklistId, String evaluatorUserid, String revisionCount) {
    assertCanWrite();
    return writeRepository.deleteTeamMember(
        checklistId, resourceTypeForProtocol(protocol), evaluatorUserid, revisionCount);
  }

  public RiparianNotes getNotes(String protocol, String checklistId) {
    return writeRepository.getNotes(checklistId, resourceTypeForProtocol(protocol));
  }

  public RiparianNotes saveNotes(String protocol, String checklistId, RiparianNotes notes) {
    assertCanWrite();
    return writeRepository.saveNotes(
        notes, resourceTypeForProtocol(protocol), loggedUserHelper.getLoggedUserId());
  }

  public List<AttachmentRow> getAttachments(String protocol, String checklistId) {
    return writeRepository.getAttachments(checklistId, resourceTypeForProtocol(protocol));
  }

  public AttachmentContent getAttachmentContent(
      String protocol, String checklistId, String attachmentId) {
    return writeRepository.getAttachmentContent(
        checklistId, resourceTypeForProtocol(protocol), attachmentId);
  }

  public List<AttachmentRow> saveAttachment(
      String protocol, String checklistId, String fileName, String description, String mimeType,
      byte[] bytes) {
    assertCanWrite();
    String resourceType = resourceTypeForProtocol(protocol);
    writeRepository.saveAttachment(checklistId, resourceType, fileName, description, mimeType, bytes,
        loggedUserHelper.getLoggedUserId());
    return writeRepository.getAttachments(checklistId, resourceType);
  }

  public List<AttachmentRow> deleteAttachment(
      String protocol, String checklistId, String attachmentId) {
    assertCanWrite();
    String resourceType = resourceTypeForProtocol(protocol);
    writeRepository.deleteAttachment(checklistId, resourceType, attachmentId);
    return writeRepository.getAttachments(checklistId, resourceType);
  }

  // --- Riparian final comments (FREP screen 235) ---

  public RiparianFinalComments getRipFinalComments(String checklistId) {
    RiparianFinalComments comments = writeRepository.getRipFinalComments(checklistId);
    if (comments == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Riparian checklist not found: " + checklistId);
    }
    return comments;
  }

  public RiparianFinalComments saveRipFinalComments(String checklistId, RiparianFinalComments comments) {
    assertCanWrite();
    return writeRepository.saveRipFinalComments(
        comments.withChecklist(checklistId), loggedUserHelper.getLoggedUserId());
  }

  // --- Riparian field data / other indicators / questions / specific impacts (231-234) ---

  public RiparianFieldData getRipFieldData(String checklistId) {
    RiparianFieldData data = writeRepository.getRipFieldData(checklistId);
    if (data == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Riparian checklist not found: " + checklistId);
    }
    return data;
  }

  public RiparianFieldData saveRipFieldData(String checklistId, RiparianFieldData data) {
    assertCanWrite();
    return writeRepository.saveRipFieldData(
        data.withChecklist(checklistId), loggedUserHelper.getLoggedUserId());
  }

  public RiparianOtherIndicators getRipOtherIndicators(String checklistId) {
    return writeRepository.getRipOtherIndicators(checklistId);
  }

  public RiparianOtherIndicators saveRipOtherIndicators(String checklistId, RiparianOtherIndicators data) {
    assertCanWrite();
    return writeRepository.saveRipOtherIndicators(
        data.withChecklist(checklistId), loggedUserHelper.getLoggedUserId());
  }

  public RiparianQuestions getRipQuestions(String checklistId) {
    return writeRepository.getRipQuestions(checklistId);
  }

  public RiparianQuestions saveRipQuestions(String checklistId, RiparianQuestions data) {
    assertCanWrite();
    return writeRepository.saveRipQuestions(
        data.withChecklist(checklistId), loggedUserHelper.getLoggedUserId());
  }

  public RiparianSpecificImpacts getRipSpecificImpacts(String checklistId) {
    return writeRepository.getRipSpecificImpacts(checklistId);
  }

  public RiparianSpecificImpacts saveRipSpecificImpacts(String checklistId, RiparianSpecificImpacts data) {
    assertCanWrite();
    return writeRepository.saveRipSpecificImpacts(
        data.withChecklist(checklistId), loggedUserHelper.getLoggedUserId());
  }

  // --- Water (FREP screens 250-253) ---

  public WaterSampleArea getWaterSampleArea(String checklistId) {
    WaterSampleArea area = writeRepository.getWaterSampleArea(checklistId);
    if (area == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Water checklist not found: " + checklistId);
    }
    return area;
  }

  public WaterSampleArea saveWaterSampleArea(String checklistId, WaterSampleArea area) {
    assertCanWrite();
    return writeRepository.saveWaterSampleArea(
        area.withChecklist(checklistId), loggedUserHelper.getLoggedUserId());
  }

  public WaterSampleSite getWaterSampleSite(String checklistId) {
    WaterSampleSite site = writeRepository.getWaterSampleSite(checklistId);
    if (site == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Water sample site not found: " + checklistId);
    }
    return site;
  }

  public WaterSampleSite saveWaterSampleSite(String checklistId, WaterSampleSite site) {
    assertCanWrite();
    return writeRepository.saveWaterSampleSite(
        site.withChecklist(checklistId), loggedUserHelper.getLoggedUserId());
  }

  public WaterAssessment getWaterAssessment(String sampleSiteId) {
    return writeRepository.getWaterAssessment(sampleSiteId);
  }

  public WaterAssessment saveWaterAssessment(String sampleSiteId, WaterAssessment data) {
    assertCanWrite();
    return writeRepository.saveWaterAssessment(
        data.withSampleSite(sampleSiteId), loggedUserHelper.getLoggedUserId());
  }

  public WaterRange getWaterRange(String sampleSiteId) {
    return writeRepository.getWaterRange(sampleSiteId);
  }

  public WaterRange saveWaterRange(String sampleSiteId, WaterRange data) {
    assertCanWrite();
    return writeRepository.saveWaterRange(
        data.withSampleSite(sampleSiteId), loggedUserHelper.getLoggedUserId());
  }

  private String resolveResourceType(String protocolType) {
    return normalizeProtocolType(protocolType)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Unknown protocol type: " + protocolType));
  }

  private void assertCanWrite() {
    if (!loggedUserHelper.canWrite()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to edit checklists.");
    }
  }

  /** Legacy returns validation failures as a {@code ;}-separated list of message codes. */
  private static List<String> splitValidationMessages(String error) {
    return Arrays.stream(error.split(";"))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .toList();
  }

  public Optional<ProtocolChecklistResponse> findChecklist(String protocolType, String checklistId) {
    if (StringUtils.isBlank(protocolType) || StringUtils.isBlank(checklistId)) {
      return Optional.empty();
    }
    Optional<String> normalizedProtocol = normalizeProtocolType(protocolType);
    if (normalizedProtocol.isEmpty()) {
      return Optional.empty();
    }

    String oracleProtocol = normalizedProtocol.get();
    Map<String, String> protocolNames = loadProtocolNames();
    List<SectionDefinition> sections = switch (oracleProtocol) {
      case "SLB" -> bioSections(checklistId);
      case "RIP" -> ripSections(checklistId);
      case "WTR" -> wtrSections(checklistId);
      default -> List.of();
    };

    if (sections.isEmpty()) {
      return Optional.empty();
    }

    ChecklistHeaderData header = ChecklistHeaderData.empty();
    List<ProtocolChecklistSection> responseSections = new ArrayList<>(sections.size());
    for (SectionDefinition section : sections) {
      ChecklistSectionData sectionData = section.data();
      header = header.mergedWith(sectionData.header());
      responseSections.add(toSection(section.id(), section.title(), sectionData));
    }

    String statusCode = header.statusCode();
    return Optional.of(new ProtocolChecklistResponse(
        checklistId,
        oracleProtocol,
        protocolNames.getOrDefault(oracleProtocol, oracleProtocol),
        header.frepSelectedSiteId(),
        header.openingNumber(),
        header.effectiveYear(),
        statusCode,
        statusCode,
        header.evaluatorUserid(),
        header.evaluationDate(),
        responseSections
    ));
  }

  static Optional<String> normalizeProtocolType(String protocolType) {
    if (StringUtils.isBlank(protocolType)) {
      return Optional.empty();
    }
    return switch (protocolType.trim().toUpperCase()) {
      case "BIO", "SLB" -> Optional.of("SLB");
      case "RIP" -> Optional.of("RIP");
      case "WAT", "WTR" -> Optional.of("WTR");
      default -> Optional.empty();
    };
  }

  static ProtocolChecklistField toField(String label, String value) {
    return new ProtocolChecklistField(label, value, inferFieldKind(label, value));
  }

  static String inferFieldKind(String label, String value) {
    if (value == null || value.isBlank()) {
      return "TEXT";
    }
    String normalized = value.trim();
    if ("Y".equalsIgnoreCase(normalized) || "N".equalsIgnoreCase(normalized)) {
      return "YES_NO";
    }
    String lowerLabel = label == null ? "" : label.toLowerCase();
    if (lowerLabel.contains("date")) {
      return "DATE";
    }
    if (lowerLabel.contains("comment") || lowerLabel.contains("summary") || lowerLabel.contains("rationale")) {
      return "MULTILINE";
    }
    if (normalized.matches("-?\\d+(\\.\\d+)?")) {
      return "NUMBER";
    }
    return "TEXT";
  }

  static ProtocolChecklistSection toSection(String id, String title, ChecklistSectionData sectionData) {
    List<ProtocolChecklistField> fields = sectionData.fields().stream()
        .map(entry -> toField(entry.getKey(), entry.getValue()))
        .toList();
    return new ProtocolChecklistSection(id, title, fields);
  }

  static ChecklistHeaderData mergeHeaders(List<ChecklistSectionData> sections) {
    ChecklistHeaderData header = ChecklistHeaderData.empty();
    for (ChecklistSectionData section : sections) {
      header = header.mergedWith(section.header());
    }
    return header;
  }

  private Map<String, String> loadProtocolNames() {
    Map<String, String> names = new HashMap<>();
    for (var row : codeListRepository.getResourceValue()) {
      var protocol = ConfigurationService.toProtocolResponse(row);
      if (protocol.code() != null && !protocol.name().isBlank()) {
        names.put(protocol.code(), protocol.name());
      }
    }
    return names;
  }

  private List<SectionDefinition> bioSections(String checklistId) {
    return List.of(
        section("administration", "Administration (FREP301)", ChecklistSectionData::emptySection),
        section("opening", "Opening info (FREP210)", () -> checklistRepository.getBioOpening(checklistId)),
        section("stratum", "Stratum summary (FREP211)", () -> checklistRepository.getBioStratum(checklistId)),
        section("plots", "Plots (FREP212)", () -> checklistRepository.getBioPlots(checklistId)),
        section("notes", "Notes", ChecklistSectionData::emptySection),
        section("attachments", "Attachments", ChecklistSectionData::emptySection)
    );
  }

  private List<SectionDefinition> ripSections(String checklistId) {
    // Other Indicators (FREP232) is intentionally omitted — the legacy tab bar
    // (FrepTabs.getRiparianTabs) retired it. Administration (FREP301) leads, as in legacy.
    return List.of(
        section("administration", "Administration (FREP301)", ChecklistSectionData::emptySection),
        section("stream", "Stream / opening (FREP230)", () -> checklistRepository.getRipStreamOpening(checklistId)),
        section("field-data", "Field data (FREP231)", () -> checklistRepository.getRipFieldData(checklistId)),
        section("questions", "Questions (FREP233)", () -> checklistRepository.getRipQuestions(checklistId)),
        section("specific-impacts", "Specific impacts (FREP234)", () -> checklistRepository.getRipSpecificImpacts(checklistId)),
        section("final-cmts", "Final comments (FREP235)", () -> checklistRepository.getRipFinalComments(checklistId)),
        section("notes", "Notes", ChecklistSectionData::emptySection),
        section("attachments", "Attachments", ChecklistSectionData::emptySection)
    );
  }

  private List<SectionDefinition> wtrSections(String checklistId) {
    ChecklistSectionData sampleSite = safeRead(() -> checklistRepository.getWaterSampleSite(checklistId));
    String waterSampleSiteId = sampleSite.fields().stream()
        .filter(entry -> "Water sample site id".equals(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse("");

    return List.of(
        section("administration", "Administration (FREP301)", ChecklistSectionData::emptySection),
        section("sample-area", "Sample area (FREP250)", () -> checklistRepository.getWaterSampleArea(checklistId)),
        section("site-control", "Site control / details (FREP251)", () -> sampleSite),
        section("assessment", "Assessment (FREP252)", () -> checklistRepository.getWaterAssessment(waterSampleSiteId)),
        section("range", "Range (FREP253)", () -> checklistRepository.getWaterRange(waterSampleSiteId)),
        section("summary", "Summary (FREP254)", () -> checklistRepository.getWaterSummary(checklistId)),
        section("notes", "Notes", ChecklistSectionData::emptySection),
        section("attachments", "Attachments", ChecklistSectionData::emptySection)
    );
  }

  private static SectionDefinition section(
      String id, String title, Supplier<ChecklistSectionData> read) {
    return new SectionDefinition(id, title, safeRead(read));
  }

  /**
   * Reads a section, degrading to an empty section when the underlying proc raises
   * {@code ORA-01403 (NO_DATA_FOUND)} — e.g. a checklist with no rows for that section, or a
   * single-row GET proc that requires ids the read-only overview can't supply. Keeps the whole
   * checklist load from failing because one section has no data. Other errors propagate.
   */
  private static ChecklistSectionData safeRead(Supplier<ChecklistSectionData> read) {
    try {
      return read.get();
    } catch (DataAccessException ex) {
      if (isNoDataFound(ex)) {
        return ChecklistSectionData.fieldsOnly(ChecklistSectionData.linkedFields());
      }
      throw ex;
    }
  }

  private static boolean isNoDataFound(DataAccessException ex) {
    return ex.getMostSpecificCause() instanceof SQLException sqlEx && sqlEx.getErrorCode() == 1403;
  }

  private record SectionDefinition(String id, String title, ChecklistSectionData data) {}

  /** Thrown when submit fails server-side validation; carries the (DB-sourced) message codes. */
  public static class ProtocolSubmitValidationException extends RuntimeException {
    private final transient List<String> messages;

    public ProtocolSubmitValidationException(List<String> messages) {
      super("Protocol checklist submit validation failed");
      this.messages = messages;
    }

    public List<String> getMessages() {
      return messages;
    }
  }
}
