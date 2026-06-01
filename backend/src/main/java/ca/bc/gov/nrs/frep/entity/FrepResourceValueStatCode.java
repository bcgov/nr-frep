package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "FREP_RESOURCE_VALUE_STAT_CODE", schema = "THE")
public class FrepResourceValueStatCode implements java.io.Serializable {

	@Id
	@Column(name = "FREP_RESOURCE_VALUE_STAT_CODE")
	private String frepResourceValueStatCode;
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
	@OneToMany(targetEntity = FrepResourceValue.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "FREP_RESOURCE_VALUE_STAT_CODE", insertable = false, updatable = false)
	private Set frepResourceValues = new HashSet(0);

	public FrepResourceValueStatCode() {
	}

	public FrepResourceValueStatCode(String frepResourceValueStatCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.frepResourceValueStatCode = frepResourceValueStatCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public FrepResourceValueStatCode(String frepResourceValueStatCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set frepResourceValues) {
		this.frepResourceValueStatCode = frepResourceValueStatCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.frepResourceValues = frepResourceValues;
	}

	public String getFrepResourceValueStatCode() {
		return this.frepResourceValueStatCode;
	}

	public void setFrepResourceValueStatCode(String frepResourceValueStatCode) {
		this.frepResourceValueStatCode = frepResourceValueStatCode;
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

	public Set getFrepResourceValues() {
		return this.frepResourceValues;
	}

	public void setFrepResourceValues(Set frepResourceValues) {
		this.frepResourceValues = frepResourceValues;
	}

}
