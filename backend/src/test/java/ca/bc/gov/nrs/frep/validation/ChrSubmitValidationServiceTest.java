package ca.bc.gov.nrs.frep.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.dto.frep.CheckList;
import ca.bc.gov.nrs.frep.dto.frep.Feature;
import ca.bc.gov.nrs.frep.dto.frep.OtherPlannedManagementStrategy;
import ca.bc.gov.nrs.frep.dto.frep.ValidationError;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChrSubmitValidationServiceTest {

  private final ChrSubmitValidationService service = new ChrSubmitValidationService();

  private static Feature validFeature() {
    Feature feature = new Feature();
    feature.setFeatureLabel("1");
    feature.setCompositeFeatureInd("false");
    feature.setFeatureDescriptionCode("CMT");
    feature.setFeatureInfoSourceCode("SP");
    feature.setBurialSite("true"); // at least one feature type
    feature.setPre1846("true"); // at least one age
    feature.setFeatureRating("W");
    return feature;
  }

  private static CheckList validChecklist() {
    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setEvaluationDate("2026-05-01");
    checklist.setYearOfHarvest("2025");
    checklist.setGeneralLocation("Near the creek");
    checklist.setAssessedBy("IDIR\\tester");
    checklist.setRating("W");
    checklist.setFeatures(new java.util.ArrayList<>(List.of(validFeature())));
    return checklist;
  }

  private static boolean hasError(List<ValidationError> errors, String field) {
    return errors.stream().anyMatch(e -> field.equals(e.getField()));
  }

  @Test
  void passesForACompleteChecklist() {
    assertTrue(service.validateBeforeSubmit(validChecklist()).isEmpty());
  }

  @Test
  void requiresEvaluationDateRatingAndAtLeastOneFeature() {
    CheckList checklist = new CheckList();
    checklist.setChecklistID("1001");
    checklist.setFeatures(new java.util.ArrayList<>());

    List<ValidationError> errors = service.validateBeforeSubmit(checklist);

    assertTrue(hasError(errors, "evaluationDate"));
    assertTrue(hasError(errors, "rating"));
    assertTrue(hasError(errors, "features"));
  }

  @Test
  void requiresFeatureLabel() {
    CheckList checklist = validChecklist();
    checklist.getFeatures().get(0).setFeatureLabel("");

    assertTrue(hasError(service.validateBeforeSubmit(checklist), "featureLabel"));
  }

  @Test
  void nonCompositeFeatureRequiresDescriptionCodeAndInfoSource() {
    CheckList checklist = validChecklist();
    Feature feature = checklist.getFeatures().get(0);
    feature.setFeatureDescriptionCode("");
    feature.setFeatureInfoSourceCode("");

    List<ValidationError> errors = service.validateBeforeSubmit(checklist);

    assertTrue(hasError(errors, "featureDescriptionCode"));
    assertTrue(hasError(errors, "featureInfoSourceCode"));
  }

  @Test
  void compositeFeatureIsExemptFromDescriptionAndInfoSource() {
    CheckList checklist = validChecklist();
    Feature feature = checklist.getFeatures().get(0);
    feature.setCompositeFeatureInd("true");
    feature.setFeatureDescriptionCode("");
    feature.setFeatureInfoSourceCode("");

    List<ValidationError> errors = service.validateBeforeSubmit(checklist);

    assertFalse(hasError(errors, "featureDescriptionCode"));
    assertFalse(hasError(errors, "featureInfoSourceCode"));
  }

  @Test
  void nullChecklistProducesSingleError() {
    List<ValidationError> errors = service.validateBeforeSubmit(null);
    assertEquals(1, errors.size());
  }

  @Test
  void blockQuestionTogglesRequireComments() {
    CheckList checklist = validChecklist();
    checklist.setQ8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock("true");
    checklist.setQ8Comments("");

    assertTrue(hasError(service.validateBeforeSubmit(checklist), "q8Comments"));
  }

  @Test
  void featureRequiresAtLeastOneTypeAndAge() {
    CheckList checklist = validChecklist();
    Feature feature = checklist.getFeatures().get(0);
    feature.setBurialSite("false");
    feature.setPre1846("false");

    List<ValidationError> errors = service.validateBeforeSubmit(checklist);

    assertTrue(hasError(errors, "siteFeatureDescription"));
    assertTrue(hasError(errors, "age"));
  }

  @Test
  void selectedBufferStrategyRequiresBufferLength() {
    CheckList checklist = validChecklist();
    Feature feature = checklist.getFeatures().get(0);
    feature.setManagementStrategyFN("true");
    feature.setRetainBufferFN("true");
    feature.setBufferLengthFN("");

    assertTrue(hasError(service.validateBeforeSubmit(checklist), "bufferLengthFN"));
  }

  @Test
  void compositeFeatureNeedsAtLeastTwoMembers() {
    CheckList checklist = validChecklist();
    checklist.getFeatures().get(0).setCompositeFeatureInd("true");

    assertTrue(hasError(service.validateBeforeSubmit(checklist), "composite"));
  }

  @Test
  void otherPlannedStrategyDescriptionsMustBeUnique() {
    CheckList checklist = validChecklist();
    Feature feature = checklist.getFeatures().get(0);
    feature.setManagementStrategyFN("true");
    feature.setModifyBlockBoundaryFN("true"); // satisfies "at least one FN strategy"
    feature.getOtherPlannedManagementStrategy()
        .add(new OtherPlannedManagementStrategy("dup", "true", "false", "false"));
    feature.getOtherPlannedManagementStrategy()
        .add(new OtherPlannedManagementStrategy("dup", "true", "false", "false"));

    assertTrue(hasError(service.validateBeforeSubmit(checklist), "otherPlanningStrategy"));
  }
}
