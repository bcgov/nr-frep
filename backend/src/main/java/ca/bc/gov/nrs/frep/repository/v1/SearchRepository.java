package ca.bc.gov.nrs.frep.repository.v1;

import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ChecklistSearchRow;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchCriteria;
import ca.bc.gov.nrs.frep.repository.v1.bean.ClientSearchRow;
import java.util.List;
import java.util.function.Consumer;

/** Contract for FREP400 (checklist search) and FREP410 (client search). */
public interface SearchRepository {
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

  /**
   * Streams every matching checklist-search row to {@code consumer} in {@code orderByColumn} order, via a
   * server-side cursor (tuned fetch size) so heap stays constant regardless of result size — backs the
   * CSV export. Bounded by an internal max-rows safety cap (a warning is logged if it is reached).
   * Returns the number of rows streamed. Holds a DB connection for the whole stream, so callers must
   * bound concurrency (see {@link ca.bc.gov.nrs.frep.service.v1.report.ExportSlotLimiter}).
   *
   * @param orderByColumn pre-validated result-set alias to sort by (never raw client input)
   */
  long streamChecklists(
      ChecklistSearchCriteria criteria, String orderByColumn, boolean descending,
      Consumer<ChecklistSearchRow> consumer);
}
