package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "FREP_EVALUATION_YEAR", schema = "THE")
public class FrepEvaluationYear implements java.io.Serializable {

	@Id
	@Column(name = "EFFECTIVE_YEAR")
	private short effectiveYear;
	@Column(name = "FREP_EVAL_YEAR_STATUS_CODE")
	private String frepEvalYearStatusCode;
	@Column(name = "MAX_HARVEST_COMPLETE_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date maxHarvestCompleteDate;
	@Column(name = "MIN_HARVEST_COMPLETE_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date minHarvestCompleteDate;
	@Column(name = "MIN_GROSS_AREA")
	private BigDecimal minGrossArea;
	@Column(name = "MAX_SITES_PER_DISTRICT")
	private Short maxSitesPerDistrict;
	@Column(name = "GENERATION_COMMENTS")
	private String generationComments;
	@Column(name = "REVISION_COUNT")
	private int revisionCount;
	@Column(name = "ENTRY_USERID")
	private String entryUserid;
	@Column(name = "ENTRY_TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date entryTimestamp;
	@Column(name = "UPDATE_USERID")
	private String updateUserid;
	@Column(name = "UPDATE_TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date updateTimestamp;
	@OneToMany(targetEntity = FrepSelectedSite.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "EFFECTIVE_YEAR", insertable = false, updatable = false)
	private Set frepSelectedSites = new HashSet(0);

	public FrepEvaluationYear() {
	}

	public FrepEvaluationYear(short effectiveYear, String frepEvalYearStatusCode, int revisionCount, String entryUserid,
			Date entryTimestamp, String updateUserid, Date updateTimestamp) {
		this.effectiveYear = effectiveYear;
		this.frepEvalYearStatusCode = frepEvalYearStatusCode;
		this.revisionCount = revisionCount;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
	}

	public FrepEvaluationYear(short effectiveYear, String frepEvalYearStatusCode, Date maxHarvestCompleteDate,
			Date minHarvestCompleteDate, BigDecimal minGrossArea, Short maxSitesPerDistrict, String generationComments,
			int revisionCount, String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp,
			Set frepSelectedSites) {
		this.effectiveYear = effectiveYear;
		this.frepEvalYearStatusCode = frepEvalYearStatusCode;
		this.maxHarvestCompleteDate = maxHarvestCompleteDate;
		this.minHarvestCompleteDate = minHarvestCompleteDate;
		this.minGrossArea = minGrossArea;
		this.maxSitesPerDistrict = maxSitesPerDistrict;
		this.generationComments = generationComments;
		this.revisionCount = revisionCount;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.frepSelectedSites = frepSelectedSites;
	}

	public short getEffectiveYear() {
		return this.effectiveYear;
	}

	public void setEffectiveYear(short effectiveYear) {
		this.effectiveYear = effectiveYear;
	}

	public String getFrepEvalYearStatusCode() {
		return this.frepEvalYearStatusCode;
	}

	public void setFrepEvalYearStatusCode(String frepEvalYearStatusCode) {
		this.frepEvalYearStatusCode = frepEvalYearStatusCode;
	}

	public Date getMaxHarvestCompleteDate() {
		return this.maxHarvestCompleteDate;
	}

	public void setMaxHarvestCompleteDate(Date maxHarvestCompleteDate) {
		this.maxHarvestCompleteDate = maxHarvestCompleteDate;
	}

	public Date getMinHarvestCompleteDate() {
		return this.minHarvestCompleteDate;
	}

	public void setMinHarvestCompleteDate(Date minHarvestCompleteDate) {
		this.minHarvestCompleteDate = minHarvestCompleteDate;
	}

	public BigDecimal getMinGrossArea() {
		return this.minGrossArea;
	}

	public void setMinGrossArea(BigDecimal minGrossArea) {
		this.minGrossArea = minGrossArea;
	}

	public Short getMaxSitesPerDistrict() {
		return this.maxSitesPerDistrict;
	}

	public void setMaxSitesPerDistrict(Short maxSitesPerDistrict) {
		this.maxSitesPerDistrict = maxSitesPerDistrict;
	}

	public String getGenerationComments() {
		return this.generationComments;
	}

	public void setGenerationComments(String generationComments) {
		this.generationComments = generationComments;
	}

	public int getRevisionCount() {
		return this.revisionCount;
	}

	public void setRevisionCount(int revisionCount) {
		this.revisionCount = revisionCount;
	}

	public String getEntryUserid() {
		return this.entryUserid;
	}

	public void setEntryUserid(String entryUserid) {
		this.entryUserid = entryUserid;
	}

	public Date getEntryTimestamp() {
		return this.entryTimestamp;
	}

	public void setEntryTimestamp(Date entryTimestamp) {
		this.entryTimestamp = entryTimestamp;
	}

	public String getUpdateUserid() {
		return this.updateUserid;
	}

	public void setUpdateUserid(String updateUserid) {
		this.updateUserid = updateUserid;
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
