package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_FEATURE_INFO_SOURCE_CODE", schema = "THE")
public class ChrFeatureInfoSourceCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_FEATURE_INFO_SOURCE_CODE")
	private String chrFeatureInfoSourceCode;
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
	@OneToMany(targetEntity = ChrFeatureInfoSourceXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_INFO_SOURCE_CODE", insertable = false, updatable = false)
	private Set chrFeatureInfoSourceXrefs = new HashSet(0);

	public ChrFeatureInfoSourceCode() {
	}

	public ChrFeatureInfoSourceCode(String chrFeatureInfoSourceCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.chrFeatureInfoSourceCode = chrFeatureInfoSourceCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatureInfoSourceCode(String chrFeatureInfoSourceCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set chrFeatureInfoSourceXrefs) {
		this.chrFeatureInfoSourceCode = chrFeatureInfoSourceCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrFeatureInfoSourceXrefs = chrFeatureInfoSourceXrefs;
	}

	public String getChrFeatureInfoSourceCode() {
		return this.chrFeatureInfoSourceCode;
	}

	public void setChrFeatureInfoSourceCode(String chrFeatureInfoSourceCode) {
		this.chrFeatureInfoSourceCode = chrFeatureInfoSourceCode;
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

	public Set getChrFeatureInfoSourceXrefs() {
		return this.chrFeatureInfoSourceXrefs;
	}

	public void setChrFeatureInfoSourceXrefs(Set chrFeatureInfoSourceXrefs) {
		this.chrFeatureInfoSourceXrefs = chrFeatureInfoSourceXrefs;
	}

}
