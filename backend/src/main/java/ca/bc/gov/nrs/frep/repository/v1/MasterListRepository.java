package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.MasterListCriteriaData;

/** Contract for FREP700 Master List generation ({@code FREP_700_GEN_MASTER}). */
public interface MasterListRepository {
  MasterListCriteriaData getCriteria(String effectiveYear);

  void generate(
      String effectiveYear,
      String maxHarvestCompleteDate,
      String minHarvestCompleteDate,
      String minOpeningGrossAreaHa,
      String maxSitesPerDistrict,
      String generationComments,
      String entryUserId);

  void saveComments(String effectiveYear, String generationComments, String userId);
  void deleteList(String effectiveYear);
}
