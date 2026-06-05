package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "FREP_RESOURCE_VALUE", schema = "THE")
public class FrepResourceValue implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "frepResourceValueIdSeq")
	@SequenceGenerator(name = "frepResourceValueIdSeq", sequenceName = "THE.FREP_RESOURCE_VALUE_SEQ", allocationSize = 1)
	@Column(name = "FREP_RESOURCE_VALUE_ID")
	private Long frepResourceValueId;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "FREP_SELECTED_SITE_ID")
	private FrepSelectedSite frepSelectedSite;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "FREP_RESOURCE_VALUE_TYPE_CODE")
	private FrepResourceValueTypeCode frepResourceValueTypeCode;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "FREP_RESOURCE_VALUE_STAT_CODE")
	private FrepResourceValueStatCode frepResourceValueStatCode;
	@Column(name = "REVISION_COUNT")
	private int revisionCount;
	@Column(name = "ENTRY_USERID")
	private String entryUserid;
	@Column(name = "UPDATE_TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date updateTimestamp;
	@Column(name = "FREP_SITE_RESOURCE_REASON_CODE")
	private String frepSiteResourceReasonCode;
	@Column(name = "ADDITIONAL_COMMENTS")
	private String additionalComments;
	@Column(name = "ENTRY_TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date entryTimestamp;
	@Column(name = "UPDATE_USERID")
	private String updateUserid;
	@Column(name = "REJECTION_REASON")
	private String rejectionReason;
	@OneToMany(targetEntity = ChrChecklist.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "FREP_RESOURCE_VALUE_ID", insertable = false, updatable = false)
	private Set chrChecklists = new HashSet(0);

	public FrepResourceValue() {
	}

	public FrepResourceValue(FrepSelectedSite frepSelectedSite, FrepResourceValueTypeCode frepResourceValueTypeCode,
			FrepResourceValueStatCode frepResourceValueStatCode, int revisionCount, String entryUserid,
			Date updateTimestamp, Date entryTimestamp, String updateUserid) {
		this.frepSelectedSite = frepSelectedSite;
		this.frepResourceValueTypeCode = frepResourceValueTypeCode;
		this.frepResourceValueStatCode = frepResourceValueStatCode;
		this.revisionCount = revisionCount;
		this.entryUserid = entryUserid;
		this.updateTimestamp = updateTimestamp;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
	}

	public FrepResourceValue(FrepSelectedSite frepSelectedSite, FrepResourceValueTypeCode frepResourceValueTypeCode,
			FrepResourceValueStatCode frepResourceValueStatCode, int revisionCount, String entryUserid,
			Date updateTimestamp, String frepSiteResourceReasonCode, String additionalComments, Date entryTimestamp,
			String updateUserid, String rejectionReason, Set chrChecklists) {
		this.frepSelectedSite = frepSelectedSite;
		this.frepResourceValueTypeCode = frepResourceValueTypeCode;
		this.frepResourceValueStatCode = frepResourceValueStatCode;
		this.revisionCount = revisionCount;
		this.entryUserid = entryUserid;
		this.updateTimestamp = updateTimestamp;
		this.frepSiteResourceReasonCode = frepSiteResourceReasonCode;
		this.additionalComments = additionalComments;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.rejectionReason = rejectionReason;
		this.chrChecklists = chrChecklists;
	}

	public Long getFrepResourceValueId() {
		return this.frepResourceValueId;
	}

	public void setFrepResourceValueId(Long frepResourceValueId) {
		this.frepResourceValueId = frepResourceValueId;
	}

	public FrepSelectedSite getFrepSelectedSite() {
		return this.frepSelectedSite;
	}

	public void setFrepSelectedSite(FrepSelectedSite frepSelectedSite) {
		this.frepSelectedSite = frepSelectedSite;
	}

	public FrepResourceValueTypeCode getFrepResourceValueTypeCode() {
		return this.frepResourceValueTypeCode;
	}

	public void setFrepResourceValueTypeCode(FrepResourceValueTypeCode frepResourceValueTypeCode) {
		this.frepResourceValueTypeCode = frepResourceValueTypeCode;
	}

	public FrepResourceValueStatCode getFrepResourceValueStatCode() {
		return this.frepResourceValueStatCode;
	}

	public void setFrepResourceValueStatCode(FrepResourceValueStatCode frepResourceValueStatCode) {
		this.frepResourceValueStatCode = frepResourceValueStatCode;
	}

	public int getRevisionCount() {
		return this.revisionCount;
	}

	public void setRevisionCount(int revisionCount) {
		this.revisionCount = revisionCount;
	}

	public String getEntryUserid() {
		return this.entryUserid;
	}

	public void setEntryUserid(String entryUserid) {
		this.entryUserid = entryUserid;
	}

	public Date getUpdateTimestamp() {
		return this.updateTimestamp;
	}

	public void setUpdateTimestamp(Date updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	public String getFrepSiteResourceReasonCode() {
		return this.frepSiteResourceReasonCode;
	}

	public void setFrepSiteResourceReasonCode(String frepSiteResourceReasonCode) {
		this.frepSiteResourceReasonCode = frepSiteResourceReasonCode;
	}

	public String getAdditionalComments() {
		return this.additionalComments;
	}

	public void setAdditionalComments(String additionalComments) {
		this.additionalComments = additionalComments;
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

	public String getRejectionReason() {
		return this.rejectionReason;
	}

	public void setRejectionReason(String rejectionReason) {
		this.rejectionReason = rejectionReason;
	}

	public Set getChrChecklists() {
		return this.chrChecklists;
	}

	public void setChrChecklists(Set chrChecklists) {
		this.chrChecklists = chrChecklists;
	}

}
