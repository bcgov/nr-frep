package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_FEATURE_TYPE_CODE", schema = "THE")
public class ChrFeatureTypeCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_FEATURE_TYPE_CODE")
	private String chrFeatureTypeCode;
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
	@OneToMany(targetEntity = ChrFeatureTypeXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_TYPE_CODE", insertable = false, updatable = false)
	private Set chrFeatureTypeXrefs = new HashSet(0);

	public ChrFeatureTypeCode() {
	}

	public ChrFeatureTypeCode(String chrFeatureTypeCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp) {
		this.chrFeatureTypeCode = chrFeatureTypeCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatureTypeCode(String chrFeatureTypeCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp, Set chrFeatureTypeXrefs) {
		this.chrFeatureTypeCode = chrFeatureTypeCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrFeatureTypeXrefs = chrFeatureTypeXrefs;
	}

	public String getChrFeatureTypeCode() {
		return this.chrFeatureTypeCode;
	}

	public void setChrFeatureTypeCode(String chrFeatureTypeCode) {
		this.chrFeatureTypeCode = chrFeatureTypeCode;
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

	public Set getChrFeatureTypeXrefs() {
		return this.chrFeatureTypeXrefs;
	}

	public void setChrFeatureTypeXrefs(Set chrFeatureTypeXrefs) {
		this.chrFeatureTypeXrefs = chrFeatureTypeXrefs;
	}

}
