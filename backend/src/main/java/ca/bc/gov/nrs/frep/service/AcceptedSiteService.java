package ca.bc.gov.nrs.frep.service;

import ca.bc.gov.nrs.frep.dto.AcceptedSiteResponse;
import java.util.List;

//Test deploy
public interface AcceptedSiteService {

  List<AcceptedSiteResponse> findAcceptedSites(
      String effectiveYear,
      String orgUnit,
      String protocolType
  );
}
