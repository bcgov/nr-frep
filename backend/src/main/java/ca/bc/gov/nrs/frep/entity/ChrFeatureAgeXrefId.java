package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

@Embeddable
public class ChrFeatureAgeXrefId implements java.io.Serializable {

	@Column(name = "CHR_FEATURE_ID")
	private long chrFeatureId;

	@Column(name = "CHR_FEATURE_AGE_CODE")
	private String chrFeatureAgeCode;

	public ChrFeatureAgeXrefId() {
	}

	public ChrFeatureAgeXrefId(long chrFeatureId, String chrFeatureAgeCode) {
		this.chrFeatureId = chrFeatureId;
		this.chrFeatureAgeCode = chrFeatureAgeCode;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getChrFeatureAgeCode() {
		return this.chrFeatureAgeCode;
	}

	public void setChrFeatureAgeCode(String chrFeatureAgeCode) {
		this.chrFeatureAgeCode = chrFeatureAgeCode;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrFeatureAgeXrefId))
			return false;
		ChrFeatureAgeXrefId castOther = (ChrFeatureAgeXrefId) other;

		return (this.getChrFeatureId() == castOther.getChrFeatureId())
				&& ((this.getChrFeatureAgeCode() == castOther.getChrFeatureAgeCode())
						|| (this.getChrFeatureAgeCode() != null && castOther.getChrFeatureAgeCode() != null
								&& this.getChrFeatureAgeCode().equals(castOther.getChrFeatureAgeCode())));
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrFeatureId();
		result = 37 * result + (getChrFeatureAgeCode() == null ? 0 : this.getChrFeatureAgeCode().hashCode());
		return result;
	}

}
