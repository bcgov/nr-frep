package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

@Embeddable
public class ChrFeatureInfoSourceXrefId implements java.io.Serializable {

	@Column(name = "CHR_FEATURE_ID")
	private long chrFeatureId;

	@Column(name = "CHR_FEATURE_INFO_SOURCE_CODE")
	private String chrFeatureInfoSourceCode;

	public ChrFeatureInfoSourceXrefId() {
	}

	public ChrFeatureInfoSourceXrefId(long chrFeatureId, String chrFeatureInfoSourceCode) {
		this.chrFeatureId = chrFeatureId;
		this.chrFeatureInfoSourceCode = chrFeatureInfoSourceCode;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getChrFeatureInfoSourceCode() {
		return this.chrFeatureInfoSourceCode;
	}

	public void setChrFeatureInfoSourceCode(String chrFeatureInfoSourceCode) {
		this.chrFeatureInfoSourceCode = chrFeatureInfoSourceCode;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrFeatureInfoSourceXrefId))
			return false;
		ChrFeatureInfoSourceXrefId castOther = (ChrFeatureInfoSourceXrefId) other;

		return (this.getChrFeatureId() == castOther.getChrFeatureId())
				&& ((this.getChrFeatureInfoSourceCode() == castOther.getChrFeatureInfoSourceCode())
						|| (this.getChrFeatureInfoSourceCode() != null
								&& castOther.getChrFeatureInfoSourceCode() != null
								&& this.getChrFeatureInfoSourceCode().equals(castOther.getChrFeatureInfoSourceCode())));
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrFeatureId();
		result = 37 * result
				+ (getChrFeatureInfoSourceCode() == null ? 0 : this.getChrFeatureInfoSourceCode().hashCode());
		return result;
	}

}
