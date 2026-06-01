package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_FEATURE_CLASS_CODE", schema = "THE")
public class ChrFeatureClassCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_FEATURE_CLASS_CODE")
	private String chrFeatureClassCode;
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
	@OneToMany(targetEntity = ChrFeatureIdentity.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_CLASS_CODE", insertable = false, updatable = false)
	private Set chrFeatureIdentities = new HashSet(0);

	public ChrFeatureClassCode() {
	}

	public ChrFeatureClassCode(String chrFeatureClassCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp) {
		this.chrFeatureClassCode = chrFeatureClassCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatureClassCode(String chrFeatureClassCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp, Set chrFeatureIdentities) {
		this.chrFeatureClassCode = chrFeatureClassCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrFeatureIdentities = chrFeatureIdentities;
	}

	public String getChrFeatureClassCode() {
		return this.chrFeatureClassCode;
	}

	public void setChrFeatureClassCode(String chrFeatureClassCode) {
		this.chrFeatureClassCode = chrFeatureClassCode;
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

	public Set getChrFeatureIdentities() {
		return this.chrFeatureIdentities;
	}

	public void setChrFeatureIdentities(Set chrFeatureIdentities) {
		this.chrFeatureIdentities = chrFeatureIdentities;
	}

}
