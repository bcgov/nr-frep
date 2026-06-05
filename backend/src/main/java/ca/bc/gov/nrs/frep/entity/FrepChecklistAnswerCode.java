package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "FREP_CHECKLIST_ANSWER_CODE", schema = "THE")
public class FrepChecklistAnswerCode implements java.io.Serializable {

	@Id
	@Column(name = "FREP_CHECKLIST_ANSWER_CODE")
	private String frepChecklistAnswerCode;
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


	public FrepChecklistAnswerCode() {}

	public FrepChecklistAnswerCode(String frepChecklistAnswerCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp) {
		this.frepChecklistAnswerCode = frepChecklistAnswerCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public String getFrepChecklistAnswerCode() {
		return this.frepChecklistAnswerCode;
	}

	public void setFrepChecklistAnswerCode(String frepChecklistAnswerCode) {
		this.frepChecklistAnswerCode = frepChecklistAnswerCode;
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

}
