package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.AcceptedSiteRow;
import java.util.List;

/**
 * Contract for legacy package {@code FREP_200_ACCEPTED_SITES} (FREP200 Accepted Sites). Implemented
 * by {@link ca.bc.gov.nrs.frep.repository.v1.impl.AcceptedSitesRepositoryImpl}. Mirrors the nr-fspts
 * {@code dao/v1} interface + {@code dao/v1/impl} split.
 */
public interface AcceptedSitesRepository {

  /** Loads accepted/targeted sites for a district and master-list year. */
  List<AcceptedSiteRow> findAcceptedSites(String orgUnitNo, String effectiveYear);

  /** Supplementary Cultural Heritage (CHR) accepted/targeted sites for a district and year. */
  List<AcceptedSiteRow> findCulturalHeritageSites(String orgUnitNo, String effectiveYear);
}
