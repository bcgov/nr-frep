package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

@Embeddable
public class ChrFeatWindthrTreatXrefId implements java.io.Serializable {

	@Column(name = "CHR_FEATURE_ID")
	private long chrFeatureId;

	@Column(name = "CHR_WINDTHROW_TREATMENT_CODE")
	private String chrWindthrowTreatmentCode;

	public ChrFeatWindthrTreatXrefId() {
	}

	public ChrFeatWindthrTreatXrefId(long chrFeatureId, String chrWindthrowTreatmentCode) {
		this.chrFeatureId = chrFeatureId;
		this.chrWindthrowTreatmentCode = chrWindthrowTreatmentCode;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getChrWindthrowTreatmentCode() {
		return this.chrWindthrowTreatmentCode;
	}

	public void setChrWindthrowTreatmentCode(String chrWindthrowTreatmentCode) {
		this.chrWindthrowTreatmentCode = chrWindthrowTreatmentCode;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrFeatWindthrTreatXrefId))
			return false;
		ChrFeatWindthrTreatXrefId castOther = (ChrFeatWindthrTreatXrefId) other;

		return (this.getChrFeatureId() == castOther.getChrFeatureId()) && ((this
				.getChrWindthrowTreatmentCode() == castOther.getChrWindthrowTreatmentCode())
				|| (this.getChrWindthrowTreatmentCode() != null && castOther.getChrWindthrowTreatmentCode() != null
						&& this.getChrWindthrowTreatmentCode().equals(castOther.getChrWindthrowTreatmentCode())));
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrFeatureId();
		result = 37 * result
				+ (getChrWindthrowTreatmentCode() == null ? 0 : this.getChrWindthrowTreatmentCode().hashCode());
		return result;
	}

}
