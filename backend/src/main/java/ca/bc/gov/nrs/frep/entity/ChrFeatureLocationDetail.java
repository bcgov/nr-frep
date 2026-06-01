package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_FEATURE_LOCATION_DETAIL", schema = "THE")
public class ChrFeatureLocationDetail implements java.io.Serializable {

	@EmbeddedId
	private ChrFeatureLocationDetailId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private ChrFeatureDetail chrFeatureDetail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_RESERVE_TYPE_CODE")
	private ChrReserveTypeCode chrReserveTypeCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_LOCN_CONTEXT_CODE", insertable = false, updatable = false)
	private ChrFeatureLocnContextCode chrFeatureLocnContextCode;

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

	public ChrFeatureLocationDetail() {
	}

	public ChrFeatureLocationDetail(ChrFeatureLocationDetailId id, ChrFeatureDetail chrFeatureDetail,
			ChrFeatureLocnContextCode chrFeatureLocnContextCode, String otherDescription, String entryUserid,
			Date entryTimestamp, String updateUserid, Date updateTimestamp, long revisionCount) {
		this.id = id;
		this.chrFeatureDetail = chrFeatureDetail;
		this.chrFeatureLocnContextCode = chrFeatureLocnContextCode;
		this.otherDescription = otherDescription;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public ChrFeatureLocationDetail(ChrFeatureLocationDetailId id, ChrFeatureDetail chrFeatureDetail,
			ChrReserveTypeCode chrReserveTypeCode, ChrFeatureLocnContextCode chrFeatureLocnContextCode,
			String otherDescription, String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp,
			long revisionCount) {
		this.id = id;
		this.chrFeatureDetail = chrFeatureDetail;
		this.chrReserveTypeCode = chrReserveTypeCode;
		this.chrFeatureLocnContextCode = chrFeatureLocnContextCode;
		this.otherDescription = otherDescription;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public ChrFeatureLocationDetailId getId() {
		return this.id;
	}

	public void setId(ChrFeatureLocationDetailId id) {
		this.id = id;
	}

	public ChrFeatureDetail getChrFeatureDetail() {
		return this.chrFeatureDetail;
	}

	public void setChrFeatureDetail(ChrFeatureDetail chrFeatureDetail) {
		this.chrFeatureDetail = chrFeatureDetail;
	}

	public ChrReserveTypeCode getChrReserveTypeCode() {
		return this.chrReserveTypeCode;
	}

	public void setChrReserveTypeCode(ChrReserveTypeCode chrReserveTypeCode) {
		this.chrReserveTypeCode = chrReserveTypeCode;
	}

	public ChrFeatureLocnContextCode getChrFeatureLocnContextCode() {
		return this.chrFeatureLocnContextCode;
	}

	public void setChrFeatureLocnContextCode(ChrFeatureLocnContextCode chrFeatureLocnContextCode) {
		this.chrFeatureLocnContextCode = chrFeatureLocnContextCode;
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
