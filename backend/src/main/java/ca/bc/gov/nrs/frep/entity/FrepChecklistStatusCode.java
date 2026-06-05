package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "FREP_CHECKLIST_STATUS_CODE", schema = "THE")
public class FrepChecklistStatusCode implements java.io.Serializable {

	@Id
	@Column(name = "FREP_CHECKLIST_STATUS_CODE")
	private String frepChecklistStatusCode;
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
	@JoinColumn(name = "FREP_CHECKLIST_STATUS_CODE", insertable = false, updatable = false)
	private Set chrChecklists = new HashSet(0);

	public FrepChecklistStatusCode() {
	}

	public FrepChecklistStatusCode(String frepChecklistStatusCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.frepChecklistStatusCode = frepChecklistStatusCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public FrepChecklistStatusCode(String frepChecklistStatusCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set chrChecklists) {
		this.frepChecklistStatusCode = frepChecklistStatusCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrChecklists = chrChecklists;
	}

	public String getFrepChecklistStatusCode() {
		return this.frepChecklistStatusCode;
	}

	public void setFrepChecklistStatusCode(String frepChecklistStatusCode) {
		this.frepChecklistStatusCode = frepChecklistStatusCode;
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

}
