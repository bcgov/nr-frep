package ca.bc.gov.nrs.frep.service;

import ca.bc.gov.nrs.frep.dto.MasterListYearResponse;
import ca.bc.gov.nrs.frep.dto.OrgUnitResponse;
import ca.bc.gov.nrs.frep.dto.ProtocolResponse;
import java.util.List;

/**
 * Reference / lookup data used to populate the UI filter dropdowns.
 *
 * <p>Legacy equivalents:
 * <ul>
 *   <li>master list years — {@code FREP_CODE_LISTS.GET_MASTERLIST_YEAR_CODE}</li>
 *   <li>org units — {@code SELECT * FROM ORG_UNIT WHERE org_level_code = 'D'}</li>
 *   <li>protocols — {@code resource_value_type} domain</li>
 * </ul>
 */
public interface ConfigurationService {

  List<MasterListYearResponse> getMasterListYears();

  List<OrgUnitResponse> getOrgUnits();

  List<ProtocolResponse> getProtocols();
}
