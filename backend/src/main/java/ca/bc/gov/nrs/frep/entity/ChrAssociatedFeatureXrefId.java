package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

@Embeddable
public class ChrAssociatedFeatureXrefId implements java.io.Serializable {

	@Column(name = "FROM_CHR_FEATURE_ID")
	private long fromChrFeatureId;
	@Column(name = "TO_CHR_FEATURE_ID")
	private long toChrFeatureId;

	public ChrAssociatedFeatureXrefId() {
	}

	public ChrAssociatedFeatureXrefId(long fromChrFeatureId, long toChrFeatureId) {
		this.fromChrFeatureId = fromChrFeatureId;
		this.toChrFeatureId = toChrFeatureId;
	}

	public long getFromChrFeatureId() {
		return this.fromChrFeatureId;
	}

	public void setFromChrFeatureId(long fromChrFeatureId) {
		this.fromChrFeatureId = fromChrFeatureId;
	}

	public long getToChrFeatureId() {
		return this.toChrFeatureId;
	}

	public void setToChrFeatureId(long toChrFeatureId) {
		this.toChrFeatureId = toChrFeatureId;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrAssociatedFeatureXrefId))
			return false;
		ChrAssociatedFeatureXrefId castOther = (ChrAssociatedFeatureXrefId) other;

		return (this.getFromChrFeatureId() == castOther.getFromChrFeatureId())
				&& (this.getToChrFeatureId() == castOther.getToChrFeatureId());
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getFromChrFeatureId();
		result = 37 * result + (int) this.getToChrFeatureId();
		return result;
	}

}
