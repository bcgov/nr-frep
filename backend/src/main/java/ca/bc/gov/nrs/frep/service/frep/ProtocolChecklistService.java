package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistField;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolChecklistSection;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistHeaderData;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistRepository;
import ca.bc.gov.nrs.frep.repository.frep.ChecklistSectionData;
import ca.bc.gov.nrs.frep.repository.frep.CodeListRepository;
import ca.bc.gov.nrs.frep.repository.frep.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
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
        section("opening", "Opening info (FREP210)", checklistRepository.getBioOpening(checklistId)),
        section("stratum", "Stratum summary (FREP211)", checklistRepository.getBioStratum(checklistId)),
        section("plots", "Plots (FREP212)", checklistRepository.getBioPlots(checklistId))
    );
  }

  private List<SectionDefinition> ripSections(String checklistId) {
    return List.of(
        section("stream", "Stream / opening (FREP230)", checklistRepository.getRipStreamOpening(checklistId)),
        section("field-data", "Field data (FREP231)", checklistRepository.getRipFieldData(checklistId)),
        section("other-inds", "Other indicators (FREP232)", checklistRepository.getRipOtherIndicators(checklistId)),
        section("questions", "Questions (FREP233)", checklistRepository.getRipQuestions(checklistId)),
        section("specific-impacts", "Specific impacts (FREP234)", checklistRepository.getRipSpecificImpacts(checklistId)),
        section("final-cmts", "Final comments (FREP235)", checklistRepository.getRipFinalComments(checklistId))
    );
  }

  private List<SectionDefinition> wtrSections(String checklistId) {
    ChecklistSectionData sampleSite = checklistRepository.getWaterSampleSite(checklistId);
    String waterSampleSiteId = sampleSite.fields().stream()
        .filter(entry -> "Water sample site id".equals(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse("");

    return List.of(
        section("sample-area", "Sample area (FREP250)", checklistRepository.getWaterSampleArea(checklistId)),
        section("site-control", "Site control / details (FREP251)", sampleSite),
        section("assessment", "Assessment (FREP252)", checklistRepository.getWaterAssessment(waterSampleSiteId)),
        section("range", "Range (FREP253)", checklistRepository.getWaterRange(waterSampleSiteId)),
        section("summary", "Summary (FREP254)", checklistRepository.getWaterSummary(checklistId))
    );
  }

  private static SectionDefinition section(String id, String title, ChecklistSectionData data) {
    return new SectionDefinition(id, title, data);
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
