package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class ChrMgmtStrategyUsedId implements java.io.Serializable {

	private long chrFeatureId;
	private String chrMgmtStrategyTypeCode;
	private String fullyConservedInd;

	public ChrMgmtStrategyUsedId() {
	}

	public ChrMgmtStrategyUsedId(long chrFeatureId, String chrMgmtStrategyTypeCode, String fullyConservedInd) {
		this.chrFeatureId = chrFeatureId;
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
		this.fullyConservedInd = fullyConservedInd;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getChrMgmtStrategyTypeCode() {
		return this.chrMgmtStrategyTypeCode;
	}

	public void setChrMgmtStrategyTypeCode(String chrMgmtStrategyTypeCode) {
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
	}

	public String getFullyConservedInd() {
		return fullyConservedInd;
	}

	public void setFullyConservedInd(String fullyConservedInd) {
		this.fullyConservedInd = fullyConservedInd;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrMgmtStrategyUsedId))
			return false;
		ChrMgmtStrategyUsedId castOther = (ChrMgmtStrategyUsedId) other;

		return (this.getChrFeatureId() == castOther.getChrFeatureId())
				&& ((this.getChrMgmtStrategyTypeCode() == castOther.getChrMgmtStrategyTypeCode())
						|| (this.getChrMgmtStrategyTypeCode() != null && castOther.getChrMgmtStrategyTypeCode() != null
								&& this.getChrMgmtStrategyTypeCode().equals(castOther.getChrMgmtStrategyTypeCode())))
				&& ((this.getFullyConservedInd() == castOther.getFullyConservedInd())
						|| (this.getFullyConservedInd() != null && castOther.getFullyConservedInd() != null
								&& this.getFullyConservedInd().equals(castOther.getFullyConservedInd())))
				;
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrFeatureId();
		result = 37 * result
				+ (getChrMgmtStrategyTypeCode() == null ? 0 : this.getChrMgmtStrategyTypeCode().hashCode())
				+ (getFullyConservedInd() == null ? 0 : this.getFullyConservedInd().hashCode());
		return result;
	}

}
