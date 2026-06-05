package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "CHR_PARTICIPANT_ROLE_CODE", schema = "THE")
public class ChrParticipantRoleCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_PARTICIPANT_ROLE_CODE")
	private String chrParticipantRoleCode;
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

	public ChrParticipantRoleCode() {
	}

	public ChrParticipantRoleCode(String chrParticipantRoleCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.chrParticipantRoleCode = chrParticipantRoleCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public String getChrParticipantRoleCode() {
		return this.chrParticipantRoleCode;
	}

	public void setChrParticipantRoleCode(String chrParticipantRoleCode) {
		this.chrParticipantRoleCode = chrParticipantRoleCode;
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
