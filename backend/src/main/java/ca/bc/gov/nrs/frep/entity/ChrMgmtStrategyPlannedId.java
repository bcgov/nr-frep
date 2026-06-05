package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class ChrMgmtStrategyPlannedId implements java.io.Serializable {

	private long chrFeatureId;
	private String chrMgmtStrategySourceCode;
	private String chrMgmtStrategyTypeCode;

	public ChrMgmtStrategyPlannedId() {
	}

	public ChrMgmtStrategyPlannedId(long chrFeatureId, String chrMgmtStrategySourceCode,
			String chrMgmtStrategyTypeCode) {
		this.chrFeatureId = chrFeatureId;
		this.chrMgmtStrategySourceCode = chrMgmtStrategySourceCode;
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getChrMgmtStrategySourceCode() {
		return this.chrMgmtStrategySourceCode;
	}

	public void setChrMgmtStrategySourceCode(String chrMgmtStrategySourceCode) {
		this.chrMgmtStrategySourceCode = chrMgmtStrategySourceCode;
	}

	public String getChrMgmtStrategyTypeCode() {
		return this.chrMgmtStrategyTypeCode;
	}

	public void setChrMgmtStrategyTypeCode(String chrMgmtStrategyTypeCode) {
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrMgmtStrategyPlannedId))
			return false;
		ChrMgmtStrategyPlannedId castOther = (ChrMgmtStrategyPlannedId) other;

		return (this.getChrFeatureId() == castOther.getChrFeatureId())
				&& ((this.getChrMgmtStrategySourceCode() == castOther.getChrMgmtStrategySourceCode())
						|| (this.getChrMgmtStrategySourceCode() != null
								&& castOther.getChrMgmtStrategySourceCode() != null
								&& this.getChrMgmtStrategySourceCode()
										.equals(castOther.getChrMgmtStrategySourceCode())))
				&& ((this.getChrMgmtStrategyTypeCode() == castOther.getChrMgmtStrategyTypeCode())
						|| (this.getChrMgmtStrategyTypeCode() != null && castOther.getChrMgmtStrategyTypeCode() != null
								&& this.getChrMgmtStrategyTypeCode().equals(castOther.getChrMgmtStrategyTypeCode())));
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrFeatureId();
		result = 37 * result
				+ (getChrMgmtStrategySourceCode() == null ? 0 : this.getChrMgmtStrategySourceCode().hashCode());
		result = 37 * result
				+ (getChrMgmtStrategyTypeCode() == null ? 0 : this.getChrMgmtStrategyTypeCode().hashCode());
		return result;
	}

}
