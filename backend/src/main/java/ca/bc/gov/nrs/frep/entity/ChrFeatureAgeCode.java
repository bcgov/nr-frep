package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_FEATURE_AGE_CODE", schema = "THE")
public class ChrFeatureAgeCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_FEATURE_AGE_CODE")
	private String chrFeatureAgeCode;
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
	@OneToMany(targetEntity = ChrFeatureAgeXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_AGE_CODE", insertable = false, updatable = false)
	private Set chrFeatureAgeXrefs = new HashSet(0);

	public ChrFeatureAgeCode() {
	}

	public ChrFeatureAgeCode(String chrFeatureAgeCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp) {
		this.chrFeatureAgeCode = chrFeatureAgeCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatureAgeCode(String chrFeatureAgeCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp, Set chrFeatureAgeXrefs) {
		this.chrFeatureAgeCode = chrFeatureAgeCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrFeatureAgeXrefs = chrFeatureAgeXrefs;
	}

	public String getChrFeatureAgeCode() {
		return this.chrFeatureAgeCode;
	}

	public void setChrFeatureAgeCode(String chrFeatureAgeCode) {
		this.chrFeatureAgeCode = chrFeatureAgeCode;
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

	public Set getChrFeatureAgeXrefs() {
		return this.chrFeatureAgeXrefs;
	}

	public void setChrFeatureAgeXrefs(Set chrFeatureAgeXrefs) {
		this.chrFeatureAgeXrefs = chrFeatureAgeXrefs;
	}

}
