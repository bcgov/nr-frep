package ca.bc.gov.nrs.frep.validation;

import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.Feature;
import ca.bc.gov.nrs.frep.struct.v1.frep.OtherPlannedManagementStrategy;
import ca.bc.gov.nrs.frep.struct.v1.frep.Picture;
import ca.bc.gov.nrs.frep.struct.v1.frep.ValidationError;
import ca.bc.gov.nrs.frep.util.ChrStringUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Port of legacy {@code SubmitCHRChecklistValidationManager}. Reproduces the CHR submit rules:
 * mandatory checklist + feature fields, conditional "if X then describe Y" requirements,
 * at-least-one / none-allowed group selections, numeric and Borden-number format checks, and the
 * Other-planned-strategy uniqueness rules. WebADE/form infrastructure is replaced with direct
 * checks over the {@link CheckList}/{@link Feature} DTOs.
 */
@Service
public class ChrSubmitValidationService {

  private static final String TYPE = "VALIDATION";

  // Feature-description "type of feature(s)" group — at least one must be selected.
  private static final String[] FEATURE_TYPE_FIELDS = {
    "culturalTraildesignated", "culturalTrailundesignated", "burialSite", "nest",
    "ceremonialSite", "cremationSite", "ofCMTs", "caveorotherKarst", "den",
    "traditionalUseSite", "cedarBarkStripArea", "rockOutcrop", "spiritualSite",
    "ofMonumentalCedars", "culturalDepression", "lithics", "other",
  };

  private static final String[] AGE_FIELDS = {"pre1846", "post1846", "ageUnknown", "historicalUse"};

  private static final String[] Q2_CAUSE_FIELDS = {
    "harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "fireQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "roadQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause",
    "otherQ2Wheredamagehasoccurredwhatisthemostlikelycause",
  };

  private static final String[] USED_STRATEGY_FIELDS = {
    "partiallytemporaryreserve", "fullyconservedintemporaryreserve",
    "partiallyconservedinpermanentreserve", "fullyconservedinpermanentreserve",
    "modifiedblockboundary", "retainabuffer", "compledCrownorstandmodification",
    "datedthefeature", "retainedinharvestareanobuffer", "leftStanding", "stubbed",
    "alteredsilviculture", "otherActivities",
  };

  private static final String[] WINDTHROW_OTHER_FIELDS = {
    "windthrowTechniqueRetentionBuffer", "windthrowTechniquePruning",
    "windthrowTechniqueFeathering", "windthrowTechniqueTopping", "otherTechnique",
  };

  // FN / SP planning strategy groups (the AIA group is gated on permit, see below).
  private static final String[] FN_STRATEGY_FIELDS = {
    "modifyBlockBoundaryFN", "retainBufferFN", "retaininHarvestAreaFN", "crownorstandmodificationFN",
    "conserveinRotationalReserveFN", "permanentReserveFN", "datetheFeatureFN", "stubCMTsabovescarFN",
    "stubnonCMTsFN", "leaveStandingFN", "machineFreeZoneFN", "harvestUnderSapFN",
    "winterHarvestFrozenGroundFN", "avoidSilvAvoidPlantingFN", "avoidSilvAvoidSitePrepFN",
  };

  private static final String[] SP_STRATEGY_FIELDS = {
    "modifyBlockBoundarySP", "retainBufferSP", "retaininHarvestAreaSP", "crownorstandmodificationSP",
    "conserveinRotationalReserveSP", "permanentReserveSP", "datetheFeatureSP", "stubCMTsabovescarSP",
    "stubnonCMTsSP", "leaveStandingSP", "machineFreeZoneSP", "harvestUnderSapSP",
    "winterHarvestFrozenGroundSP", "avoidSilvAvoidPlantingSP", "avoidSilvAvoidSitePrepSP",
  };

  private static final String BORDEN_REGEX = "^[A-U][a-l][A-W][a-x]-[0-9]{1,4}$";

  public List<ValidationError> validateBeforeSubmit(CheckList checklist) {
    List<ValidationError> errors = new ArrayList<>();
    if (checklist == null) {
      errors.add(new ValidationError(TYPE, "Checklist payload is required.", "checklist"));
      return errors;
    }

    validateChecklistLevel(checklist, errors);

    if (checklist.getFeatures() == null || checklist.getFeatures().isEmpty()) {
      errors.add(err(checklist.getChecklistID(), "features",
          "At least one feature is required before submit."));
    } else {
      for (Feature feature : checklist.getFeatures()) {
        validateComposite(checklist, feature, errors);
        validateFeature(checklist, feature, errors);
      }
    }

    validatePhotos(checklist, errors);
    return errors;
  }

  // --- checklist level ---

  private void validateChecklistLevel(CheckList c, List<ValidationError> errors) {
    String ref = c.getChecklistID();
    req(errors, ref, "evaluationDate", c.getEvaluationDate(),
        "Please enter the date the evaluation occurred.");
    req(errors, ref, "yearOfHarvest", c.getYearOfHarvest(),
        "Please confirm the Harvest Completion Year.");
    req(errors, ref, "generalLocation", c.getGeneralLocation(),
        "Please provide a general location name.");
    req(errors, ref, "assessedBy", c.getAssessedBy(),
        "Please save the opening info so the Evaluator field is populated.");
    req(errors, ref, "rating", c.getRating(), "Please provide a Rating in Block summary.");

    conditional(errors, ref, "q8Comments",
        c.getQ8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock(),
        c.getQ8Comments(), "Please provide a description.");
    conditional(errors, ref, "q9Comments",
        c.getQ9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues(),
        c.getQ9Comments(), "Please provide a description.");
    conditional(errors, ref, "q10Comments",
        c.getQ10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock(),
        c.getQ10Comments(), "Please provide a description.");
  }

  // --- composite ---

  private void validateComposite(CheckList c, Feature feature, List<ValidationError> errors) {
    if (!isTrue(feature.getCompositeFeatureInd())) {
      return;
    }
    long members = c.getFeatures().stream()
        .filter(f -> feature.getFeatureLabel() != null
            && feature.getFeatureLabel().equalsIgnoreCase(f.getCompositeFeature()))
        .count();
    if (members < 2) {
      errors.add(err(ref(c, feature), "composite",
          "Composite has only one feature member — please select another feature to be a member of the composite."));
    }
  }

  // --- per feature ---

  private void validateFeature(CheckList c, Feature feature, List<ValidationError> errors) {
    String ref = ref(c, feature);

    if (!ChrStringUtils.hasAValue(feature.getFeatureLabel())) {
      errors.add(err(ref, "featureLabel", "Each feature must have a feature label."));
    }

    // Non-composite features require a description code + information source.
    if ("false".equals(feature.getCompositeFeatureInd())) {
      req(errors, ref, "featureDescriptionCode", feature.getFeatureDescriptionCode(),
          "Please provide a Description Code.");
      req(errors, ref, "featureInfoSourceCode", feature.getFeatureInfoSourceCode(),
          "Please provide an Information Source Code.");
    }

    // Members of a composite skip the per-tab validation (validated via their parent).
    if (ChrStringUtils.hasAValue(feature.getCompositeFeature())) {
      return;
    }

    validateDescriptionTab(c, feature, errors);
    validateLocationAgeTab(c, feature, errors);
    validatePlanningTab(c, feature, errors);
    validateEffectivenessTab(c, feature, errors);
    validateSummaryTab(c, feature, errors);
  }

  private void validateDescriptionTab(CheckList c, Feature f, List<ValidationError> errors) {
    String ref = ref(c, f);
    conditional(errors, ref, "ofCMTsNumber", f.getOfCMTs(), f.getOfCMTsNumber(),
        "Please enter the number of CMT(s) included in the feature.");
    conditional(errors, ref, "standofMonumentalCedar", f.getOfMonumentalCedars(),
        f.getStandofMonumentalCedar(),
        "Please enter the number of Monumental Cedar(s) included in the feature.");
    conditional(errors, ref, "otherDescription", f.getOther(), f.getOtherDescription(),
        "Please provide a description of the feature.");
    integer(errors, ref, "ofCMTsNumber", f.getOfCMTsNumber(),
        "Please enter a valid number of CMT(s) included in the feature.");
    integer(errors, ref, "standofMonumentalCedar", f.getStandofMonumentalCedar(),
        "Please enter a valid number of Monumental Cedar(s) included in the feature.");

    if (!anyTrue(values(f, FEATURE_TYPE_FIELDS))) {
      errors.add(err(ref, "siteFeatureDescription",
          "Please ensure at least one feature description has been selected."));
    }

    if (isTrue(f.getChrRegisteredSite()) && ChrStringUtils.hasAValue(f.getBorden())
        && !f.getBorden().matches(BORDEN_REGEX)) {
      errors.add(err(ref, "borden",
          "Please provide a Borden # in one of the formats AaBb-0, AaBb-00, AaBb-000, or AaBb-0000."));
    }
  }

  private void validateLocationAgeTab(CheckList c, Feature f, List<ValidationError> errors) {
    String ref = ref(c, f);
    conditional(errors, ref, "locationOtherDescription", f.getLocationOther(),
        f.getLocationOtherDescription(), "Please provide a description of the location if Other.");
    conditional(errors, ref, "locationReservetype", f.getInReserve(), f.getLocationReservetype(),
        "Please provide the In Reserve \"Type\".");
    if (!anyTrue(values(f, AGE_FIELDS))) {
      errors.add(err(ref, "age", "Please select at least one item for the Age of this feature."));
    }
  }

  private void validatePlanningTab(CheckList c, Feature f, List<ValidationError> errors) {
    String ref = ref(c, f);
    planningDetail(errors, ref, f.getRetainBufferFN(), f.getBufferLengthFN(), "bufferLengthFN",
        "Please provide the buffer size in metres.");
    planningDetail(errors, ref, f.getRetainBufferAIA(), f.getBufferLengthAIA(), "bufferLengthAIA",
        "Please provide the buffer size in metres.");
    planningDetail(errors, ref, f.getRetainBufferSP(), f.getBufferLengthSP(), "bufferLengthSP",
        "Please provide the buffer size in metres.");
    planningDetail(errors, ref, f.getConserveinRotationalReserveFN(),
        f.getConserveRotationalReserveTypeFN(), "conserveRotationalReserveTypeFN",
        "Please provide the Rotational Reserve \"Type\".");
    planningDetail(errors, ref, f.getConserveinRotationalReserveAIA(),
        f.getConserveRotationalReserveTypeAIA(), "conserveRotationalReserveTypeAIA",
        "Please provide the Rotational Reserve \"Type\".");
    planningDetail(errors, ref, f.getConserveinRotationalReserveSP(),
        f.getConserveRotationalReserveTypeSP(), "conserveRotationalReserveTypeSP",
        "Please provide the Rotational Reserve \"Type\".");
    planningDetail(errors, ref, f.getPermanentReserveFN(), f.getTemporaryRetentionTypeFN(),
        "temporaryRetentionTypeFN", "Please provide the Temporary Reserve Area \"Type\".");
    planningDetail(errors, ref, f.getPermanentReserveAIA(), f.getTemporaryRetentionTypeAIA(),
        "temporaryRetentionTypeAIA", "Please provide the Temporary Reserve Area \"Type\".");
    planningDetail(errors, ref, f.getPermanentReserveSP(), f.getTemporaryRetentionTypeSP(),
        "temporaryRetentionTypeSP", "Please provide the Temporary Reserve Area \"Type\".");
    integer(errors, ref, "bufferLengthFN", f.getBufferLengthFN(),
        "Please provide a valid buffer size in metres.");
    integer(errors, ref, "bufferLengthAIA", f.getBufferLengthAIA(),
        "Please provide a valid buffer size in metres.");
    integer(errors, ref, "bufferLengthSP", f.getBufferLengthSP(),
        "Please provide a valid buffer size in metres.");

    validateOtherStrategy(c, f, errors);

    boolean otherFn = anyOtherStrategyInd(f, OtherPlannedManagementStrategy::getFnInd);
    boolean otherSp = anyOtherStrategyInd(f, OtherPlannedManagementStrategy::getSpInd);

    if (isTrue(f.getManagementStrategyFN())
        && !(anyTrue(values(f, FN_STRATEGY_FIELDS)) || otherFn)) {
      errors.add(err(ref, "recommendationsProvided",
          "First Nations recommendations is toggled 'ON' but no related management strategies are entered. "
              + "Please input related management strategies OR unselect \"Recommendations Provided\"."));
    }
    if (isTrue(f.getSitePermitIssued())) {
      req(errors, ref, "permit", f.getPermit(),
          "SAP is toggled 'ON' but no Site Alteration Permit number has been entered OR unselect \"SAP Issued\".");
    }
    if (isTrue(f.getManagementStrategySP())
        && !(anyTrue(values(f, SP_STRATEGY_FIELDS)) || otherSp)) {
      errors.add(err(ref, "strategyProvided",
          "Site plan recommendations is toggled 'ON' but no related management strategies are entered. "
              + "Please input related management strategies OR unselect \"Recommendations Provided\"."));
    }
  }

  private void validateEffectivenessTab(CheckList c, Feature f, List<ValidationError> errors) {
    String ref = ref(c, f);
    conditional(errors, ref, "bufferWidthMeter", f.getRetainabuffer(), f.getBufferWidthMeter(),
        "Please provide the buffer size in metres.");
    conditional(errors, ref, "partiallytemporaryreservetype", f.getPartiallytemporaryreserve(),
        f.getPartiallytemporaryreservetype(), "Please provide the Temporary Reserve \"Type\".");
    conditional(errors, ref, "fullytemporaryreserve", f.getFullyconservedintemporaryreserve(),
        f.getFullytemporaryreserve(), "Please provide the Temporary Reserve \"Type\".");
    conditional(errors, ref, "partiallyconservedinpermanentreserveType",
        f.getPartiallyconservedinpermanentreserve(), f.getPartiallyconservedinpermanentreserveType(),
        "Please provide the Permanent Reserve \"Type\".");
    conditional(errors, ref, "fullyconservedinpermanentreserveType",
        f.getFullyconservedinpermanentreserve(), f.getFullyconservedinpermanentreserveType(),
        "Please provide the Permanent Reserve \"Type\".");
    conditional(errors, ref, "ifotherpleasedescribeOtherQ2",
        f.getOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause(),
        f.getIfotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause(),
        "Please provide a description for \"Other\" Damage Cause.");
    conditional(errors, ref, "ifotherpleasedescribe", f.getOtherTechnique(),
        f.getIfotherpleasedescribe(),
        "Please provide a description for \"Other\" Windthrow Management Technique.");
    conditional(errors, ref, "trailLength", f.getIsthereevidenceofdamage(), f.getTrailLength(),
        "Please provide an estimated percentage of the trail length affected.");
    integer(errors, ref, "bufferWidthMeter", f.getBufferWidthMeter(),
        "Please provide a valid buffer size in metres.");
    integer(errors, ref, "trailLength", f.getTrailLength(),
        "Please provide a valid estimated percentage of the trail length affected.");

    if (isTrue(f.getNoManagement()) && anyTrue(values(f, USED_STRATEGY_FIELDS))) {
      errors.add(err(ref, "noManagement",
          "Please unselect any selected Management Strategies OR unselect \"No Management of the Site or feature\"."));
    }
    if (isTrue(f.getQ1Isthereevidenceofdamagetothesiteorfeature())
        && !anyTrue(values(f, Q2_CAUSE_FIELDS))) {
      errors.add(err(ref, "q2MostLikelyCause", "Please provide at least one answer in Q2 if Q1 is selected."));
    }
    if (isTrue(f.getQ3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse())
        && !anyTrue(values(f, Q2_CAUSE_FIELDS))) {
      errors.add(err(ref, "q2MostLikelyCause", "Please provide at least one answer in Q2 if Q3 is selected."));
    }
    if (isTrue(f.getWindthrowManagement()) && isTrue(f.getWindthrowTechniqueNone())
        && anyTrue(values(f, WINDTHROW_OTHER_FIELDS))) {
      errors.add(err(ref, "windthrowNone",
          "Please unselect any selected Windthrow Management Techniques OR unselect \"None\"."));
    }
  }

  private void validateSummaryTab(CheckList c, Feature f, List<ValidationError> errors) {
    String ref = ref(c, f);
    req(errors, ref, "featureRating", f.getFeatureRating(),
        "Please provide a Rating in Feature Summary.");
    conditional(errors, ref, "q4Description",
        f.getQ4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature(),
        f.getQ4Description(), "Please provide a description in Feature Summary.");
    conditional(errors, ref, "q5Description",
        f.getQ5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective(),
        f.getQ5Description(), "Please provide a description in Feature Summary.");
    conditional(errors, ref, "q6Description",
        f.getQ6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature(),
        f.getQ6Description(), "Please provide a description in Feature Summary.");
  }

  /** Other-planned-strategy rules: description required, must have a source, descriptions unique. */
  private void validateOtherStrategy(CheckList c, Feature f, List<ValidationError> errors) {
    if (f.getOtherPlannedManagementStrategy() == null) {
      return;
    }
    String ref = ref(c, f);
    List<OtherPlannedManagementStrategy> list = f.getOtherPlannedManagementStrategy();
    boolean reportedBlank = false;
    List<String> reportedDuplicate = new ArrayList<>();
    for (OtherPlannedManagementStrategy strategy : list) {
      if (!ChrStringUtils.hasAValue(strategy.getOtherStrategy())) {
        if (!reportedBlank) {
          errors.add(err(ref, "otherPlanningStrategy",
              "Please provide a description for any management strategies defined as 'Other'."));
          reportedBlank = true;
        }
        continue;
      }
      boolean hasSource = isTrue(strategy.getFnInd()) || isTrue(strategy.getAiaInd())
          || isTrue(strategy.getSpInd());
      if (!hasSource) {
        errors.add(err(ref, "otherPlanningStrategy", "'Other' management strategy: '"
            + strategy.getOtherStrategy() + "' must have a Management Strategy defined."));
        continue;
      }
      String value = strategy.getOtherStrategy();
      long count = list.stream()
          .filter(s -> value.equals(s.getOtherStrategy())).count();
      if (count > 1 && !reportedDuplicate.contains(value)) {
        reportedDuplicate.add(value);
        errors.add(err(ref, "otherPlanningStrategy", "There is more than one 'Other' management strategy defined as '"
            + value + "'. Each 'Other' management strategy description must be unique."));
      }
    }
  }

  private void validatePhotos(CheckList checklist, List<ValidationError> errors) {
    if (checklist.getPictures() == null) {
      return;
    }
    for (Picture picture : checklist.getPictures()) {
      if (!ChrStringUtils.hasAValue(picture.getDescription())) {
        errors.add(err(checklist.getChecklistID(), "pictureDescription",
            "Each photo requires a description."));
      }
    }
  }

  // --- helpers ---

  private interface IndAccessor {
    String get(OtherPlannedManagementStrategy s);
  }

  private boolean anyOtherStrategyInd(Feature f, IndAccessor accessor) {
    if (f.getOtherPlannedManagementStrategy() == null) {
      return false;
    }
    return f.getOtherPlannedManagementStrategy().stream().anyMatch(s -> isTrue(accessor.get(s)));
  }

  private static boolean isTrue(String value) {
    return "true".equalsIgnoreCase(value);
  }

  private static boolean anyTrue(String[] values) {
    for (String v : values) {
      if (isTrue(v)) {
        return true;
      }
    }
    return false;
  }

  /** Resolve a set of feature indicator fields to their values via reflection-free getters. */
  private static String[] values(Feature f, String[] fields) {
    String[] out = new String[fields.length];
    for (int i = 0; i < fields.length; i++) {
      out[i] = readIndicator(f, fields[i]);
    }
    return out;
  }

  /** Read a feature indicator by field name. Uses a switch to avoid reflection. */
  private static String readIndicator(Feature f, String field) {
    return switch (field) {
      case "culturalTraildesignated" -> f.getCulturalTraildesignated();
      case "culturalTrailundesignated" -> f.getCulturalTrailundesignated();
      case "burialSite" -> f.getBurialSite();
      case "nest" -> f.getNest();
      case "ceremonialSite" -> f.getCeremonialSite();
      case "cremationSite" -> f.getCremationSite();
      case "ofCMTs" -> f.getOfCMTs();
      case "caveorotherKarst" -> f.getCaveorotherKarst();
      case "den" -> f.getDen();
      case "traditionalUseSite" -> f.getTraditionalUseSite();
      case "cedarBarkStripArea" -> f.getCedarBarkStripArea();
      case "rockOutcrop" -> f.getRockOutcrop();
      case "spiritualSite" -> f.getSpiritualSite();
      case "ofMonumentalCedars" -> f.getOfMonumentalCedars();
      case "culturalDepression" -> f.getCulturalDepression();
      case "lithics" -> f.getLithics();
      case "other" -> f.getOther();
      case "pre1846" -> f.getPre1846();
      case "post1846" -> f.getPost1846();
      case "ageUnknown" -> f.getAgeUnknown();
      case "historicalUse" -> f.getHistoricalUse();
      case "harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getHarvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getSafetyQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getSilvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getRecreationQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "fireQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getFireQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getIndustrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "roadQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getRoadQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getLivestockQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getWindthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "otherQ2Wheredamagehasoccurredwhatisthemostlikelycause" -> f.getOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause();
      case "partiallytemporaryreserve" -> f.getPartiallytemporaryreserve();
      case "fullyconservedintemporaryreserve" -> f.getFullyconservedintemporaryreserve();
      case "partiallyconservedinpermanentreserve" -> f.getPartiallyconservedinpermanentreserve();
      case "fullyconservedinpermanentreserve" -> f.getFullyconservedinpermanentreserve();
      case "modifiedblockboundary" -> f.getModifiedblockboundary();
      case "retainabuffer" -> f.getRetainabuffer();
      case "compledCrownorstandmodification" -> f.getCompledCrownorstandmodification();
      case "datedthefeature" -> f.getDatedthefeature();
      case "retainedinharvestareanobuffer" -> f.getRetainedinharvestareanobuffer();
      case "leftStanding" -> f.getLeftStanding();
      case "stubbed" -> f.getStubbed();
      case "alteredsilviculture" -> f.getAlteredsilviculture();
      case "otherActivities" -> f.getOtherActivities();
      case "windthrowTechniqueRetentionBuffer" -> f.getWindthrowTechniqueRetentionBuffer();
      case "windthrowTechniquePruning" -> f.getWindthrowTechniquePruning();
      case "windthrowTechniqueFeathering" -> f.getWindthrowTechniqueFeathering();
      case "windthrowTechniqueTopping" -> f.getWindthrowTechniqueTopping();
      case "otherTechnique" -> f.getOtherTechnique();
      case "modifyBlockBoundaryFN" -> f.getModifyBlockBoundaryFN();
      case "retainBufferFN" -> f.getRetainBufferFN();
      case "retaininHarvestAreaFN" -> f.getRetaininHarvestAreaFN();
      case "crownorstandmodificationFN" -> f.getCrownorstandmodificationFN();
      case "conserveinRotationalReserveFN" -> f.getConserveinRotationalReserveFN();
      case "permanentReserveFN" -> f.getPermanentReserveFN();
      case "datetheFeatureFN" -> f.getDatetheFeatureFN();
      case "stubCMTsabovescarFN" -> f.getStubCMTsabovescarFN();
      case "stubnonCMTsFN" -> f.getStubnonCMTsFN();
      case "leaveStandingFN" -> f.getLeaveStandingFN();
      case "machineFreeZoneFN" -> f.getMachineFreeZoneFN();
      case "harvestUnderSapFN" -> f.getHarvestUnderSapFN();
      case "winterHarvestFrozenGroundFN" -> f.getWinterHarvestFrozenGroundFN();
      case "avoidSilvAvoidPlantingFN" -> f.getAvoidSilvAvoidPlantingFN();
      case "avoidSilvAvoidSitePrepFN" -> f.getAvoidSilvAvoidSitePrepFN();
      case "modifyBlockBoundarySP" -> f.getModifyBlockBoundarySP();
      case "retainBufferSP" -> f.getRetainBufferSP();
      case "retaininHarvestAreaSP" -> f.getRetaininHarvestAreaSP();
      case "crownorstandmodificationSP" -> f.getCrownorstandmodificationSP();
      case "conserveinRotationalReserveSP" -> f.getConserveinRotationalReserveSP();
      case "permanentReserveSP" -> f.getPermanentReserveSP();
      case "datetheFeatureSP" -> f.getDatetheFeatureSP();
      case "stubCMTsabovescarSP" -> f.getStubCMTsabovescarSP();
      case "stubnonCMTsSP" -> f.getStubnonCMTsSP();
      case "leaveStandingSP" -> f.getLeaveStandingSP();
      case "machineFreeZoneSP" -> f.getMachineFreeZoneSP();
      case "harvestUnderSapSP" -> f.getHarvestUnderSapSP();
      case "winterHarvestFrozenGroundSP" -> f.getWinterHarvestFrozenGroundSP();
      case "avoidSilvAvoidPlantingSP" -> f.getAvoidSilvAvoidPlantingSP();
      case "avoidSilvAvoidSitePrepSP" -> f.getAvoidSilvAvoidSitePrepSP();
      default -> null;
    };
  }

  private void req(List<ValidationError> errors, String ref, String field, String value, String msg) {
    if (!ChrStringUtils.hasAValue(value)) {
      errors.add(err(ref, field, msg));
    }
  }

  /** If {@code condition} is true, {@code value} must be present. */
  private void conditional(List<ValidationError> errors, String ref, String field,
      String condition, String value, String msg) {
    if (isTrue(condition) && !ChrStringUtils.hasAValue(value)) {
      errors.add(err(ref, field, msg));
    }
  }

  /** If a planning strategy is selected, its detail field must be present. */
  private void planningDetail(List<ValidationError> errors, String ref, String selected,
      String detail, String field, String msg) {
    if (isTrue(selected) && !ChrStringUtils.hasAValue(detail)) {
      errors.add(err(ref, field, msg));
    }
  }

  private void integer(List<ValidationError> errors, String ref, String field, String value, String msg) {
    if (ChrStringUtils.hasAValue(value)) {
      try {
        Integer.parseInt(value.trim());
      } catch (NumberFormatException ex) {
        errors.add(err(ref, field, msg + " (" + value + ")"));
      }
    }
  }

  private static String ref(CheckList c, Feature f) {
    return c.getChecklistID() + "-" + f.getFeatureLabel();
  }

  private static ValidationError err(String referenceId, String field, String message) {
    return new ValidationError(TYPE, message, referenceId, field);
  }
}
