package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_FEATURE_INFO_SOURCE_XREF", schema = "THE")
public class ChrFeatureInfoSourceXref implements java.io.Serializable {

	@EmbeddedId
	private ChrFeatureInfoSourceXrefId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_INFO_SOURCE_CODE", insertable = false, updatable = false)
	private ChrFeatureInfoSourceCode chrFeatureInfoSourceCode;

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

	public ChrFeatureInfoSourceXref() {
	}

	public ChrFeatureInfoSourceXref(ChrFeatureInfoSourceXrefId id, ChrFeatureInfoSourceCode chrFeatureInfoSourceCode,
			String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp) {
		this.id = id;
		this.chrFeatureInfoSourceCode = chrFeatureInfoSourceCode;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatureInfoSourceXrefId getId() {
		return this.id;
	}

	public void setId(ChrFeatureInfoSourceXrefId id) {
		this.id = id;
	}

	public ChrFeatureInfoSourceCode getChrFeatureInfoSourceCode() {
		return this.chrFeatureInfoSourceCode;
	}

	public void setChrFeatureInfoSourceCode(ChrFeatureInfoSourceCode chrFeatureInfoSourceCode) {
		this.chrFeatureInfoSourceCode = chrFeatureInfoSourceCode;
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
