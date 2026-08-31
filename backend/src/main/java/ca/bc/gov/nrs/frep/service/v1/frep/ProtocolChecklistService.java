package ca.bc.gov.nrs.frep.service.v1.frep;

import ca.bc.gov.nrs.frep.struct.v1.frep.BioCwdRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Protocol checklist lookup backed by legacy Oracle GET procedures via
 * {@link ChecklistRepository}.
 */
@Service
public class ProtocolChecklistService {

  private static final Logger log = LoggerFactory.getLogger(ProtocolChecklistService.class);

  // Allowed attachment types = the codes in THE.MIME_TYPE_CODE (keyed by file extension). The
  // FREP_CHECKLIST_ATTACHMENTS proc stores only these and rejects anything else with an ORA-01400
  // (NULL mime_type_code). Guard here so an unsupported type is a clean 400 instead of a 500.
  private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
      "BMP", "CSV", "DOC", "GIF", "HTM", "IFM", "JPG", "JPK", "MDB", "MDE", "OBD", "PDF", "PNG",
      "PPS", "PPT", "RPT", "RTF", "TIF", "TXT", "WAV", "XLD", "XLS", "XML", "ZIP");
  private static final String ALLOWED_ATTACHMENT_TYPES_DISPLAY =
      "BMP, CSV, DOC, GIF, HTM, IFM, JPG, JPK, MDB, MDE, OBD, PDF, PNG, PPS, PPT, RPT, RTF, TIF, TXT, "
          + "WAV, XLD, XLS, XML, ZIP";

  // Numeric-format guards for user-supplied field values.
  //
  // Written as an explicit alternation rather than the more obvious
  // `[-+]?\d*\.?\d+`. In that form `\d*` and `\d+` overlap with only an optional
  // `\.?` between them, so a long run of digits that ultimately FAILS to match
  // (e.g. 20k digits then a letter) makes the engine retry every split point —
  // quadratic backtracking, and these values are attacker-controlled with no
  // upstream length cap. Measured on the old form: 16k chars ≈ 0.9s of CPU per
  // call, scaling 4x per doubling. The alternation below cannot overlap, so it
  // is linear, and it accepts/rejects exactly the same strings.
  //
  // Both accept: 123, 1.5, .5 (and a leading sign, where allowed). Both reject:
  // "1." (trailing dot), "", ".", "1e5", "1..2".
  // Keep these as the single source of truth — the old inline form previously
  // existed at four separate call sites and static analysis only caught two.
  private static final Pattern SIGNED_DECIMAL =
      Pattern.compile("[-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)");
  private static final Pattern UNSIGNED_DECIMAL =
      Pattern.compile("(?:\\d+(?:\\.\\d+)?|\\.\\d+)");

  /** Hard cap on attachment rows returned per call; matches SearchService / OpeningTargetService. */
  private static final int MAX_PAGE_SIZE = 100;

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
      // Not an error: the proc refused because the record is incomplete. Logged at info so support
      // can see how often submit is blocked and on how many rules, without a user having to report
      // it — the messages themselves stay out of the log, since they can name site data.
      List<String> messages = splitValidationMessages(error);
      log.info("Submit blocked for {} checklist :: {} by {} validation failure(s)", resourceType,
          checklistId, messages.size());
      throw new ProtocolSubmitValidationException(messages);
    }
    log.info("Submitted {} checklist :: {} by user :: {}", resourceType, checklistId,
        loggedUserHelper.getLoggedUserId());
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
   * Validate the Biodiversity Opening for <b>saving</b>.
   *
   * <p>Only rules the stored row cannot survive are enforced here: the byte-length limits the
   * columns declare ({@code LOCATION_DESCRIPTION VARCHAR2(50 BYTE)}, the 4000-byte comments, the
   * 2000-byte rationale) and the FREP gross-area override's number format (float, 0.01–99999.99,
   * two decimals). Breaking one of those means an {@code ORA-12899} or a nonsense number in the
   * column, so they stay a hard {@link InvalidPayloadException} (HTTP 400).
   *
   * <p><b>Completeness is deliberately not enforced.</b> The legacy FREP210
   * {@code Frep210ValidationManager} "Save" chain also required Location description, Invasive
   * plant?, Innovative practice?, Rating and the two conditional comments, and this method used to
   * copy that. An evaluator working a block in the field routinely has some of those answers and not
   * others, and refusing the save cost them the answers they did have. They are now reported rather
   * than refused: the tab marks them, counts them and warns after the save, and
   * {@code FREP_TOMBSTONE.validate_biodiversity_chklst} still blocks <i>submit</i> on the three it
   * owns (evaluation date, evaluation team lead, location description). A partially filled Opening
   * is a legitimate saved state; an unsubmittable one.
   */
  private static void validateBiodiversityOpening(BiodiversityOpening opening) {
    List<String> errors = new ArrayList<>();

    if (length(opening.locationDescription()) > 50) {
      errors.add(tooLong("Location description", opening.locationDescription(), 50));
    }
    if (length(opening.innovativePracticesComment()) > 4000) {
      errors.add(tooLong("Description", opening.innovativePracticesComment(), 4000));
    }
    if (length(opening.invasivePlantComment()) > 4000) {
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
    if (!SIGNED_DECIMAL.matcher(text).matches()) {
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

    // The four the database itself insists on: BIODIVERSITY_STRATUM declares them NOT NULL and
    // FREP_BIODIVERSITY_STRATUM.validate_mandatories re-checks three of them. A blank one is an
    // ORA-01400, not a preference, so these stay a hard refusal. BGC subzone joins them because
    // validate_bec runs the whole BEC combination through FREP_VALIDATE_BGC against the catalogue.
    requireField(s.consistentMapInd(), "Consistent with map", errors);
    requireField(s.plotCount(), "Plot count", errors);
    requireField(s.harvestAreaCode(), "Harvest area", errors);
    requireField(s.bgcZoneCode(), "BGC zone", errors);
    requireField(s.bgcSubzoneCode(), "BGC subzone", errors);

    // Stratum number, stratum type, the size fields and the patch details are nullable columns, so a
    // half-entered stratum stores fine. They are reported on the tab and counted against submit
    // rather than refused here — see the note on validateBiodiversityOpening.

    if (StringUtils.isNotBlank(s.stratumNumber()) && !stratumNumberValid(s.stratumNumber().trim())) {
      errors.add("Stratum Id: use 1-3 letters then 0-2 digits, e.g. AB12.");
    }
    if ("0".equals(trimmedOrEmpty(s.plotCount())) && !type.isEmpty() && !type.startsWith("P")) {
      errors.add("A stratum with 0 plots must be a patch stratum type.");
    }
    // A contradiction between two entered values, not a gap: keep refusing it.
    if ("M".equals(consistent) && isNumeric(s.size()) && Double.parseDouble(s.size().trim()) != 0) {
      errors.add("Stratum size must be blank when \"Not mapped\".");
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
    return StringUtils.isNotBlank(value) && SIGNED_DECIMAL.matcher(value.trim()).matches();
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
    if (!SIGNED_DECIMAL.matcher(value).matches()) {
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


  /**
   * Reject a non-numeric id before it reaches the driver.
   *
   * {@code BIODIVERSITY_PLOT.BIODIVERSITY_PLOT_ID} is a NUMBER, so binding a blank or non-numeric
   * string fails inside Oracle as "Invalid Input Number" — which surfaces to the evaluator as
   * "A database error occurred… contact the FREP help desk" and is logged as a system fault, with
   * nothing anywhere naming the value that caused it. A 400 that quotes the id says what happened
   * and leaves a usable trace.
   */
  private static void requireNumericId(String value, String what) {
    if (!StringUtils.isNumeric(value)) {
      log.warn("Rejected non-numeric {}: '{}'", what, value);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid " + what + ": '" + value + "'");
    }
  }

  public BioPlot getBioPlot(String plotId) {
    requireNumericId(plotId, "plot id");
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

    // UTM and the bearings are nullable, so a plot recorded before the GPS fix (or before the
    // transect is walked) stores fine — the missing values are counted on the tab and block submit.
    // A value that *is* entered still has to be the right shape.
    if (!"N".equals(trimmedOrEmpty(p.utmSignal()))) {
      if (StringUtils.isNotBlank(p.utmEasting()) && !p.utmEasting().trim().matches("\\d{6}")) {
        errors.add("Easting must be exactly 6 digits.");
      }
      if (StringUtils.isNotBlank(p.utmNorthing()) && !p.utmNorthing().trim().matches("\\d{7}")) {
        errors.add("Northing must be exactly 7 digits.");
      }
    }

    intRange(p.firstLegTransect(), "Bearing 1st leg", 0, 359, errors);
    intRange(p.secondLegTransect(), "2nd leg", 0, 359, errors);
    // BIODIVERSITY_PLOT.ASSESSOR_NAME is NOT NULL (and validate_mandatories re-checks it).
    requireField(p.assessorName(), "Evaluated by", errors);

    intRange(p.plotNumber(), "Plot #", 0, 999, errors);
    maxLength(p.plotComment(), "Comments", 2000, errors);
    intRange(p.basalAreaFactor(), "BAF", 1, 99, errors);
    numRange(p.fixedAreaRadius(), "Fixed area radius", 0.01, 999.99, errors);
    decimalLimit(p.fixedAreaRadius(), "Fixed area radius", 2, errors);
    numRange(p.fullCountArea(), "Full count area", 0.01, 9999.99, errors);
    decimalLimit(p.fullCountArea(), "Full count area", 2, errors);

    // Naming two measurement methods is a contradiction and still refused; naming none is just a
    // plot not measured yet.
    long methods = Stream.of(p.basalAreaFactor(), p.fixedAreaRadius(), p.fullCountArea())
        .filter(StringUtils::isNotBlank).count();
    if (methods > 1) {
      errors.add("Enter only one of BAF, fixed area radius, or full count area.");
    }

    // Every column of BIODIVERSITY_STAND_DETAIL and COARSE_WOODY_DEBRIS_DETAIL is NOT NULL, so a row
    // the user has added must be complete before it can be stored. Having no rows at all is fine.
    if ("Y".equals(trimmedOrEmpty(p.treeIndicator()))) {
      List<BioStandRow> stand = p.standTable() == null ? List.of() : p.standTable();
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

  private static void requireFloat(String value, String label, double min, double max,
      int maxDecimals, boolean exclusiveMin, List<String> errors) {
    if (StringUtils.isBlank(value)) {
      errors.add(label + " is required.");
      return;
    }
    String text = value.trim();
    if (!UNSIGNED_DECIMAL.matcher(text).matches()) {
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
   *
   * <p>{@code size} is clamped to {@link #MAX_PAGE_SIZE} (and at least 1) and {@code page} floored
   * at 0, matching SearchService / OpeningTargetService. The cap matters more here than on a plain
   * query: the loop below issues one object-storage HEAD <em>per row</em>, so an unclamped
   * {@code ?size=} would turn a single request into that many sequential remote calls. A negative
   * page would reach Oracle as a negative OFFSET.
   */
  public AttachmentPage getAttachments(String protocol, String checklistId, int page, int size) {
    int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    int safePage = Math.max(0, page);
    String resourceType = checklistRepository.resolveResourceType(checklistId);
    List<AttachmentRow> rows =
        writeRepository.getAttachments(checklistId, resourceType, safePage, safeSize);
    List<AttachmentRow> withSizes = rows.stream()
        .map(row -> {
          long bytes = objectStorage.getObjectSize(bioObjectKey(row.checklistAttachmentId()));
          return new AttachmentRow(row.checklistAttachmentId(), row.fileName(), row.description(),
              row.mimeTypeCode(), bytes < 0 ? null : String.valueOf(bytes));
        })
        .toList();
    return new AttachmentPage(withSizes, writeRepository.countAttachments(checklistId, resourceType));
  }

  /**
   * Object key for a Biodiversity attachment; resolved through {@link
   * ObjectStorageService#bioObjectKey} so the size shown in the list is HEADed from the same key the
   * download reads. This one previously skipped the {@code trim()} the write path applies — harmless
   * in practice, since ids come from a NUMBER column, but it meant a blank size rather than a failed
   * download would have been the only symptom if that ever stopped holding.
   */
  private static String bioObjectKey(String attachmentId) {
    return ObjectStorageService.bioObjectKey(attachmentId);
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
      String protocol, String checklistId, MultipartFile file, String description) {
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
    log.info("Uploaded attachment :: {} ({} bytes) to checklist :: {} by user :: {}", fileName,
        bytes.length, checklistId, loggedUserHelper.getLoggedUserId());
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

  public void deleteAttachment(String protocol, String checklistId, String attachmentId) {
    String resourceType = checklistRepository.resolveResourceType(checklistId);
    assertEditable(resourceType);
    writeRepository.deleteAttachment(checklistId, resourceType, attachmentId);
    log.info("Deleted attachment :: {} from {} checklist :: {} by user :: {}", attachmentId,
        resourceType, checklistId, loggedUserHelper.getLoggedUserId());
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

  /** Resolve the record's code from the checklist id and enforce {@link #assertEditable}. A blank id
   * (e.g. an orphan stratum/plot lookup) is left for the downstream proc to report as not-found. */
  private void assertChecklistEditable(String checklistId) {
    if (StringUtils.isNotBlank(checklistId)) {
      assertEditable(checklistRepository.resolveResourceType(checklistId));
    }
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
