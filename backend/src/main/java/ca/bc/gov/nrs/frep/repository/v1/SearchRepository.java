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
}
