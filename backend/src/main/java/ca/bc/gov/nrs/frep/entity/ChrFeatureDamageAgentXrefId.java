package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

@Embeddable
public class ChrFeatureDamageAgentXrefId implements java.io.Serializable {

	@Column(name = "CHR_FEATURE_ID")
	private long chrFeatureId;

	@Column(name = "CHR_FEATURE_DAMAGE_AGENT_CODE")
	private String chrFeatureDamageAgentCode;

	public ChrFeatureDamageAgentXrefId() {
	}

	public ChrFeatureDamageAgentXrefId(long chrFeatureId, String chrFeatureDamageAgentCode) {
		this.chrFeatureId = chrFeatureId;
		this.chrFeatureDamageAgentCode = chrFeatureDamageAgentCode;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getChrFeatureDamageAgentCode() {
		return this.chrFeatureDamageAgentCode;
	}

	public void setChrFeatureDamageAgentCode(String chrFeatureDamageAgentCode) {
		this.chrFeatureDamageAgentCode = chrFeatureDamageAgentCode;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrFeatureDamageAgentXrefId))
			return false;
		ChrFeatureDamageAgentXrefId castOther = (ChrFeatureDamageAgentXrefId) other;

		return (this.getChrFeatureId() == castOther.getChrFeatureId()) && ((this
				.getChrFeatureDamageAgentCode() == castOther.getChrFeatureDamageAgentCode())
				|| (this.getChrFeatureDamageAgentCode() != null && castOther.getChrFeatureDamageAgentCode() != null
						&& this.getChrFeatureDamageAgentCode().equals(castOther.getChrFeatureDamageAgentCode())));
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrFeatureId();
		result = 37 * result
				+ (getChrFeatureDamageAgentCode() == null ? 0 : this.getChrFeatureDamageAgentCode().hashCode());
		return result;
	}

}
