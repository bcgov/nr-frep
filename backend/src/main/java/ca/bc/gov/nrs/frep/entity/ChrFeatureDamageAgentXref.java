package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_FEATURE_DAMAGE_AGENT_XREF", schema = "THE")
public class ChrFeatureDamageAgentXref implements java.io.Serializable {

	@EmbeddedId
	private ChrFeatureDamageAgentXrefId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_DAMAGE_AGENT_CODE", insertable = false, updatable = false)
	private ChrFeatureDamageAgentCode chrFeatureDamageAgentCode;

	@Column(name = "OTHER_DESCRIPTION")
	private String otherDescription;

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

	@Column(name = "REVISION_COUNT")
	private long revisionCount;

	public ChrFeatureDamageAgentXref() {
	}

	public ChrFeatureDamageAgentXref(ChrFeatureDamageAgentXrefId id,
			ChrFeatureDamageAgentCode chrFeatureDamageAgentCode, String entryUserid, Date entryTimestamp,
			String updateUserid, Date updateTimestamp, long revisionCount) {
		this.id = id;
		this.chrFeatureDamageAgentCode = chrFeatureDamageAgentCode;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public ChrFeatureDamageAgentXref(ChrFeatureDamageAgentXrefId id,
			ChrFeatureDamageAgentCode chrFeatureDamageAgentCode, String otherDescription, String entryUserid,
			Date entryTimestamp, String updateUserid, Date updateTimestamp, long revisionCount) {
		this.id = id;
		this.chrFeatureDamageAgentCode = chrFeatureDamageAgentCode;
		this.otherDescription = otherDescription;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public ChrFeatureDamageAgentXrefId getId() {
		return this.id;
	}

	public void setId(ChrFeatureDamageAgentXrefId id) {
		this.id = id;
	}

	public ChrFeatureDamageAgentCode getChrFeatureDamageAgentCode() {
		return this.chrFeatureDamageAgentCode;
	}

	public void setChrFeatureDamageAgentCode(ChrFeatureDamageAgentCode chrFeatureDamageAgentCode) {
		this.chrFeatureDamageAgentCode = chrFeatureDamageAgentCode;
	}

	public String getOtherDescription() {
		return this.otherDescription;
	}

	public void setOtherDescription(String otherDescription) {
		this.otherDescription = otherDescription;
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

	public long getRevisionCount() {
		return this.revisionCount;
	}

	public void setRevisionCount(long revisionCount) {
		this.revisionCount = revisionCount;
	}

}
