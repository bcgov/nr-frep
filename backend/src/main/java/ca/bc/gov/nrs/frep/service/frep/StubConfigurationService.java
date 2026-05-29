package ca.bc.gov.nrs.frep.service.frep;

import ca.bc.gov.nrs.frep.dto.frep.MasterListYearResponse;
import ca.bc.gov.nrs.frep.dto.frep.OrgUnitResponse;
import ca.bc.gov.nrs.frep.dto.frep.ProtocolResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Hard-coded reference data for unit tests. At runtime,
 * {@link JdbcConfigurationService} is used instead.
 */
@Service
@ConditionalOnMissingBean(JdbcConfigurationService.class)
public class StubConfigurationService implements ConfigurationService {

  private static final List<MasterListYearResponse> MASTER_LIST_YEARS = List.of(
      new MasterListYearResponse("2024", "2024/2025", true),
      new MasterListYearResponse("2023", "2023/2024", false),
      new MasterListYearResponse("2022", "2022/2023", false)
  );

  private static final List<OrgUnitResponse> ORG_UNITS = List.of(
      new OrgUnitResponse("56", "DCK", "Chilliwack Forest District"),
      new OrgUnitResponse("58", "DKA", "Kamloops Forest District"),
      new OrgUnitResponse("61", "DNI", "Nadina Forest District"),
      new OrgUnitResponse("63", "DPC", "Prince George Forest District")
  );

  private static final List<ProtocolResponse> PROTOCOLS = List.of(
      new ProtocolResponse("BIO", "Biodiversity"),
      new ProtocolResponse("RIP", "Riparian"),
      new ProtocolResponse("WAT", "Water Quality"),
      new ProtocolResponse("CHR", "Culture Heritage")
  );

  @Override
  public List<MasterListYearResponse> getMasterListYears() {
    return MASTER_LIST_YEARS;
  }

  @Override
  public List<OrgUnitResponse> getOrgUnits() {
    return ORG_UNITS;
  }

  @Override
  public List<ProtocolResponse> getProtocols() {
    return PROTOCOLS;
  }
}
