package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_MGMT_STRATEGY_TYPE_CODE", schema = "THE")
public class ChrMgmtStrategyTypeCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_MGMT_STRATEGY_TYPE_CODE")
	private String chrMgmtStrategyTypeCode;
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
	@OneToMany(targetEntity = ChrMgmtStrategyPlanned.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_MGMT_STRATEGY_TYPE_CODE", insertable = false, updatable = false)
	private Set chrMgmtStrategyPlanneds = new HashSet(0);
	@OneToMany(targetEntity = ChrMgmtStrategyUsed.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_MGMT_STRATEGY_TYPE_CODE", insertable = false, updatable = false)
	private Set chrMgmtStrategyUseds = new HashSet(0);

	public ChrMgmtStrategyTypeCode() {
	}

	public ChrMgmtStrategyTypeCode(String chrMgmtStrategyTypeCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrMgmtStrategyTypeCode(String chrMgmtStrategyTypeCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set chrMgmtStrategyPlanneds,
			Set chrMgmtStrategyUseds) {
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrMgmtStrategyPlanneds = chrMgmtStrategyPlanneds;
		this.chrMgmtStrategyUseds = chrMgmtStrategyUseds;
	}

	public String getChrMgmtStrategyTypeCode() {
		return this.chrMgmtStrategyTypeCode;
	}

	public void setChrMgmtStrategyTypeCode(String chrMgmtStrategyTypeCode) {
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
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

	public Set getChrMgmtStrategyPlanneds() {
		return this.chrMgmtStrategyPlanneds;
	}

	public void setChrMgmtStrategyPlanneds(Set chrMgmtStrategyPlanneds) {
		this.chrMgmtStrategyPlanneds = chrMgmtStrategyPlanneds;
	}

	public Set getChrMgmtStrategyUseds() {
		return this.chrMgmtStrategyUseds;
	}

	public void setChrMgmtStrategyUseds(Set chrMgmtStrategyUseds) {
		this.chrMgmtStrategyUseds = chrMgmtStrategyUseds;
	}

}
