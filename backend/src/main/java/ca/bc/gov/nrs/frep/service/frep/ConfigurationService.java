package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.dto.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolResponse;
import java.util.List;

/**
 * Reference / lookup data used to populate the UI filter dropdowns.
 *
 * <p>Legacy equivalents:
 * <ul>
 *   <li>master list years — {@code FREP_CODE_LISTS.GET_MASTERLIST_YEAR_CODE}</li>
 *   <li>org units — {@code FREP_CODE_LISTS.GET_DISTRICT_ORG_UNIT_CODE}</li>
 *   <li>protocols — {@code resource_value_type} domain</li>
 * </ul>
 */
public interface ConfigurationService {

  List<MasterListYearResponse> getMasterListYears();

  List<OrgUnitResponse> getOrgUnits();

  List<ProtocolResponse> getProtocols();
}
