package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_FEATURE_LOCN_CONTEXT_CODE", schema = "THE")
public class ChrFeatureLocnContextCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_FEATURE_LOCN_CONTEXT_CODE")
	private String chrFeatureLocnContextCode;
	@Column(name = "DESCRIPTION")
	private String description;
	@Column(name = "EFFECTIVE_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date effectiveDate;
	@Column(name = "EXPIRY_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date expiryDate;
	@Column(name = "UPDATE_TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date updateTimestamp;
	@OneToMany(targetEntity = ChrFeatureLocationDetail.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_LOCN_CONTEXT_CODE", insertable = false, updatable = false)
	private Set chrFeatureLocationDetails = new HashSet(0);

	public ChrFeatureLocnContextCode() {
	}

	public ChrFeatureLocnContextCode(String chrFeatureLocnContextCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.chrFeatureLocnContextCode = chrFeatureLocnContextCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatureLocnContextCode(String chrFeatureLocnContextCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set chrFeatureLocationDetails) {
		this.chrFeatureLocnContextCode = chrFeatureLocnContextCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrFeatureLocationDetails = chrFeatureLocationDetails;
	}

	public String getChrFeatureLocnContextCode() {
		return this.chrFeatureLocnContextCode;
	}

	public void setChrFeatureLocnContextCode(String chrFeatureLocnContextCode) {
		this.chrFeatureLocnContextCode = chrFeatureLocnContextCode;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Date getEffectiveDate() {
		return this.effectiveDate;
	}

	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public Date getExpiryDate() {
		return this.expiryDate;
	}

	public void setExpiryDate(Date expiryDate) {
		this.expiryDate = expiryDate;
	}

	public Date getUpdateTimestamp() {
		return this.updateTimestamp;
	}

	public void setUpdateTimestamp(Date updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	public Set getChrFeatureLocationDetails() {
		return this.chrFeatureLocationDetails;
	}

	public void setChrFeatureLocationDetails(Set chrFeatureLocationDetails) {
		this.chrFeatureLocationDetails = chrFeatureLocationDetails;
	}

}
