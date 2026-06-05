package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_FEATURE_AGE_XREF", schema = "THE")
public class ChrFeatureAgeXref implements java.io.Serializable {

	@EmbeddedId
	private ChrFeatureAgeXrefId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private ChrFeatureDetail chrFeatureDetail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_AGE_CODE", insertable = false, updatable = false)
	private ChrFeatureAgeCode chrFeatureAgeCode;

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

	public ChrFeatureAgeXref() {
	}

	public ChrFeatureAgeXref(ChrFeatureAgeXrefId id, ChrFeatureDetail chrFeatureDetail,
			ChrFeatureAgeCode chrFeatureAgeCode, String entryUserid, Date entryTimestamp, String updateUserid,
			Date updateTimestamp) {
		this.id = id;
		this.chrFeatureDetail = chrFeatureDetail;
		this.chrFeatureAgeCode = chrFeatureAgeCode;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatureAgeXrefId getId() {
		return this.id;
	}

	public void setId(ChrFeatureAgeXrefId id) {
		this.id = id;
	}

	public ChrFeatureDetail getChrFeatureDetail() {
		return this.chrFeatureDetail;
	}

	public void setChrFeatureDetail(ChrFeatureDetail chrFeatureDetail) {
		this.chrFeatureDetail = chrFeatureDetail;
	}

	public ChrFeatureAgeCode getChrFeatureAgeCode() {
		return this.chrFeatureAgeCode;
	}

	public void setChrFeatureAgeCode(ChrFeatureAgeCode chrFeatureAgeCode) {
		this.chrFeatureAgeCode = chrFeatureAgeCode;
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
