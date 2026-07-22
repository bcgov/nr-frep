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
import ca.bc.gov.nrs.frep.exception.InvalidPayloadException;
import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

  public ProtocolChecklistService(
      ChecklistRepository checklistRepository,
      CodeListRepository codeListRepository,
      ProtocolChecklistWriteRepository writeRepository,
      LoggedUserHelper loggedUserHelper,
      FamUserDirectoryService famUserDirectoryService
  ) {
    this.checklistRepository = checklistRepository;
    this.codeListRepository = codeListRepository;
    this.writeRepository = writeRepository;
    this.loggedUserHelper = loggedUserHelper;
    this.famUserDirectoryService = famUserDirectoryService;
  }

  /** Submit a protocol checklist (server-side DB validation + status to SUB). */
  public void submit(String protocolType, String checklistId) {
    String resourceType = resolveResourceType(protocolType);
    String error = writeRepository.submit(resourceType, checklistId, loggedUserHelper.getLoggedUserId());
    if (StringUtils.isNotBlank(error)) {
      throw new ProtocolSubmitValidationException(splitValidationMessages(error));
    }
  }

  /** Revert a submitted checklist to ACT. */
  public void unsubmit(String protocolType, String checklistId) {
    String resourceType = resolveResourceType(protocolType);
    String error = writeRepository.unsubmit(resourceType, checklistId, loggedUserHelper.getLoggedUserId());
    if (StringUtils.isNotBlank(error)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
  }

  /** Typed read of the Biodiversity Opening screen for editing (with the evaluator name resolved). */
  public BiodiversityOpening getBiodiversityOpening(String checklistId) {
    BiodiversityOpening opening = writeRepository.getBiodiversityOpening(checklistId);
    if (opening == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Biodiversity checklist not found: " + checklistId);
    }
    return withResolvedBioLead(opening);
  }

  /** Save the Biodiversity Opening via FREP_210_BIO_OPENING.SAVE, then apply any evaluator claim. */
  public BiodiversityOpening saveBiodiversityOpening(String checklistId, BiodiversityOpening opening) {
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
    writeRepository.assignBiodiversityLead(checklistId, resourceTypeForProtocol("bio"),
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
    } else if (opening.locationDescription().length() > 50) {
      errors.add("Location description must be 50 characters or fewer.");
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
      errors.add("Description must be 4000 characters or fewer.");
    }
    if ("Y".equals(opening.invasivePlantIndicator())
        && StringUtils.isBlank(opening.invasivePlantComment())) {
      errors.add("Enter a comment about the invasive plants.");
    } else if (length(opening.invasivePlantComment()) > 4000) {
      errors.add("Comments must be 4000 characters or fewer.");
    }
    if (length(opening.evaluatorOpinionComment()) > 2000) {
      errors.add("Rationale must be 2000 characters or fewer.");
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

  private static int length(String value) {
    return value == null ? 0 : value.length();
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
      errors.add("Stratum Id must start with a letter, in letters-then-digits order — up to 3 "
          + "letters then 2 digits, max 5 characters, no spaces (e.g. AB12).");
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
    if (value != null && value.length() > max) {
      errors.add(label + " must be " + max + " characters or fewer.");
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
    validateBioStratum(stratum);
    return writeRepository.saveBioStratum(stratum, loggedUserHelper.getLoggedUserId());
  }

  public void deleteBioStratum(String stratumId, String revisionCount) {
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
        requireFloat(r.dbh(), prefix + "DBH", 12.6, 400, 1, false, errors);
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
      errors.add(label + " must be between " + fmt(min) + " and " + fmt(max) + ".");
    }
  }

  public void deleteBioPlot(String plotId, String revisionCount) {
    String error = writeRepository.deleteBioPlot(plotId, revisionCount);
    if (StringUtils.isNotBlank(error)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
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

  public RiparianNotes getNotes(String protocol, String checklistId) {
    return writeRepository.getNotes(checklistId, resourceTypeForProtocol(protocol));
  }

  public RiparianNotes saveNotes(String protocol, String checklistId, RiparianNotes notes) {
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
    validateAttachmentType(fileName);
    String resourceType = resourceTypeForProtocol(protocol);
    writeRepository.saveAttachment(checklistId, resourceType, fileName, description, mimeType, bytes,
        loggedUserHelper.getLoggedUserId());
    return writeRepository.getAttachments(checklistId, resourceType);
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

  public List<AttachmentRow> deleteAttachment(
      String protocol, String checklistId, String attachmentId) {
    String resourceType = resourceTypeForProtocol(protocol);
    writeRepository.deleteAttachment(checklistId, resourceType, attachmentId);
    return writeRepository.getAttachments(checklistId, resourceType);
  }

  private String resolveResourceType(String protocolType) {
    return normalizeProtocolType(protocolType)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Unknown protocol type: " + protocolType));
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
    String evaluatorUserid = header.evaluatorUserid();
    String evaluatorName = StringUtils.isBlank(evaluatorUserid)
        ? evaluatorUserid
        : famUserDirectoryService.resolveName(evaluatorUserid).orElse(evaluatorUserid);
    return Optional.of(new ProtocolChecklistResponse(
        checklistId,
        oracleProtocol,
        protocolNames.getOrDefault(oracleProtocol, oracleProtocol),
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

  static Optional<String> normalizeProtocolType(String protocolType) {
    if (StringUtils.isBlank(protocolType)) {
      return Optional.empty();
    }
    // Riparian (RIP) and Water (WTR) are out of scope — only Biodiversity (SLB) and the shared
    // bio/chr protocol segments are recognised.
    return switch (protocolType.trim().toUpperCase()) {
      case "BIO", "SLB" -> Optional.of("SLB");
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
