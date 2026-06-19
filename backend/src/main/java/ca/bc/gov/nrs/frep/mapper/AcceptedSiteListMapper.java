package ca.bc.gov.nrs.frep.mapper;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSite;
import ca.bc.gov.nrs.frep.util.ChrDateUtils;

public final class AcceptedSiteListMapper extends FrepMapper{


	public static List<AcceptedSite> getAcceptedSiteList(List<ChrChecklist> dbList) {

		List<AcceptedSite> resourceList = new ArrayList<AcceptedSite>();
		for (ChrChecklist checklist : dbList) {
			resourceList.add(getAcceptedSite(checklist));
		}
		return resourceList;
	}

	public static AcceptedSite getAcceptedSite(ChrChecklist checklist) {

		AcceptedSite resource = new AcceptedSite();
		resource.setChecklistID(new Long(checklist.getChrChecklistId()).toString());
		resource.setLicenseNumber(checklist.getFrepResourceValue().getFrepSelectedSite().getForestFileId());
		resource.setOpeningID(new Long(checklist.getFrepResourceValue().getFrepSelectedSite().getOpeningId()).toString());
		resource.setCutBlock(checklist.getFrepResourceValue().getFrepSelectedSite().getCutBlockId());
		resource.setCuttingPermit(checklist.getFrepResourceValue().getFrepSelectedSite().getCuttingPermitId());
		resource.setStatus(checklist.getFrepChecklistStatusCode().getDescription());
		resource.setStatusCode(checklist.getFrepChecklistStatusCode().getFrepChecklistStatusCode());
		resource.setTargeted(isResourceTargeted(checklist.getFrepResourceValue()));

		if(checklist.getEvaluationDate() != null) {
			try {
			resource.setEvaluationDate(ChrDateUtils.formatDate(checklist.getEvaluationDate()));
			} catch (ParseException e) {
				// do nothing
			}
		}
		resource.setProtocolCode(checklist.getFrepResourceValue().getFrepResourceValueTypeCode().getFrepResourceValueTypeCode());
		resource.setProtocolName(checklist.getFrepResourceValue().getFrepResourceValueTypeCode().getDescription());
		resource.setOrgUnitCode(checklist.getFrepResourceValue().getFrepSelectedSite().getOrgUnit().getOrgUnitCode());
		resource.setOrgUnitName(checklist.getFrepResourceValue().getFrepSelectedSite().getOrgUnit().getOrgUnitName());
		resource.setOrgUnitNo(new Long(checklist.getFrepResourceValue().getFrepSelectedSite().getOrgUnit().getOrgUnitNo()).toString());

		resource.setEffectiveYear(new Short(checklist.getFrepResourceValue().getFrepSelectedSite().getFrepEvaluationYear().getEffectiveYear()).toString());
		resource.setMasterList(formatMasterList(checklist.getFrepResourceValue().getFrepSelectedSite().getFrepEvaluationYear()));

		return resource;
	}
}
