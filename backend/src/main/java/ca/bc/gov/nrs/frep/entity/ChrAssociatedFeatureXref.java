package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_ASSOCIATED_FEATURE_XREF", schema = "THE")
public class ChrAssociatedFeatureXref implements java.io.Serializable {

	@EmbeddedId
	private ChrAssociatedFeatureXrefId id;
	@Transient
	private ChrFeatureIdentity chrFeatureIdentityByFromChrFeatureId;
	@Transient
	private ChrFeatureIdentity chrFeatureIdentityByToChrFeatureId;
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

	public ChrAssociatedFeatureXref() {
	}

	public ChrAssociatedFeatureXref(ChrAssociatedFeatureXrefId id,
			ChrFeatureIdentity chrFeatureIdentityByFromChrFeatureId,
			ChrFeatureIdentity chrFeatureIdentityByToChrFeatureId, String entryUserid, Date entryTimestamp,
			String updateUserid, Date updateTimestamp) {
		this.id = id;
		this.chrFeatureIdentityByFromChrFeatureId = chrFeatureIdentityByFromChrFeatureId;
		this.chrFeatureIdentityByToChrFeatureId = chrFeatureIdentityByToChrFeatureId;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrAssociatedFeatureXrefId getId() {
		return this.id;
	}

	public void setId(ChrAssociatedFeatureXrefId id) {
		this.id = id;
	}

	public ChrFeatureIdentity getChrFeatureIdentityByFromChrFeatureId() {
		return this.chrFeatureIdentityByFromChrFeatureId;
	}

	public void setChrFeatureIdentityByFromChrFeatureId(ChrFeatureIdentity chrFeatureIdentityByFromChrFeatureId) {
		this.chrFeatureIdentityByFromChrFeatureId = chrFeatureIdentityByFromChrFeatureId;
	}

	public ChrFeatureIdentity getChrFeatureIdentityByToChrFeatureId() {
		return this.chrFeatureIdentityByToChrFeatureId;
	}

	public void setChrFeatureIdentityByToChrFeatureId(ChrFeatureIdentity chrFeatureIdentityByToChrFeatureId) {
		this.chrFeatureIdentityByToChrFeatureId = chrFeatureIdentityByToChrFeatureId;
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

}
