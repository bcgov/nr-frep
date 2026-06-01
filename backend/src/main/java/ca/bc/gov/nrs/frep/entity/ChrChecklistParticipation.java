package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_CHECKLIST_PARTICIPATION", schema = "THE")
public class ChrChecklistParticipation implements java.io.Serializable {

	@EmbeddedId
	private ChrChecklistParticipationId id;

	@Column(name = "CHR_PARTICIPANT_ROLE_CODE")
	private String chrParticipantRoleCode;

	@Column(name = "CONTACTED_IND")
	private String contactedInd;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "CONTACTED_DATE")
	private Date contactedDate;

	@Column(name = "ATTENDING_ON_SITE_IND")
	private String attendingOnSiteInd;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "CHR_CHECKLIST_PARTICIPANT_ID", insertable = false, updatable = false)
	private ChrChecklistParticipant chrChecklistParticipant;

	@Column(name = "ENTRY_USERID")
	private String entryUserid;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "ENTRY_TIMESTAMP")
	private Date entryTimestamp;

	@Column(name = "UPDATE_USERID")
	private String updateUserid;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "UPDATE_TIMESTAMP")
	private Date updateTimestamp;

	public ChrChecklistParticipation() {
	}

	public ChrChecklistParticipation(ChrChecklistParticipationId id, String contactedInd, String attendingOnSiteInd,
			String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp) {
		this.id = id;
		this.contactedInd = contactedInd;
		this.attendingOnSiteInd = attendingOnSiteInd;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrChecklistParticipation(ChrChecklistParticipationId id, String chrParticipantRoleCode, String contactedInd,
			Date contactedDate, String attendingOnSiteInd, String entryUserid, Date entryTimestamp, String updateUserid,
			Date updateTimestamp) {
		this.id = id;
		this.chrParticipantRoleCode = chrParticipantRoleCode;
		this.contactedInd = contactedInd;
		this.contactedDate = contactedDate;
		this.attendingOnSiteInd = attendingOnSiteInd;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrChecklistParticipationId getId() {
		return this.id;
	}

	public void setId(ChrChecklistParticipationId id) {
		this.id = id;
	}

	public String getChrParticipantRoleCode() {
		return this.chrParticipantRoleCode;
	}

	public void setChrParticipantRoleCode(String chrParticipantRoleCode) {
		this.chrParticipantRoleCode = chrParticipantRoleCode;
	}

	public String getContactedInd() {
		return this.contactedInd;
	}

	public void setContactedInd(String contactedInd) {
		this.contactedInd = contactedInd;
	}

	public Date getContactedDate() {
		return this.contactedDate;
	}

	public void setContactedDate(Date contactedDate) {
		this.contactedDate = contactedDate;
	}

	public String getAttendingOnSiteInd() {
		return this.attendingOnSiteInd;
	}

	public void setAttendingOnSiteInd(String attendingOnSiteInd) {
		this.attendingOnSiteInd = attendingOnSiteInd;
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

	public ChrChecklistParticipant getChrChecklistParticipant() {
		return chrChecklistParticipant;
	}

	public void setChrChecklistParticipant(ChrChecklistParticipant chrChecklistParticipant) {
		this.chrChecklistParticipant = chrChecklistParticipant;
	}

}
