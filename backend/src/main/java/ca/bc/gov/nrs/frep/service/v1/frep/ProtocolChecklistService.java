package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.struct.v1.frep.BioCheckout;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioCwdRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioSnapshot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioSnapshotUpload;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStandRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.struct.v1.frep.StratumComputed;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolChecklistField;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.struct.v1.frep.ProtocolChecklistSection;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistHeaderData;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.repository.v1.ChecklistRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSectionData;
import ca.bc.gov.nrs.frep.repository.v1.CodeListRepository;
import ca.bc.gov.nrs.frep.repository.v1.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.security.LoggedUserHelper;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
import ca.bc.gov.nrs.frep.service.v1.VirusScanner;
import ca.bc.gov.nrs.frep.exception.AccessForbiddenException;
import ca.bc.gov.nrs.frep.exception.ConflictFoundException;
import ca.bc.gov.nrs.frep.exception.InvalidParameterException;
import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import ca.bc.gov.nrs.frep.ChrConstants;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Protocol checklist lookup backed by legacy Oracle GET procedures via
 * {@link ChecklistRepository}.
 */
@Service
public class ProtocolChecklistService {

  // Allowed attachment types = the codes in THE.MIME_TYPE_CODE (keyed by file extension). The
  // FREP_CHECKLIST_ATTACHMENTS proc stores only these and rejects anything else with an ORA-01400
  // (NULL mime_type_code). Guard here so an unsupported type is a clean 400 instead of a 500.
  private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
      "BMP", "CSV", "DOC", "GIF", "HTM", "IFM", "JPG", "JPK", "MDB", "MDE", "OBD", "PDF", "PNG",
      "PPS", "PPT", "RPT", "RTF", "TIF", "TXT", "WAV", "XLD", "XLS", "XML", "ZIP");
  private static final String ALLOWED_ATTACHMENT_TYPES_DISPLAY =
      "BMP, CSV, DOC, GIF, HTM, IFM, JPG, JPK, MDB, MDE, OBD, PDF, PNG, PPS, PPT, RPT, RTF, TIF, TXT, "
          + "WAV, XLD, XLS, XML, ZIP";

  private final ChecklistRepository checklistRepository;
  private final CodeListRepository codeListRepository;
  private final ProtocolChecklistWriteRepository writeRepository;
  private final LoggedUserHelper loggedUserHelper;
  private final FamUserDirectoryService famUserDirectoryService;
  private final VirusScanner virusScanner;
  private final ObjectStorageService objectStorage;

  public ProtocolChecklistService(
      ChecklistRepository checklistRepository,
      CodeListRepository codeListRepository,
      ProtocolChecklistWriteRepository writeRepository,
      LoggedUserHelper loggedUserHelper,
      FamUserDirectoryService famUserDirectoryService,
      VirusScanner virusScanner,
      ObjectStorageService objectStorage
  ) {
    this.checklistRepository = checklistRepository;
    this.codeListRepository = codeListRepository;
    this.writeRepository = writeRepository;
    this.loggedUserHelper = loggedUserHelper;
    this.famUserDirectoryService = famUserDirectoryService;
    this.virusScanner = virusScanner;
    this.objectStorage = objectStorage;
  }

  /** Submit a protocol checklist (server-side DB validation + status to SUB). */
  public void submit(String protocolType, String checklistId) {
    String resourceType = checklistRepository.resolveResourceType(checklistId);
    assertEditable(resourceType);
    String error = writeRepository.submit(resourceType, checklistId, loggedUserHelper.getLoggedUserId());
    if (StringUtils.isNotBlank(error)) {
      throw new ProtocolSubmitValidationException(splitValidationMessages(error));
    }
  }

  /** Revert a submitted checklist to ACT. */
  public void unsubmit(String protocolType, String checklistId) {
    String resourceType = checklistRepository.resolveResourceType(checklistId);
    assertEditable(resourceType);
    String error = writeRepository.unsubmit(resourceType, checklistId, loggedUserHelper.getLoggedUserId());
    if (StringUtils.isNotBlank(error)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
  }

  /** Typed read of the Biodiversity Opening screen for editing (with the evaluator name resolved). */
  public BiodiversityOpening getBiodiversityOpening(String checklistId) {
    BiodiversityOpening opening = writeRepository.getBiodiversityOpening(checklistId);
    if (opening == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "Stand Level Retention checklist not found: " + checklistId);
    }
    return withResolvedBioLead(opening);
  }

  /** Save the Biodiversity Opening via FREP_210_BIO_OPENING.SAVE, then apply any evaluator claim. */
  public BiodiversityOpening saveBiodiversityOpening(String checklistId, BiodiversityOpening opening) {
    assertChecklistEditable(checklistId);
    validateBiodiversityOpening(opening);
    String userId = loggedUserHelper.getLoggedUserId();
    BiodiversityOpening toSave = opening.checklistId() == null
        ? opening.withIdentity(checklistId, opening.revisionCount())
        : opening;
    writeRepository.saveBiodiversityOpening(toSave, userId);
    // "Assign it to me": the payload names the caller as the evaluator. Act only when the caller is
    // claiming it for themselves (never a third party) — a takeover replaces the previous lead. The
    // evaluator record is a separate table/revision, so it doesn't disturb the opening save above.
    applyEvaluatorAssignment(checklistId, opening.teamLeadNameId(), userId);
    return getBiodiversityOpening(checklistId);
  }

  private void applyEvaluatorAssignment(String checklistId, String requestedLead, String userId) {
    if (!userId.equals(requestedLead)) {
      return;
    }
    BiodiversityOpening current = writeRepository.getBiodiversityOpening(checklistId);
    if (current != null && userId.equalsIgnoreCase(current.teamLeadNameId())) {
      return; // already the lead — nothing to do
    }
    writeRepository.assignBiodiversityLead(checklistId,
        checklistRepository.resolveResourceType(checklistId),
        userId, current == null ? null : current.teamLeadNameId(),
        current == null ? null : current.teamLeadRevisionCount(), userId);
  }

  /** Resolve the evaluator (team lead) userid to a display name via FAM, mirroring the header. */
  private BiodiversityOpening withResolvedBioLead(BiodiversityOpening opening) {
    if (opening == null || StringUtils.isBlank(opening.teamLeadNameId())) {
      return opening;
    }
    String name = famUserDirectoryService.resolveName(opening.teamLeadNameId())
        .orElse(opening.teamLeadNameId());
    return opening.withTeamLead(opening.teamLeadNameId(), name, opening.teamLeadRevisionCount());
  }

  /**
   * Validate the Biodiversity Opening, mirroring the legacy FREP210 {@code Frep210ValidationManager}
   * "Save" chain: Location description, Invasive plant?, Innovative practice? and Rating are required;
   * the two practice/invasive comments are required when their answer is Yes; length limits on the
   * description (50) and the comments (4000) / rationale (2000); and the FREP gross-area override is a
   * float within 0.01–99999.99 to two decimals. Throws {@link InvalidPayloadException} (HTTP 400).
   */
  private static void validateBiodiversityOpening(BiodiversityOpening opening) {
    List<String> errors = new ArrayList<>();

    if (StringUtils.isBlank(opening.locationDescription())) {
      errors.add("Location description is required.");
    } else if (length(opening.locationDescription()) > 50) {
      errors.add(tooLong("Location description", opening.locationDescription(), 50));
    }
    if (StringUtils.isBlank(opening.invasivePlantIndicator())) {
      errors.add("Select whether invasive plant species are present.");
    }
    if (StringUtils.isBlank(opening.innovativePracticeInd())) {
      errors.add("Select whether innovative practices were used.");
    }
    if (StringUtils.isBlank(opening.frepSiteEvaluationCode())) {
      errors.add("A rating is required.");
    }

    if ("Y".equals(opening.innovativePracticeInd())
        && StringUtils.isBlank(opening.innovativePracticesComment())) {
      errors.add("Describe the innovative practice.");
    } else if (length(opening.innovativePracticesComment()) > 4000) {
      errors.add(tooLong("Description", opening.innovativePracticesComment(), 4000));
    }
    if ("Y".equals(opening.invasivePlantIndicator())
        && StringUtils.isBlank(opening.invasivePlantComment())) {
      errors.add("Enter a comment about the invasive plants.");
    } else if (length(opening.invasivePlantComment()) > 4000) {
      errors.add(tooLong("Comments", opening.invasivePlantComment(), 4000));
    }
    if (length(opening.evaluatorOpinionComment()) > 2000) {
      errors.add(tooLong("Rationale", opening.evaluatorOpinionComment(), 2000));
    }
    validateOverride(opening.frepWtpOverride(), errors);

    if (!errors.isEmpty()) {
      ApiError error = ApiError.builder()
          .timestamp(LocalDateTime.now())
          .message(String.join(" ", errors))
          .status(HttpStatus.BAD_REQUEST)
          .build();
      throw new InvalidPayloadException(error);
    }
  }

  /**
   * Length in the unit the database enforces: UTF-8 <b>bytes</b>.
   *
   * <p>Every free-text column behind these screens is declared byte-semantic —
   * {@code BIODIVERSITY_CHECKLIST.LOCATION_DESCRIPTION VARCHAR2(50 BYTE)} and the rest (see
   * nr-mof-db {@code scripts/THE/TABLES/}). A character count therefore accepts values the insert
   * rejects: 26 curly quotes are 26 characters but 78 bytes, so a "50 character" location
   * description can overflow a 50-byte column. The frontend counters measure the same way, so the
   * count an evaluator sees is the count that decides whether the save succeeds.
   */
  private static int length(String value) {
    return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
  }

  /** The over-limit message, phrased without "characters" — the limit is bytes. */
  private static String tooLong(String label, String value, int max) {
    return label + " is too long — the limit is " + max + " and this entry uses "
        + length(value) + ".";
  }

  private static void validateOverride(String value, List<String> errors) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    String text = value.trim();
    if (!text.matches("[-+]?\\d*\\.?\\d+")) {
      errors.add("FREP gross area override must be a number.");
      return;
    }
    double number = Double.parseDouble(text);
    if (number < 0.01 || number > 99999.99) {
      errors.add("FREP gross area override must be between 0.01 and 99999.99.");
      return;
    }
    int dot = text.indexOf('.');
    if (dot >= 0 && text.length() - dot - 1 > 2) {
      errors.add("FREP gross area override can have at most 2 decimal places.");
    }
  }

  // --- Stratum summary validation (legacy FREP211 Frep211ValidationManager) ---

  /**
   * Validate a Biodiversity Stratum, mirroring the legacy FREP211 {@code Frep211ValidationManager}
   * "Save" chain: required core fields (incl. BGC subzone, PT #43888); the stratum-id mask; the
   * 0-plots-needs-patch rule; conditional size / estimated size / patch fields; numeric ranges and
   * decimal limits; free-text length limits; the constraint-total cross-field rules; the
   * stratum-type ↔ harvest-area ↔ patch-location coupling; windthrow-treatment exclusivity; and the
   * other-constraint / other-eco pairings. Throws {@link InvalidPayloadException} (HTTP 400).
   */
  private static void validateBioStratum(BioStratum s) {
    List<String> errors = new ArrayList<>();
    String type = trimmedOrEmpty(s.strataTypeCode());
    String harvest = trimmedOrEmpty(s.harvestAreaCode());
    String consistent = trimmedOrEmpty(s.consistentMapInd());

    requireField(s.stratumNumber(), "Stratum number", errors);
    requireField(s.strataTypeCode(), "Stratum type", errors);
    requireField(s.consistentMapInd(), "Consistent with map", errors);
    requireField(s.plotCount(), "Plot count", errors);
    requireField(s.harvestAreaCode(), "Harvest area", errors);
    requireField(s.bgcZoneCode(), "BGC zone", errors);
    requireField(s.bgcSubzoneCode(), "BGC subzone", errors);

    if (StringUtils.isNotBlank(s.stratumNumber()) && !stratumNumberValid(s.stratumNumber().trim())) {
      errors.add("Stratum Id: use 1-3 letters then 0-2 digits, e.g. AB12.");
    }
    if ("0".equals(trimmedOrEmpty(s.plotCount())) && !type.isEmpty() && !type.startsWith("P")) {
      errors.add("A stratum with 0 plots must be a patch stratum type.");
    }
    if ("Y".equals(consistent) && StringUtils.isBlank(s.size())) {
      errors.add("Stratum size is required when consistent with map is \"Yes\".");
    }
    if (("N".equals(consistent) || "M".equals(consistent)) && StringUtils.isBlank(s.estimatedSize())) {
      errors.add("Estimated size is required when not consistent with map.");
    }
    if ("M".equals(consistent) && isNumeric(s.size()) && Double.parseDouble(s.size().trim()) != 0) {
      errors.add("Stratum size must be blank when \"Not mapped\".");
    }
    if ("PCH".equals(harvest) && StringUtils.isBlank(s.patchWindthrowPct())) {
      errors.add("% of trees windthrown is required for a patch reserve.");
    }
    if (type.startsWith("P") && StringUtils.isBlank(s.patchLocationCode())) {
      errors.add("Patch location is required for a patch stratum type.");
    }

    intRange(s.plotCount(), "Plot count", 0, 99, errors);
    numRange(s.size(), "Mapped stratum size", 0.01, 9999.99, errors);
    numRange(s.estimatedSize(), "Estimated size", 0.01, 9999.99, errors);
    intRange(s.patchEstimatedOldestTreeAge(), "Estimated oldest tree age", 0, 999, errors);
    numRange(s.patchWindthrowPct(), "% of trees windthrown", 0, 100, errors);
    intRange(s.wetlandPct(), "Wetland %", 1, 100, errors);
    intRange(s.riparianManagementZonePct(), "Riparian mgmt zone %", 1, 100, errors);
    intRange(s.riparianReserveZonePct(), "Riparian reserve zone %", 1, 100, errors);
    intRange(s.rockOutcropPct(), "Rock outcrop %", 1, 100, errors);
    intRange(s.nonCommercialBrushPct(), "Non-commercial brush %", 1, 100, errors);
    intRange(s.nonMerchTimberPct(), "Non-merch timber %", 1, 100, errors);
    intRange(s.sensitiveSoilPct(), "Sensitive soil %", 1, 100, errors);
    intRange(s.ungHoofAnimalWinteringPct(), "Ungulate wintering %", 1, 100, errors);
    intRange(s.wildlifeHabitatAreaPct(), "Wildlife habitat area %", 1, 100, errors);
    intRange(s.oldGrowthManagementAreaPct(), "OGMA %", 1, 100, errors);
    intRange(s.visualsPct(), "Visuals %", 1, 100, errors);
    intRange(s.culturalHeritageFeaturePct(), "Cultural heritage feature %", 1, 100, errors);
    intRange(s.recreationFeaturePct(), "Recreation feature %", 1, 100, errors);
    intRange(s.otherConstraintPct(), "Other constraint %", 1, 100, errors);
    intRange(s.bearDenCnt(), "Bear den count", 1, 999, errors);
    intRange(s.hibernaculumCnt(), "Hibernaculum count", 1, 999, errors);
    intRange(s.vetTreeCnt(), "Veteran tree count", 1, 999, errors);
    intRange(s.mineralLickCnt(), "Mineral lick count", 1, 999, errors);
    intRange(s.largeStickNestCnt(), "Large stick nest count", 1, 999, errors);
    intRange(s.cavityNestCnt(), "Cavity nest count", 1, 999, errors);
    intRange(s.largeHallowTreeCnt(), "Large hollow tree count", 1, 999, errors);
    intRange(s.largeWitchesBroomCnt(), "Large witches' broom count", 1, 999, errors);
    intRange(s.otherEcoAnchorCnt(), "Other eco anchor count", 1, 999, errors);

    decimalLimit(s.size(), "Mapped stratum size", 2, errors);
    decimalLimit(s.estimatedSize(), "Estimated size", 2, errors);
    decimalLimit(s.patchWindthrowPct(), "% of trees windthrown", 1, errors);

    maxLength(s.otherConstraint(), "Other constraint", 50, errors);
    maxLength(s.otherEcoAnchorDesc(), "Other eco anchor description", 30, errors);
    maxLength(s.patchGeneralComment(), "Patch general comment", 2000, errors);

    if (isIntInRange(trimmedOrEmpty(s.constrainedTotal()), 0, 100)) {
      int total = Integer.parseInt(s.constrainedTotal().trim());
      int maxSingle = maxConstraintPct(s);
      if (total > 0 && total < maxSingle) {
        errors.add("Total constrained must be at least the largest single constraint %.");
      } else if (total > 0 && maxSingle == 0) {
        errors.add("Enter at least one constraint when total constrained is greater than 0.");
      }
    }

    if (!type.isEmpty()) {
      boolean isPatch = type.startsWith("P");
      if (isPatch && !harvest.isEmpty() && !"PCH".equals(harvest)) {
        errors.add("A patch stratum type requires harvest area \"Patch reserve\".");
      } else if (!isPatch && "PCH".equals(harvest)) {
        errors.add("Harvest area \"Patch reserve\" is only valid for a patch stratum type.");
      }
    }
    String patchLoc = trimmedOrEmpty(s.patchLocationCode());
    if ("PCH".equals(harvest) && "NA".equals(patchLoc)) {
      errors.add("Patch location cannot be N/A for a patch reserve.");
    } else if ("HDR".equals(harvest) && !patchLoc.isEmpty() && !"NA".equals(patchLoc)) {
      errors.add("Patch location must be N/A for dispersed retention.");
    }
    if (treatmentChecked(s, "N")
        && (treatmentChecked(s, "F") || treatmentChecked(s, "T")
            || StringUtils.isNotBlank(s.otherWindthrowTreatment()))) {
      errors.add("Windthrow treatment \"None\" cannot be combined with other treatments.");
    }
    if (StringUtils.isNotBlank(s.otherConstraint()) != StringUtils.isNotBlank(s.otherConstraintPct())) {
      errors.add("Other constraint needs both a name and a %.");
    }
    if (StringUtils.isNotBlank(s.otherEcoAnchorDesc()) != StringUtils.isNotBlank(s.otherEcoAnchorCnt())) {
      errors.add("Other eco anchor needs both a description and a count.");
    }

    if (!errors.isEmpty()) {
      ApiError error = ApiError.builder()
          .timestamp(LocalDateTime.now())
          .message(String.join(" ", errors))
          .status(HttpStatus.BAD_REQUEST)
          .build();
      throw new InvalidPayloadException(error);
    }
  }

  private static String trimmedOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static boolean isNumeric(String value) {
    return StringUtils.isNotBlank(value) && value.trim().matches("[-+]?\\d*\\.?\\d+");
  }

  private static boolean isIntInRange(String value, int min, int max) {
    if (!value.matches("-?\\d+")) {
      return false;
    }
    try {
      int n = Integer.parseInt(value);
      return n >= min && n <= max;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private static boolean isNumInRange(String value, double min, double max) {
    if (!value.matches("[-+]?\\d*\\.?\\d+")) {
      return false;
    }
    double n = Double.parseDouble(value);
    return n >= min && n <= max;
  }

  private static void requireField(String value, String label, List<String> errors) {
    if (StringUtils.isBlank(value)) {
      errors.add(label + " is required.");
    }
  }

  private static void intRange(String value, String label, int min, int max, List<String> errors) {
    if (StringUtils.isNotBlank(value) && !isIntInRange(value.trim(), min, max)) {
      errors.add(label + " must be a whole number from " + min + " to " + max + ".");
    }
  }

  private static void numRange(String value, String label, double min, double max,
      List<String> errors) {
    if (StringUtils.isNotBlank(value) && !isNumInRange(value.trim(), min, max)) {
      errors.add(label + " must be a number from " + fmt(min) + " to " + fmt(max) + ".");
    }
  }

  private static void decimalLimit(String value, String label, int max, List<String> errors) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    String text = value.trim();
    int dot = text.indexOf('.');
    int places = dot < 0 ? 0 : text.length() - dot - 1;
    if (places > max) {
      errors.add(label + " can have at most " + max + (max == 1 ? " decimal place." : " decimal places."));
    }
  }

  private static void maxLength(String value, String label, int max, List<String> errors) {
    if (value != null && length(value) > max) {
      errors.add(tooLong(label, value, max));
    }
  }

  private static int maxConstraintPct(BioStratum s) {
    int max = 0;
    for (String value : List.of(
        trimmedOrEmpty(s.wetlandPct()), trimmedOrEmpty(s.riparianManagementZonePct()),
        trimmedOrEmpty(s.riparianReserveZonePct()), trimmedOrEmpty(s.rockOutcropPct()),
        trimmedOrEmpty(s.nonCommercialBrushPct()), trimmedOrEmpty(s.nonMerchTimberPct()),
        trimmedOrEmpty(s.sensitiveSoilPct()), trimmedOrEmpty(s.ungHoofAnimalWinteringPct()),
        trimmedOrEmpty(s.wildlifeHabitatAreaPct()), trimmedOrEmpty(s.oldGrowthManagementAreaPct()),
        trimmedOrEmpty(s.visualsPct()), trimmedOrEmpty(s.culturalHeritageFeaturePct()),
        trimmedOrEmpty(s.recreationFeaturePct()), trimmedOrEmpty(s.otherConstraintPct()))) {
      if (value.matches("\\d+")) {
        max = Math.max(max, Integer.parseInt(value));
      }
    }
    return max;
  }

  private static boolean treatmentChecked(BioStratum s, String code) {
    return s.windthrowTreatments() != null && s.windthrowTreatments().stream()
        .anyMatch(t -> code.equals(t.code()) && !"N".equals(t.checkInd()));
  }

  private static String fmt(double value) {
    return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
  }

  private static boolean stratumNumberValid(String value) {
    if (value.isEmpty()) {
      return true;
    }
    if (value.length() > 5 || value.contains(" ") || Character.isDigit(value.charAt(0))) {
      return false;
    }
    boolean seenDigit = false;
    int digits = 0;
    int letters = 0;
    for (char c : value.toCharArray()) {
      if (Character.isDigit(c)) {
        seenDigit = true;
        digits++;
      } else {
        if (seenDigit) {
          return false;
        }
        letters++;
      }
    }
    return digits <= 2 && letters <= 3;
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
    assertChecklistEditable(stratum.checklistId());
    validateBioStratum(stratum);
    return writeRepository.saveBioStratum(stratum, loggedUserHelper.getLoggedUserId());
  }

  public void deleteBioStratum(String stratumId, String revisionCount) {
    assertChecklistEditable(writeRepository.checklistIdForStratum(stratumId));
    String error = writeRepository.deleteBioStratum(stratumId, revisionCount);
    if (StringUtils.isNotBlank(error)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
  }

  /** Read-only NAR + plots-completed for the FREP211 Stratum Summary header. */
  public StratumComputed getStratumComputed(String stratumId) {
    return writeRepository.getStratumComputed(stratumId);
  }

  /** NAR + plots-completed (0) for a not-yet-saved stratum on the Add form. */
  public StratumComputed getNewStratumComputed(String checklistId) {
    return writeRepository.getNewStratumComputed(checklistId);
  }

  // --- Biodiversity plots (FREP screen 212) ---

  public List<BioPlotRow> listBioPlots(String stratumId) {
    return writeRepository.listBioPlots(stratumId).stream()
        .map(row -> row.withAssessorDisplayName(assessorDisplayName(row.assessorName())))
        .toList();
  }

  public BioPlot getBioPlot(String plotId) {
    BioPlot plot = writeRepository.getBioPlot(plotId);
    if (plot == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plot not found: " + plotId);
    }
    return plot.withAssessorDisplayName(assessorDisplayName(plot.assessorName()));
  }

  /**
   * Display name for a plot assessor userid — the FAM-resolved "First Last (USERID)" the checklist
   * header shows, falling back to the bare userid when the assessor no longer has FREP access (or
   * FAM is unavailable). Plot assessors are stored bare, so this is the only place a name exists.
   *
   * <p>Cheap for a plot list: {@link FamUserDirectoryService#resolveName} caches by userid, and a
   * stratum's plots are usually all assessed by the same person, so the list costs one lookup.
   */
  private String assessorDisplayName(String userid) {
    if (StringUtils.isBlank(userid)) {
      return userid;
    }
    return famUserDirectoryService.resolveName(userid).orElse(userid);
  }

  public BioPlot saveBioPlot(BioPlot plot) {
    assertChecklistEditable(writeRepository.checklistIdForStratum(plot.stratumId()));
    validateBioPlot(plot);
    return writeRepository.saveBioPlot(plot, loggedUserHelper.getLoggedUserId());
  }

  // --- Plots validation (legacy FREP212 Frep212ValidationManager) ---

  /**
   * Validate a Biodiversity Plot, mirroring the legacy FREP212 save chains: UTM (conditional on the
   * "no signal" toggle, easting 6 / northing 7 digits), bearings (required, 0–359), Evaluated by
   * required, Plot # / BAF / fixed-area / full-count numeric ranges and decimals, comment length,
   * the "exactly one measurement method" rule, and per-row stand-table (species/WT class/DBH/height)
   * and CWD (species/decay/diameter/length) rules. Throws {@link InvalidPayloadException} (HTTP 400).
   *
   * <p>The clear-cut measurement nuance (must be fixed-area radius specifically) needs the stratum
   * type, which the plot payload doesn't carry — it's enforced client-side and by the proc; here the
   * rule is the protocol-agnostic "exactly one".
   */
  private static void validateBioPlot(BioPlot p) {
    List<String> errors = new ArrayList<>();

    if (!"N".equals(trimmedOrEmpty(p.utmSignal()))) {
      requireField(p.utmZone(), "Zone", errors);
      if (StringUtils.isBlank(p.utmEasting())) {
        errors.add("Easting is required.");
      } else if (!p.utmEasting().trim().matches("\\d{6}")) {
        errors.add("Easting must be exactly 6 digits.");
      }
      if (StringUtils.isBlank(p.utmNorthing())) {
        errors.add("Northing is required.");
      } else if (!p.utmNorthing().trim().matches("\\d{7}")) {
        errors.add("Northing must be exactly 7 digits.");
      }
    }

    requireBearing(p.firstLegTransect(), "Bearing 1st leg", errors);
    requireBearing(p.secondLegTransect(), "2nd leg", errors);
    requireField(p.assessorName(), "Evaluated by", errors);

    intRange(p.plotNumber(), "Plot #", 0, 999, errors);
    maxLength(p.plotComment(), "Comments", 2000, errors);
    intRange(p.basalAreaFactor(), "BAF", 1, 99, errors);
    numRange(p.fixedAreaRadius(), "Fixed area radius", 0.01, 999.99, errors);
    decimalLimit(p.fixedAreaRadius(), "Fixed area radius", 2, errors);
    numRange(p.fullCountArea(), "Full count area", 0.01, 9999.99, errors);
    decimalLimit(p.fullCountArea(), "Full count area", 2, errors);

    long methods = Stream.of(p.basalAreaFactor(), p.fixedAreaRadius(), p.fullCountArea())
        .filter(StringUtils::isNotBlank).count();
    if (methods != 1) {
      errors.add("Enter exactly one of BAF, fixed area radius, or full count area.");
    }

    if ("Y".equals(trimmedOrEmpty(p.treeIndicator()))) {
      List<BioStandRow> stand = p.standTable() == null ? List.of() : p.standTable();
      if (stand.isEmpty()) {
        errors.add("\"Trees exist\" is checked — add at least one stand-table row, or uncheck it.");
      }
      for (int i = 0; i < stand.size(); i++) {
        BioStandRow r = stand.get(i);
        String prefix = "Stand row " + (i + 1) + ": ";
        requireField(r.speciesCode(), prefix + "Species", errors);
        requireField(r.decayClassCode(), prefix + "WT class", errors);
        requireFloat(r.dbh(), prefix + "DBH", 12.5, 400, 1, true, errors);
        requireFloat(r.height(), prefix + "Height", 1.4, 99.9, 1, false, errors);
      }
    }
    if ("Y".equals(trimmedOrEmpty(p.cwdTransectIndicator()))) {
      List<BioCwdRow> cwd = p.cwdTable() == null ? List.of() : p.cwdTable();
      for (int i = 0; i < cwd.size(); i++) {
        BioCwdRow r = cwd.get(i);
        String prefix = "CWD row " + (i + 1) + ": ";
        requireField(r.speciesCode(), prefix + "Species", errors);
        requireField(r.decayClassCode(), prefix + "Decay class", errors);
        requireFloat(r.logDiameter(), prefix + "Diameter", 7.6, 400, 1, false, errors);
        requireFloat(r.logLength(), prefix + "Length", 0, 99.9, 1, true, errors);
      }
    }

    if (!errors.isEmpty()) {
      ApiError error = ApiError.builder()
          .timestamp(LocalDateTime.now())
          .message(String.join(" ", errors))
          .status(HttpStatus.BAD_REQUEST)
          .build();
      throw new InvalidPayloadException(error);
    }
  }

  private static void requireBearing(String value, String label, List<String> errors) {
    if (StringUtils.isBlank(value)) {
      errors.add(label + " is required.");
    } else {
      intRange(value, label, 0, 359, errors);
    }
  }

  private static void requireFloat(String value, String label, double min, double max,
      int maxDecimals, boolean exclusiveMin, List<String> errors) {
    if (StringUtils.isBlank(value)) {
      errors.add(label + " is required.");
      return;
    }
    String text = value.trim();
    if (!text.matches("\\d*\\.?\\d+")) {
      errors.add(label + " must be a number.");
      return;
    }
    int dot = text.indexOf('.');
    if (dot >= 0 && text.length() - dot - 1 > maxDecimals) {
      errors.add(label + " can have at most " + maxDecimals
          + (maxDecimals == 1 ? " decimal place." : " decimal places."));
      return;
    }
    double n = Double.parseDouble(text);
    if ((exclusiveMin ? n <= min : n < min) || n > max) {
      errors.add(exclusiveMin
          ? label + " must be greater than " + fmt(min) + " and no more than " + fmt(max) + "."
          : label + " must be between " + fmt(min) + " and " + fmt(max) + ".");
    }
  }

  public void deleteBioPlot(String plotId, String revisionCount) {
    assertChecklistEditable(writeRepository.checklistIdForPlot(plotId));
    String error = writeRepository.deleteBioPlot(plotId, revisionCount);
    if (StringUtils.isNotBlank(error)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
  }

  // --- Administration / Notes / Attachments (biodiversity) ---
  //
  // The resource value type the shared procs key on is resolved from the record
  // (checklistRepository.resolveResourceType) — SLB (legacy) / SLR (going forward) — not the URL. The
  // {protocol} path segment only selects the family; it no longer determines the code.

  public RiparianNotes getNotes(String protocol, String checklistId) {
    return writeRepository.getNotes(checklistId, checklistRepository.resolveResourceType(checklistId));
  }

  public RiparianNotes saveNotes(String protocol, String checklistId, RiparianNotes notes) {
    assertChecklistEditable(checklistId);
    return writeRepository.saveNotes(notes, checklistRepository.resolveResourceType(checklistId),
        loggedUserHelper.getLoggedUserId());
  }

  /**
   * One page of attachment metadata, with each row's real size read from object storage.
   *
   * <p>The size cannot come from the database: {@code file_size} there is derived from the Oracle
   * BLOB, which Biodiversity deliberately leaves empty, so it always reads 0.00. A HEAD per row on
   * the page is exact and bounded; a prefix listing is not an option because the keys are flat
   * ({@code slr/<id>}) and would sweep every checklist's attachments.
   */
  public AttachmentPage getAttachments(String protocol, String checklistId, int page, int size) {
    String resourceType = checklistRepository.resolveResourceType(checklistId);
    List<AttachmentRow> rows = writeRepository.getAttachments(checklistId, resourceType, page, size);
    List<AttachmentRow> withSizes = rows.stream()
        .map(row -> {
          long bytes = objectStorage.getObjectSize(bioObjectKey(row.checklistAttachmentId()));
          return new AttachmentRow(row.checklistAttachmentId(), row.fileName(), row.description(),
              row.mimeTypeCode(), bytes < 0 ? null : String.valueOf(bytes));
        })
        .toList();
    return new AttachmentPage(withSizes, writeRepository.countAttachments(checklistId, resourceType));
  }

  /** Object key for a Biodiversity attachment; mirrors the write path. */
  private static String bioObjectKey(String attachmentId) {
    return "slr/" + attachmentId;
  }

  /** A page of attachment metadata plus the total, for the pager. */
  public record AttachmentPage(List<AttachmentRow> attachments, int totalCount) {}

  public AttachmentContent getAttachmentContent(
      String protocol, String checklistId, String attachmentId) {
    return writeRepository.getAttachmentContent(
        checklistId, checklistRepository.resolveResourceType(checklistId), attachmentId);
  }

  /**
   * Store one uploaded attachment. Multipart spools the body to a temp file, so the only point the
   * whole file is in heap is the {@code byte[]} below — read <b>once</b> and reused for the scan and
   * the write, since {@link MultipartFile#getBytes()} allocates a fresh array on every call.
   */
  public void saveAttachment(
      String protocol, String checklistId, MultipartFile file, String description,
      String deviceCheckoutGuid) {
    // Previously unguarded: this path checked neither the SLB exclusion nor status, so an attachment
    // could be added to a historical or submitted checklist. ACT, or RDO with the caller's own token
    // — the offline flush pushes attachments through here while the checklist is still checked out,
    // since the RDO → ACT flip happens at the end of the sync.
    assertChecklistEditable(checklistId, deviceCheckoutGuid);
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "The selected file is empty. Choose a file with content and try again.");
    }
    String fileName = file.getOriginalFilename();
    validateAttachmentType(fileName);
    byte[] bytes = readBytes(file, fileName);
    // Scan the raw bytes before any persistence — a hit throws VirusDetectedException (→ 422).
    virusScanner.scanOrThrow(bytes, fileName);
    // Resource type comes from the record, not the {protocol} path segment (SLB legacy / SLR
    // go-forward) — see the section comment above.
    writeRepository.saveAttachment(checklistId, checklistRepository.resolveResourceType(checklistId),
        fileName, description, file.getContentType(), bytes, loggedUserHelper.getLoggedUserId());
  }

  /** Pull the spooled upload into heap, turning the I/O failure into a clean 400 rather than a 500. */
  private static byte[] readBytes(MultipartFile file, String fileName) {
    try {
      return file.getBytes();
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Could not read the uploaded file" + (fileName == null ? "" : " " + fileName) + ".", ex);
    }
  }

  /** Reject file types the attachment proc can't store (see {@link #ALLOWED_ATTACHMENT_TYPES}). */
  private static void validateAttachmentType(String fileName) {
    int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
    String ext = (dot < 0 || dot == fileName.length() - 1)
        ? "" : fileName.substring(dot + 1).toUpperCase();
    if (!ALLOWED_ATTACHMENT_TYPES.contains(ext)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Unsupported file type" + (ext.isEmpty() ? "" : " ." + ext.toLowerCase())
              + ". Allowed types: " + ALLOWED_ATTACHMENT_TYPES_DISPLAY + ".");
    }
  }

  public void deleteAttachment(String protocol, String checklistId, String attachmentId,
      String deviceCheckoutGuid) {
    // Was SLB-only; now the full three-way check, so a submitted checklist's attachments are safe and
    // a checked-out one can only be changed by the device holding it.
    assertChecklistEditable(checklistId, deviceCheckoutGuid);
    writeRepository.deleteAttachment(
        checklistId, checklistRepository.resolveResourceType(checklistId), attachmentId);
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
    // The {protocol} path segment only selects the family — this service handles biodiversity only
    // (CHR is a separate service/route). The record's actual code (SLB legacy / SLR going forward) is
    // resolved from the DB, not the URL.
    if (!isBiodiversity(protocolType)) {
      return Optional.empty();
    }

    List<SectionDefinition> sections = bioSections(checklistId);
    if (sections.isEmpty()) {
      return Optional.empty();
    }

    String recordType = checklistRepository.resolveResourceType(checklistId);
    Map<String, String> protocolNames = loadProtocolNames();

    ChecklistHeaderData header = ChecklistHeaderData.empty();
    List<ProtocolChecklistSection> responseSections = new ArrayList<>(sections.size());
    for (SectionDefinition section : sections) {
      ChecklistSectionData sectionData = section.data();
      header = header.mergedWith(sectionData.header());
      responseSections.add(toSection(section.id(), section.title(), sectionData));
    }

    String statusCode = header.statusCode();
    String evaluatorUserid = header.evaluatorUserid();
    String evaluatorName = StringUtils.isBlank(evaluatorUserid)
        ? evaluatorUserid
        : famUserDirectoryService.resolveName(evaluatorUserid).orElse(evaluatorUserid);
    return Optional.of(new ProtocolChecklistResponse(
        checklistId,
        recordType,
        protocolNames.getOrDefault(recordType, recordType),
        header.frepSelectedSiteId(),
        header.openingNumber(),
        header.effectiveYear(),
        statusCode,
        statusCode,
        evaluatorUserid,
        evaluatorName,
        header.evaluationDate(),
        responseSections
    ));
  }

  /** The biodiversity family segment ({@code bio}/{@code SLB}/{@code SLR}) — page routing only, not the
   * record's code (which is resolved from the DB). */
  private static boolean isBiodiversity(String protocol) {
    String p = protocol.trim().toUpperCase();
    return p.equals("BIO") || p.equals("SLB") || p.equals("SLR");
  }

  /**
   * Historical biodiversity records carry code {@code SLB} and are view-only in the new app ({@code SLR}
   * is the go-forward code). Block every mutation authoritatively at the service layer — not just the UI.
   * See slb-to-slr-rename.local.md.
   */
  private static void assertEditable(String resourceType) {
    if ("SLB".equals(resourceType)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "This is a historical Stand Level Retention (SLB) record and is read-only.");
    }
  }

  // ── Offline snapshot (SLR only) ──────────────────────────────────────

  /** Attachment pages to walk per request when assembling the snapshot's full metadata set. */
  private static final int SNAPSHOT_ATTACHMENT_PAGE_SIZE = 50;

  /**
   * Assemble the whole SLR graph for taking a checklist offline.
   *
   * <p><b>Read-only on purpose.</b> This does not flip status or claim the checkout — the client
   * POSTs {@code /offline} afterwards. Reads first, checkout last, so an interrupted download leaves
   * the checklist editable online instead of stranded in {@code RDO} with no local copy to show for
   * it. An earlier draft of this design had the snapshot GET take the checkout; CHR's shipped flow
   * proved the opposite order, and the mid-download network kill was verified by hand.
   *
   * <p>Cost is inherent rather than accidental: there is no bulk read, so this is one call per
   * stratum plus one per plot. Field-sized data (a single block) is the expected shape.
   */
  public BioSnapshot getSnapshot(String checklistId) {
    assertSlrOnly(checklistId);

    BiodiversityOpening opening = getBiodiversityOpening(checklistId);
    String resourceType = checklistRepository.resolveResourceType(checklistId);

    List<BioSnapshot.BioStratumSnapshot> strata = new ArrayList<>();
    for (BioStratumRow stratumRow : listBioStrata(checklistId)) {
      BioStratum stratum = getBioStratum(stratumRow.stratumId());
      List<BioPlot> plots = new ArrayList<>();
      for (BioPlotRow plotRow : listBioPlots(stratumRow.stratumId())) {
        plots.add(getBioPlot(plotRow.plotId()));
      }
      strata.add(new BioSnapshot.BioStratumSnapshot(stratum, plots));
    }

    return new BioSnapshot(
        BioSnapshot.CURRENT_SCHEMA_VERSION,
        checklistId,
        resourceType,
        checklistRepository.getBioChecklistStatus(checklistId),
        opening,
        getNotes(resourceType, checklistId),
        strata,
        allAttachments(resourceType, checklistId));
  }

  /**
   * Every attachment's metadata, not one page.
   *
   * <p>The list read is paginated for the online tab, but an offline copy needs the complete set —
   * page 1 would silently truncate what the device can see and, worse, what it can flush back. Walks
   * to {@code totalCount} rather than adding an unpaginated read path, matching how CHR's
   * take-offline pages through photo metadata.
   */
  private List<AttachmentRow> allAttachments(String resourceType, String checklistId) {
    List<AttachmentRow> all = new ArrayList<>();
    for (int page = 0; ; page++) {
      List<AttachmentRow> current =
          getAttachments(resourceType, checklistId, page, SNAPSHOT_ATTACHMENT_PAGE_SIZE)
              .attachments();
      all.addAll(current);
      // Terminate on a short page, NOT on totalCount. The count is a separate query from the page, so
      // if the two ever disagree — a concurrent delete, or simply a bug — trusting it would silently
      // truncate the snapshot, and the device would neither see those attachments nor be able to
      // flush them back. A short page is self-evidently the last one. Costs at most one extra call
      // when the total is an exact multiple of the page size.
      if (current.size() < SNAPSHOT_ATTACHMENT_PAGE_SIZE) {
        return all;
      }
    }
  }

  /**
   * Check a device's edited graph back in: one transaction, then RDO → ACT.
   *
   * <p>Ordering is not stylistic — every step below was verified against DEV during the week-1 spike,
   * and three of them fail loudly if reordered:
   *
   * <ol>
   *   <li><b>The whole chain is one transaction.</b> The Bio save procs do not self-COMMIT and
   *       {@code executeCall} joins the Spring transaction, so a mid-chain conflict rolls everything
   *       back and the device can retry cleanly. Without {@code @Transactional} each proc call
   *       auto-commits independently and a failure at step 3 of 5 leaves the checklist half-synced.</li>
   *   <li><b>The opening's returned token is threaded into the notes save.</b> Both write the same
   *       {@code BIODIVERSITY_CHECKLIST} row and share its single {@code revision_count}, so the
   *       second call sending the device's stale token fails {@code record.modified2} —
   *       deterministically, in every sync that touches both tabs, not as a rare race.</li>
   *   <li><b>Creates and updates run before deletes</b>, so an id assigned earlier in this same sync
   *       cannot be deleted by a stale reference.</li>
   *   <li><b>Plot tombstones are applied before their stratum's.</b> The stratum delete <em>refuses</em>
   *       while any plot references it — it does not cascade — so the wrong order aborts the sync.</li>
   * </ol>
   *
   * <p>The checkout is held throughout and released only at the end, so a failure anywhere leaves the
   * device still holding its copy and its token.
   */
  @Transactional
  public BioCheckout uploadSnapshot(String checklistId, BioSnapshotUpload upload) {
    assertSlrOnly(checklistId);
    assertReadableSchema(upload.schemaVersion());
    // Full three-way check: this must be RDO and the caller must hold the checkout.
    assertChecklistEditable(checklistId, upload.deviceCheckoutGuid());

    String userId = loggedUserHelper.getLoggedUserId();

    // 1. Opening, and carry its new token forward — see the class note above.
    BiodiversityOpening savedOpening =
        writeRepository.saveBiodiversityOpening(upload.opening(), userId);

    // 2. Notes, on the token the opening just produced rather than the device's.
    if (upload.notes() != null) {
      writeRepository.saveNotes(
          new RiparianNotes(checklistId, upload.notes().noteDescription(),
              savedOpening.revisionCount()),
          checklistRepository.resolveResourceType(checklistId),
          userId);
    }

    // 3. Strata, then their plots. A stratum created offline gets its real id here, and its plots
    //    must be pointed at that id rather than the tmp: one they were captured against.
    for (BioSnapshotUpload.BioStratumUpload entry : nullSafe(upload.strata())) {
      BioStratum toSave = isTemporaryId(entry.stratum().stratumId())
          ? entry.stratum().withIdentity(null, null)
          : entry.stratum();
      BioStratum savedStratum =
          writeRepository.saveBioStratum(toSave.withChecklist(checklistId), userId);

      for (BioPlot plot : nullSafe(entry.plots())) {
        BioPlot plotToSave = isTemporaryId(plot.plotId())
            ? plot.withIdentity(null, savedStratum.stratumId(), null)
            : plot.withStratum(savedStratum.stratumId());
        writeRepository.saveBioPlot(plotToSave, userId);
      }
    }

    // 4. Deletes last, plots before strata.
    applyTombstones(upload.tombstones());

    // 5. Only now release the checkout.
    assertActivated(checklistId);
    return new BioCheckout(checklistId, ChrConstants.FrepChecklistStatusCode.ACT, null);
  }

  /**
   * Apply delete tombstones: every plot first, then every stratum.
   *
   * <p>Not merely tidy. {@code frep_biodiversity_stratum.validate_remove} refuses while any plot
   * references the stratum, so a stratum-first order gets {@code frep.error.usr.childexists} and
   * rolls the entire sync back on what reads like a data problem rather than an ordering bug.
   */
  private void applyTombstones(List<BioSnapshotUpload.Tombstone> tombstones) {
    List<BioSnapshotUpload.Tombstone> all = nullSafe(tombstones);
    for (BioSnapshotUpload.Tombstone tombstone : all) {
      if (BioSnapshotUpload.Tombstone.PLOT.equalsIgnoreCase(tombstone.entity())) {
        throwIfDeleteFailed(
            writeRepository.deleteBioPlot(tombstone.id(), tombstone.revisionCount()), tombstone);
      }
    }
    for (BioSnapshotUpload.Tombstone tombstone : all) {
      if (BioSnapshotUpload.Tombstone.STRATUM.equalsIgnoreCase(tombstone.entity())) {
        throwIfDeleteFailed(
            writeRepository.deleteBioStratum(tombstone.id(), tombstone.revisionCount()), tombstone);
      }
    }
  }

  /**
   * A failed delete must abort the sync, not be skipped. The delete procs report failure as a message
   * rather than an exception, so an unchecked return would silently drop the deletion and the row
   * would reappear on the device's next read — the exact "deleted strata come back" failure the
   * tombstone design exists to prevent.
   */
  private void throwIfDeleteFailed(String error, BioSnapshotUpload.Tombstone tombstone) {
    if (StringUtils.isNotBlank(error)) {
      throw new ConflictFoundException(
          "Could not delete " + tombstone.entity().toLowerCase() + " " + tombstone.id()
              + " during check-in: " + error + " Re-pull the checklist and try again.");
    }
  }

  /**
   * A snapshot written by a build this server cannot read is blocked, never migrated forward.
   * Silently reinterpreting an older graph is how a sync corrupts data rather than failing.
   */
  private static void assertReadableSchema(String schemaVersion) {
    if (!BioSnapshot.CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
      throw new InvalidParameterException(
          "This offline copy was made by a different version of the app (snapshot format "
              + schemaVersion + "; this server reads " + BioSnapshot.CURRENT_SCHEMA_VERSION
              + "). Update the app and sync again.");
    }
  }

  /**
   * Whether an id was minted on the device rather than by Oracle. Offline-created rows carry a
   * {@code tmp:} prefix (or nothing at all); anything else is a real sequence id.
   */
  private static boolean isTemporaryId(String id) {
    return StringUtils.isBlank(id) || id.startsWith("tmp:");
  }

  private static <T> List<T> nullSafe(List<T> values) {
    return values == null ? List.of() : values;
  }

  // ── Offline checkout (SLR only) ──────────────────────────────────────
  //
  // Mirrors the shipped CHR flow: take-offline hands the device a token, release lets that device
  // give the checkout back, and an admin-only activate recovers a checkout stranded on a lost or
  // wiped device. Status is what makes a checked-out checklist read-only online
  // (see assertChecklistEditable); the token is what stops a *different* device editing it.

  /**
   * Check an SLR checklist out to a field device (ACT → RDO) and issue its token.
   *
   * <p>The token is minted here rather than accepted from the client: it is proof the server issued
   * this checkout, so a caller cannot pick its own value and claim someone else's.
   */
  @Transactional
  public BioCheckout takeOffline(String checklistId) {
    assertSlrOnly(checklistId);
    UUID token = UUID.randomUUID();
    String error = writeRepository.takeOffline(
        checklistId, token, loggedUserHelper.getLoggedUserId());
    if (StringUtils.isNotBlank(error)) {
      // The proc refuses unless the row is ACT, so this is "already checked out, or submitted".
      throw new AccessForbiddenException(
          "This checklist can't be taken offline right now — it may already be checked out or "
              + "submitted. Refresh and try again.");
    }
    return new BioCheckout(checklistId, ChrConstants.FrepChecklistStatusCode.RDO, token.toString());
  }

  /**
   * Release a checkout on behalf of the device holding it (RDO → ACT), so the online copy is
   * editable again.
   *
   * <p>Idempotent, matching CHR: releasing a checklist that isn't checked out returns the current
   * state rather than failing, because the common cause is a device discarding a copy the server
   * already reclaimed.
   */
  @Transactional
  public BioCheckout releaseCheckout(String checklistId, String deviceCheckoutGuid) {
    assertSlrOnly(checklistId);
    String status = checklistRepository.getBioChecklistStatus(checklistId);
    if (!ChrConstants.FrepChecklistStatusCode.RDO.equals(status)) {
      return new BioCheckout(checklistId, status, null);
    }
    UUID serverToken = checklistRepository.getBioDeviceCheckoutGuid(checklistId);
    if (serverToken == null || !serverToken.toString().equals(deviceCheckoutGuid)) {
      throw new AccessForbiddenException(
          "This checklist is checked out on another device, so it can't be released here. "
              + "An administrator can activate it instead.");
    }
    assertActivated(checklistId);
    return new BioCheckout(checklistId, ChrConstants.FrepChecklistStatusCode.ACT, null);
  }

  /**
   * Admin recovery for a checkout stranded on a device that is lost, wiped, or simply never coming
   * back (RDO → ACT, no token required).
   *
   * <p>Clearing the token is the point: whatever is still on that device can never be uploaded
   * afterwards, so the caller must accept losing it. The endpoint is {@code FREP_ADMIN}-only.
   */
  @Transactional
  public BioCheckout activate(String checklistId) {
    assertSlrOnly(checklistId);
    assertActivated(checklistId);
    return new BioCheckout(checklistId, ChrConstants.FrepChecklistStatusCode.ACT, null);
  }

  private void assertActivated(String checklistId) {
    String error = writeRepository.activate(checklistId, loggedUserHelper.getLoggedUserId());
    if (StringUtils.isNotBlank(error)) {
      // The proc refuses unless the row is RDO.
      throw new AccessForbiddenException(
          "This checklist isn't checked out, so there is nothing to release or activate.");
    }
  }

  /**
   * Offline is SLR-only. SLB records are historical and read-only everywhere, so checking one out
   * would hand a device a copy it could never sync back.
   *
   * <p>Stricter than {@link #assertEditable}, which only excludes SLB: this requires SLR positively,
   * so a future third biodiversity code is refused by default rather than silently permitted.
   */
  private void assertSlrOnly(String checklistId) {
    String resourceType = checklistRepository.resolveResourceType(checklistId);
    assertEditable(resourceType);
    if (!"SLR".equals(resourceType)) {
      throw new AccessForbiddenException(
          "Only Stand Level Retention (SLR) checklists can be taken offline.");
    }
  }

  /** Resolve the record's code from the checklist id and enforce {@link #assertEditable}. A blank id
   * (e.g. an orphan stratum/plot lookup) is left for the downstream proc to report as not-found. */
  private void assertChecklistEditable(String checklistId) {
    assertChecklistEditable(checklistId, null);
  }

  /**
   * Full editability gate for a Biodiversity write: the SLB exclusion <em>and</em> the checklist's
   * status. Mirrors {@code ChrChecklistService.assertPhotoEditable}, which is the shipped precedent.
   *
   * <ul>
   *   <li>{@code ACT} — editable by anyone who passed the endpoint's authorization.</li>
   *   <li>{@code RDO} — checked out to a device. Editable <em>only</em> by the holder of that
   *       checkout, so the caller must present the matching {@code deviceCheckoutGuid}. The online
   *       per-section saves pass {@code null} and are therefore refused, which is what makes a
   *       checked-out checklist read-only in the UI.</li>
   *   <li>anything else — refused. {@code SUB} is the case that matters: submitted checklists were
   *       writable through the API until now, because nothing on this path read status at all.</li>
   * </ul>
   *
   * <p>RDO has to be allowed at all because the offline check-in flushes attachments and posts the
   * snapshot while the checklist is still checked out — the RDO → ACT flip happens at the end of that
   * sync, not before it.
   *
   * <p>The GUID comparison lives here rather than in PL/SQL (decided 2026-08-07): the procs only
   * store and clear the token, so the rule exists in exactly one place.
   */
  private void assertChecklistEditable(String checklistId, String deviceCheckoutGuid) {
    if (StringUtils.isBlank(checklistId)) {
      return;
    }
    assertEditable(checklistRepository.resolveResourceType(checklistId));

    String status = checklistRepository.getBioChecklistStatus(checklistId);
    if (status == null || ChrConstants.FrepChecklistStatusCode.ACT.equals(status)) {
      // Null here means the checklist row does not exist. FREP_CHECKLIST_STATUS_CODE is nullable in
      // the DDL (TABLES/V2.00399:5, unlike CHR_CHECKLIST's NOT NULL), but nothing ever writes a null
      // and the count is zero in every environment — so this is the not-found path, not a permissive
      // fallback for a real row. It defers to the downstream proc to report the bad id, exactly as
      // resolveResourceType does.
      return;
    }
    if (ChrConstants.FrepChecklistStatusCode.RDO.equals(status)) {
      UUID serverGuid = checklistRepository.getBioDeviceCheckoutGuid(checklistId);
      if (serverGuid == null || !serverGuid.toString().equals(deviceCheckoutGuid)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "This checklist is checked out to a device, so it can't be changed here. "
                + "Upload it from that device, or have it activated.");
      }
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "This checklist has been submitted and is read-only. Unsubmit it to make changes.");
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
        section("opening", "Opening info", () -> checklistRepository.getBioOpening(checklistId)),
        section("stratum", "Stratum summary", () -> checklistRepository.getBioStratum(checklistId)),
        section("plots", "Plots", () -> checklistRepository.getBioPlots(checklistId)),
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
