package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

@Embeddable
public class ChrFeatureLocationDetailId implements java.io.Serializable {

	@Column(name = "CHR_FEATURE_ID")
	private long chrFeatureId;

	@Column(name = "CHR_FEATURE_LOCN_CONTEXT_CODE")
	private String chrFeatureLocnContextCode;

	public ChrFeatureLocationDetailId() {
	}

	public ChrFeatureLocationDetailId(long chrFeatureId, String chrFeatureLocnContextCode) {
		this.chrFeatureId = chrFeatureId;
		this.chrFeatureLocnContextCode = chrFeatureLocnContextCode;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getChrFeatureLocnContextCode() {
		return this.chrFeatureLocnContextCode;
	}

	public void setChrFeatureLocnContextCode(String chrFeatureLocnContextCode) {
		this.chrFeatureLocnContextCode = chrFeatureLocnContextCode;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrFeatureLocationDetailId))
			return false;
		ChrFeatureLocationDetailId castOther = (ChrFeatureLocationDetailId) other;

		return (this.getChrFeatureId() == castOther.getChrFeatureId()) && ((this
				.getChrFeatureLocnContextCode() == castOther.getChrFeatureLocnContextCode())
				|| (this.getChrFeatureLocnContextCode() != null && castOther.getChrFeatureLocnContextCode() != null
						&& this.getChrFeatureLocnContextCode().equals(castOther.getChrFeatureLocnContextCode())));
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrFeatureId();
		result = 37 * result
				+ (getChrFeatureLocnContextCode() == null ? 0 : this.getChrFeatureLocnContextCode().hashCode());
		return result;
	}

}
