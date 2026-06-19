package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.struct.v1.frep.BecRow;
import java.util.List;
import java.util.Map;

/** Contract for legacy code-list / reference lookups ({@code FREP_CODE_LISTS} + BEC search). */
public interface CodeListRepository {
  List<Map<String, Object>> getDistrictOrgUnitCode();
  List<Map<String, Object>> getMasterListYearCode();
  List<Map<String, Object>> getResourceValue();
  List<Map<String, Object>> getSiteResourceReasonCode();
  List<Map<String, Object>> getStreamClassCode();
  List<Map<String, Object>> getSiteAccessCode();
  List<Map<String, Object>> getEvaluationCode();
  List<Map<String, Object>> getStratumTypeCode();
  List<Map<String, Object>> getResourceValueStatusCode();
  List<Map<String, Object>> getChecklistStatusCode();
  List<Map<String, Object>> getFrepSpeciesCode();
  List<Map<String, Object>> getWildlifeTreeDecayCode();
  List<Map<String, Object>> getCwdDecayClassCode();
  List<Map<String, Object>> getEvaluatorCode(String checklistId, String resourceType);
  List<Map<String, Object>> getChecklistAnswerCode(String excludeAnswerCode);
  List<BecRow> searchBec(String zone, String subzone, String variant, String phase,
      String siteSeries, String siteSeriesPhase, String seral);
}
