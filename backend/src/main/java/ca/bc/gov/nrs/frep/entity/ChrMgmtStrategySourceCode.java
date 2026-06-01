package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_MGMT_STRATEGY_SOURCE_CODE", schema = "THE")
public class ChrMgmtStrategySourceCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_MGMT_STRATEGY_SOURCE_CODE")
	private String chrMgmtStrategySourceCode;
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
	@JoinColumn(name = "CHR_MGMT_STRATEGY_SOURCE_CODE", insertable = false, updatable = false)
	private Set chrMgmtStrategyPlanneds = new HashSet(0);

	public ChrMgmtStrategySourceCode() {
	}

	public ChrMgmtStrategySourceCode(String chrMgmtStrategySourceCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.chrMgmtStrategySourceCode = chrMgmtStrategySourceCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrMgmtStrategySourceCode(String chrMgmtStrategySourceCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set chrMgmtStrategyPlanneds) {
		this.chrMgmtStrategySourceCode = chrMgmtStrategySourceCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrMgmtStrategyPlanneds = chrMgmtStrategyPlanneds;
	}

	public String getChrMgmtStrategySourceCode() {
		return this.chrMgmtStrategySourceCode;
	}

	public void setChrMgmtStrategySourceCode(String chrMgmtStrategySourceCode) {
		this.chrMgmtStrategySourceCode = chrMgmtStrategySourceCode;
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

}
