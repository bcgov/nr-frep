package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_SITE_EVALUATION_CODE", schema = "THE")
public class ChrSiteEvaluationCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_SITE_EVALUATION_CODE")
	private String chrSiteEvaluationCode;
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
	@OneToMany(targetEntity = ChrChecklist.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_SITE_EVALUATION_CODE", insertable = false, updatable = false)
	private Set chrChecklists = new HashSet(0);
	@OneToMany(targetEntity = ChrFeatureDetail.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_SITE_EVALUATION_CODE", insertable = false, updatable = false)
	private Set chrFeatureDetails = new HashSet(0);

	public ChrSiteEvaluationCode() {
	}

	public ChrSiteEvaluationCode(String frepSiteEvaluationCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.chrSiteEvaluationCode = frepSiteEvaluationCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrSiteEvaluationCode(String frepSiteEvaluationCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set chrChecklists, Set chrFeatureDetails) {
		this.chrSiteEvaluationCode = frepSiteEvaluationCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrChecklists = chrChecklists;
		this.chrFeatureDetails = chrFeatureDetails;
	}

	public String getChrSiteEvaluationCode() {
		return chrSiteEvaluationCode;
	}

	public void setChrSiteEvaluationCode(String chrSiteEvaluationCode) {
		this.chrSiteEvaluationCode = chrSiteEvaluationCode;
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

	public Set getChrChecklists() {
		return this.chrChecklists;
	}

	public void setChrChecklists(Set chrChecklists) {
		this.chrChecklists = chrChecklists;
	}

	public Set getChrFeatureDetails() {
		return this.chrFeatureDetails;
	}

	public void setChrFeatureDetails(Set chrFeatureDetails) {
		this.chrFeatureDetails = chrFeatureDetails;
	}

}
