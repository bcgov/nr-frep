package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_FEAT_WINDTHR_TREAT_XREF", schema = "THE")
public class ChrFeatWindthrTreatXref implements java.io.Serializable {

	@EmbeddedId
	private ChrFeatWindthrTreatXrefId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private ChrFeatureDetail chrFeatureDetail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_WINDTHROW_TREATMENT_CODE", insertable = false, updatable = false)
	private ChrWindthrowTreatmentCode chrWindthrowTreatmentCode;

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

	public ChrFeatWindthrTreatXref() {
	}

	public ChrFeatWindthrTreatXref(ChrFeatWindthrTreatXrefId id, ChrFeatureDetail chrFeatureDetail,
			ChrWindthrowTreatmentCode chrWindthrowTreatmentCode, String otherDescription, String entryUserid, Date entryTimestamp,
			String updateUserid, Date updateTimestamp) {
		this.id = id;
		this.chrFeatureDetail = chrFeatureDetail;
		this.chrWindthrowTreatmentCode = chrWindthrowTreatmentCode;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatWindthrTreatXrefId getId() {
		return this.id;
	}

	public void setId(ChrFeatWindthrTreatXrefId id) {
		this.id = id;
	}

	public ChrFeatureDetail getChrFeatureDetail() {
		return this.chrFeatureDetail;
	}

	public void setChrFeatureDetail(ChrFeatureDetail chrFeatureDetail) {
		this.chrFeatureDetail = chrFeatureDetail;
	}

	public ChrWindthrowTreatmentCode getChrWindthrowTreatmentCode() {
		return this.chrWindthrowTreatmentCode;
	}

	public void setChrWindthrowTreatmentCode(ChrWindthrowTreatmentCode chrWindthrowTreatmentCode) {
		this.chrWindthrowTreatmentCode = chrWindthrowTreatmentCode;
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

	public String getOtherDescription() {
		return otherDescription;
	}

	public void setOtherDescription(String otherDescription) {
		this.otherDescription = otherDescription;
	}
}
