package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_FEATURE_IDENTITY", schema = "THE")
public class ChrFeatureIdentity implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chrFeatureIdentitySeq")
	@SequenceGenerator(name = "chrFeatureIdentitySeq", sequenceName = "THE.CHR_FEATURE_IDENTITY_SEQ", allocationSize = 1)
	@Column(name = "CHR_FEATURE_ID")
	private Long chrFeatureId;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_CLASS_CODE")
	private ChrFeatureClassCode chrFeatureClassCode;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_CHECKLIST_ID")
	private ChrChecklist chrChecklist;
	@Column(name = "COMPOSITE_CHR_FEATURE_ID")
	private Long compositeChrFeatureIdentity;
	@Column(name = "FEATURE_LABEL")
	private String featureLabel;
	@Column(name = "COMPOSITE_FEATURE_IND")
	private String compositeFeatureInd;
	@Column(name = "COMMENTS")
	private String comments;
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
	@Column(name = "REVISION_COUNT")
	private long revisionCount;
	@OneToMany(targetEntity = ChrAssociatedFeatureXref.class, fetch = FetchType.EAGER)
	@JoinColumn(name = "FROM_CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrAssociatedFeatureXrefsForFromChrFeatureId = new HashSet(0);
	@OneToMany(targetEntity = ChrAssociatedFeatureXref.class, fetch = FetchType.EAGER)
	@JoinColumn(name = "TO_CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrAssociatedFeatureXrefsForToChrFeatureId = new HashSet(0);
	@OneToOne(mappedBy = "chrFeatureIdentity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private ChrFeatureDetail chrFeatureDetail;
	@OneToMany(targetEntity = ChrFeatureInfoSourceXref.class, fetch = FetchType.EAGER)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrFeatureInfoSourceXrefs = new HashSet(0);

	public ChrFeatureIdentity() {
	}

	public ChrFeatureIdentity(ChrChecklist chrChecklist, String featureLabel, String compositeFeatureInd,
			String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp, long revisionCount) {
		this.chrChecklist = chrChecklist;
		this.featureLabel = featureLabel;
		this.compositeFeatureInd = compositeFeatureInd;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public ChrFeatureIdentity(ChrFeatureClassCode chrFeatureClassCode, ChrChecklist chrChecklist,
			Long compositeChrFeatureIdentity, String featureLabel, String compositeFeatureInd, String comments,
			String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp, long revisionCount,
			Set chrAssociatedFeatureXrefsForFromChrFeatureId, Set chrAssociatedFeatureXrefsForToChrFeatureId,
			ChrFeatureDetail chrFeatureDetail) {
		this.chrFeatureClassCode = chrFeatureClassCode;
		this.chrChecklist = chrChecklist;
		this.compositeChrFeatureIdentity = compositeChrFeatureIdentity;
		this.featureLabel = featureLabel;
		this.compositeFeatureInd = compositeFeatureInd;
		this.comments = comments;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
		this.chrAssociatedFeatureXrefsForFromChrFeatureId = chrAssociatedFeatureXrefsForFromChrFeatureId;
		this.chrAssociatedFeatureXrefsForToChrFeatureId = chrAssociatedFeatureXrefsForToChrFeatureId;
		this.chrFeatureDetail = chrFeatureDetail;
	}

	public Long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(Long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public ChrFeatureClassCode getChrFeatureClassCode() {
		return this.chrFeatureClassCode;
	}

	public void setChrFeatureClassCode(ChrFeatureClassCode chrFeatureClassCode) {
		this.chrFeatureClassCode = chrFeatureClassCode;
	}

	public ChrChecklist getChrChecklist() {
		return this.chrChecklist;
	}

	public void setChrChecklist(ChrChecklist chrChecklist) {
		this.chrChecklist = chrChecklist;
	}

	public Long getCompositeChrFeatureIdentity() {
		return this.compositeChrFeatureIdentity;
	}

	public void setCompositeChrFeatureIdentity(Long compositeChrFeatureIdentity) {
		this.compositeChrFeatureIdentity = compositeChrFeatureIdentity;
	}

	public String getFeatureLabel() {
		return this.featureLabel;
	}

	public void setFeatureLabel(String featureLabel) {
		this.featureLabel = featureLabel;
	}

	public String getCompositeFeatureInd() {
		return this.compositeFeatureInd;
	}

	public void setCompositeFeatureInd(String compositeFeatureInd) {
		this.compositeFeatureInd = compositeFeatureInd;
	}

	public String getComments() {
		return this.comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
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

	public Set getChrAssociatedFeatureXrefsForFromChrFeatureId() {
		return this.chrAssociatedFeatureXrefsForFromChrFeatureId;
	}

	public void setChrAssociatedFeatureXrefsForFromChrFeatureId(Set chrAssociatedFeatureXrefsForFromChrFeatureId) {
		this.chrAssociatedFeatureXrefsForFromChrFeatureId = chrAssociatedFeatureXrefsForFromChrFeatureId;
	}

	public Set getChrAssociatedFeatureXrefsForToChrFeatureId() {
		return this.chrAssociatedFeatureXrefsForToChrFeatureId;
	}

	public void setChrAssociatedFeatureXrefsForToChrFeatureId(Set chrAssociatedFeatureXrefsForToChrFeatureId) {
		this.chrAssociatedFeatureXrefsForToChrFeatureId = chrAssociatedFeatureXrefsForToChrFeatureId;
	}

	public ChrFeatureDetail getChrFeatureDetail() {
		return this.chrFeatureDetail;
	}

	public void setChrFeatureDetail(ChrFeatureDetail chrFeatureDetail) {
		this.chrFeatureDetail = chrFeatureDetail;
	}

	public Set getChrFeatureInfoSourceXrefs() {
		return chrFeatureInfoSourceXrefs;
	}

	public void setChrFeatureInfoSourceXrefs(Set chrFeatureInfoSourceXrefs) {
		this.chrFeatureInfoSourceXrefs = chrFeatureInfoSourceXrefs;
	}

}
