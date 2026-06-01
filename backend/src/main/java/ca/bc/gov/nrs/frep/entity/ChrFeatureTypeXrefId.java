package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

@Embeddable
public class ChrFeatureTypeXrefId implements java.io.Serializable {

	@Column(name = "CHR_FEATURE_ID")
	private long chrFeatureId;

	@Column(name = "CHR_FEATURE_TYPE_CODE")
	private String chrFeatureTypeCode;

	public ChrFeatureTypeXrefId() {
	}

	public ChrFeatureTypeXrefId(long chrFeatureId, String chrFeatureTypeCode) {
		this.chrFeatureId = chrFeatureId;
		this.chrFeatureTypeCode = chrFeatureTypeCode;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getChrFeatureTypeCode() {
		return this.chrFeatureTypeCode;
	}

	public void setChrFeatureTypeCode(String chrFeatureTypeCode) {
		this.chrFeatureTypeCode = chrFeatureTypeCode;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrFeatureTypeXrefId))
			return false;
		ChrFeatureTypeXrefId castOther = (ChrFeatureTypeXrefId) other;

		return (this.getChrFeatureId() == castOther.getChrFeatureId())
				&& ((this.getChrFeatureTypeCode() == castOther.getChrFeatureTypeCode())
						|| (this.getChrFeatureTypeCode() != null && castOther.getChrFeatureTypeCode() != null
								&& this.getChrFeatureTypeCode().equals(castOther.getChrFeatureTypeCode())));
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrFeatureId();
		result = 37 * result + (getChrFeatureTypeCode() == null ? 0 : this.getChrFeatureTypeCode().hashCode());
		return result;
	}

}
