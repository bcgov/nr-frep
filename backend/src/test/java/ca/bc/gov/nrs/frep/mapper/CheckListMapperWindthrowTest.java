package ca.bc.gov.nrs.frep.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.entity.ChrFeatWindthrTreatXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureDetail;
import ca.bc.gov.nrs.frep.entity.ChrFeatureIdentity;
import ca.bc.gov.nrs.frep.entity.ChrWindthrowTreatmentCode;
import ca.bc.gov.nrs.frep.entity.FrepChecklistAnswerCode;
import ca.bc.gov.nrs.frep.entity.FrepChecklistStatusCode;
import ca.bc.gov.nrs.frep.entity.FrepResourceValue;
import ca.bc.gov.nrs.frep.entity.FrepResourceValueStatCode;
import ca.bc.gov.nrs.frep.entity.FrepSelectedSite;
import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSite;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The "Other technique" description survives a load.
 *
 * <p>It did not: the windthrow xref was mapped with {@code feature.getIfotherpleasedescribe()} —
 * the half-built struct's own value, empty at that point — instead of the row's
 * {@code OTHER_DESCRIPTION}. The text saved correctly and was wiped on every read, so an evaluator
 * typed it, saved, and watched it disappear. Legacy carries the same line, which is where it came
 * from. The sibling mappings for damage agents and used strategies pass the row's value and were
 * always right.
 */
class CheckListMapperWindthrowTest {

  private static ChrChecklist checklistWithWindthrowOther(String description) {
    ChrFeatureDetail detail = new ChrFeatureDetail();
    detail.setDamageIrreversibleAnswerCd(answer());
    detail.setChrFeatWindthrTreatXrefs(Set.of(otherTechnique(description)));

    ChrFeatureIdentity identity = new ChrFeatureIdentity();
    identity.setChrFeatureId(1L);
    identity.setFeatureLabel("1");
    identity.setChrFeatureDetail(detail);

    ChrChecklist checklist = new ChrChecklist();
    checklist.setChrChecklistId(1001L);
    checklist.setRevisionCount(0L);
    checklist.setUpdateTimestamp(new Date());
    checklist.setChrFeatureIdentities(new HashSet<>(Set.of(identity)));
    // The header mappings dereference this chain before reaching the features.
    FrepResourceValue resourceValue = new FrepResourceValue();
    resourceValue.setFrepSelectedSite(new FrepSelectedSite());
    FrepResourceValueStatCode valueStatus = new FrepResourceValueStatCode();
    valueStatus.setFrepResourceValueStatCode("ACC");
    resourceValue.setFrepResourceValueStatCode(valueStatus);
    checklist.setFrepResourceValue(resourceValue);
    FrepChecklistStatusCode status = new FrepChecklistStatusCode();
    status.setFrepChecklistStatusCode("DRAFT");
    checklist.setFrepChecklistStatusCode(status);
    return checklist;
  }

  private static ChrFeatWindthrTreatXref otherTechnique(String description) {
    ChrWindthrowTreatmentCode code = new ChrWindthrowTreatmentCode();
    code.setChrWindthrowTreatmentCode(ChrConstants.ChrWindthrowTreatmentCode.OTHER);
    ChrFeatWindthrTreatXref xref = new ChrFeatWindthrTreatXref();
    xref.setChrWindthrowTreatmentCode(code);
    xref.setOtherDescription(description);
    return xref;
  }

  private static FrepChecklistAnswerCode answer() {
    FrepChecklistAnswerCode answer = new FrepChecklistAnswerCode();
    answer.setFrepChecklistAnswerCode("N");
    return answer;
  }

  @Test
  void otherTechniqueDescriptionSurvivesTheRead() throws Exception {
    CheckList mapped = CheckListMapper.getChecklist(
        checklistWithWindthrowOther("Fenced the windthrow edge"),
        new AcceptedSite(), "IDIR\\tester", null, null, null, null, null, null);

    var feature = mapped.getFeatures().get(0);
    assertEquals("true", feature.getOtherTechnique());
    assertEquals("Fenced the windthrow edge", feature.getIfotherpleasedescribe());
  }
}
