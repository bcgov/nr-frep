package ca.bc.gov.nrs.frep.mapper;

import ca.bc.gov.nrs.frep.entity.FrepEvaluationYear;
import ca.bc.gov.nrs.frep.entity.FrepResourceValue;
import ca.bc.gov.nrs.frep.ChrConstants;

public abstract class FrepMapper {

	protected static String formatMasterList(FrepEvaluationYear evaluationYear) {
		return new Short(evaluationYear.getEffectiveYear()).toString() + "/" +
				new Integer(evaluationYear.getEffectiveYear() + new Short("1")).toString();
	}

	protected static String isResourceTargeted (FrepResourceValue frepResource) {
		if (ChrConstants.FrepResourceValueStatusCode.TAR.equalsIgnoreCase(
				frepResource.getFrepResourceValueStatCode().getFrepResourceValueStatCode())) {
			return "true";
		} else {
			return "false";
		}
	}
}
