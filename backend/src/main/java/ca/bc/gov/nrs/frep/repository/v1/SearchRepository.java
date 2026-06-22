package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchRow;
import java.util.List;

/** Contract for FREP400 (checklist search) and FREP410 (client search). */
public interface SearchRepository {
  List<ChecklistSearchRow> searchChecklists(ChecklistSearchCriteria criteria);
  List<ClientSearchRow> searchClients(ClientSearchCriteria criteria);

  /**
   * Total checklists matching the criteria. Backs the page envelope's {@code totalElements}; runs the
   * native search query as {@code COUNT} over the distinct result set (uncapped, unlike the legacy
   * VARRAY proc).
   */
  long countChecklists(ChecklistSearchCriteria criteria);

  /**
   * One page of checklist-search rows. Native paginated query (Oracle {@code OFFSET/FETCH}) that
   * replicates {@code FREP_CHECKLIST_SEARCH.search} without the 5000-row VARRAY cap.
   *
   * @param orderByColumn pre-validated result-set alias to sort by (never raw client input)
   * @param descending    sort direction
   */
  List<ChecklistSearchRow> searchChecklistsPage(
      ChecklistSearchCriteria criteria, int offset, int pageSize, String orderByColumn, boolean descending);
}
