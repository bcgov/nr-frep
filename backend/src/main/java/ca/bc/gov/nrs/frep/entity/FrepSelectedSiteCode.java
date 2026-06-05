package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "FREP_SELECTED_SITE_CODE", schema = "THE")
public class FrepSelectedSiteCode implements java.io.Serializable {

	@Id
	@Column(name = "FREP_SELECTED_SITE_CODE")
	private String frepSelectedSiteCode;
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
	@OneToMany(targetEntity = FrepSelectedSite.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "FREP_SELECTED_SITE_CODE", insertable = false, updatable = false)
	private Set frepSelectedSites = new HashSet(0);

	public FrepSelectedSiteCode() {
	}

	public FrepSelectedSiteCode(String frepSelectedSiteCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp) {
		this.frepSelectedSiteCode = frepSelectedSiteCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public FrepSelectedSiteCode(String frepSelectedSiteCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp, Set frepSelectedSites) {
		this.frepSelectedSiteCode = frepSelectedSiteCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.frepSelectedSites = frepSelectedSites;
	}

	public String getFrepSelectedSiteCode() {
		return this.frepSelectedSiteCode;
	}

	public void setFrepSelectedSiteCode(String frepSelectedSiteCode) {
		this.frepSelectedSiteCode = frepSelectedSiteCode;
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

	public Set getFrepSelectedSites() {
		return this.frepSelectedSites;
	}
	public void setFrepSelectedSites(Set frepSelectedSites) {
		this.frepSelectedSites = frepSelectedSites;
	}

}
