package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_CHECKLIST_ATTACHMENT", schema = "THE")
public class ChrChecklistAttachment implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chrchecklistAttachmentIdSeq")
	@SequenceGenerator(name = "chrchecklistAttachmentIdSeq", sequenceName = "THE.CHR_CHECKLIST_ATTACHMENT_SEQ", allocationSize = 1)
	@Column(name = "CHR_CHECKLIST_ATTACHMENT_ID")
	private Long chrchecklistAttachmentId;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "CHR_CHECKLIST_ID")
	private ChrChecklist chrChecklist;

	/**
	 * Optional FK to CHR_FEATURE_DETAIL — the feature this photo documents, if any
	 * (constraint CHCA_CHFD_FK). Mapped as the bare id rather than a {@code @ManyToOne}: only the
	 * association is carried, so a relation would buy an extra fetch and a lifecycle coupling to
	 * ChrFeatureDetail for nothing. The referenced feature must belong to the same checklist —
	 * enforced in ChrChecklistPersistenceService#addPhoto, since the FK alone permits any feature.
	 */
	@Column(name = "CHR_FEATURE_ID")
	private Long chrFeatureId;

	@Column(name = "MIME_TYPE_CODE")
	private String mimeTypeCode;

	@Column(name = "DESCRIPTION")
	private String description;

	@Column(name = "FILE_NAME")
	private String fileName;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "FILE_DATE")
	private Date fileDate;

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

	public ChrChecklistAttachment() {}

	public Long getChrchecklistAttachmentId() {
		return chrchecklistAttachmentId;
	}

	public void setChrchecklistAttachmentId(Long chrchecklistAttachmentId) {
		this.chrchecklistAttachmentId = chrchecklistAttachmentId;
	}

	public ChrChecklist getChrChecklist() {
		return chrChecklist;
	}

	public void setChrChecklist(ChrChecklist chrChecklist) {
		this.chrChecklist = chrChecklist;
	}

	public Long getChrFeatureId() {
		return chrFeatureId;
	}

	public void setChrFeatureId(Long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public String getMimeTypeCode() {
		return mimeTypeCode;
	}

	public void setMimeTypeCode(String mimeTypeCode) {
		this.mimeTypeCode = mimeTypeCode;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public Date getFileDate() {
		return fileDate;
	}

	public void setFileDate(Date fileDate) {
		this.fileDate = fileDate;
	}

	public String getEntryUserid() {
		return entryUserid;
	}

	public void setEntryUserid(String entryUserid) {
		this.entryUserid = entryUserid;
	}

	public Date getEntryTimestamp() {
		return entryTimestamp;
	}

	public void setEntryTimestamp(Date entryTimestamp) {
		this.entryTimestamp = entryTimestamp;
	}

	public String getUpdateUserid() {
		return updateUserid;
	}

	public void setUpdateUserid(String updateUserid) {
		this.updateUserid = updateUserid;
	}

	public Date getUpdateTimestamp() {
		return updateTimestamp;
	}

	public void setUpdateTimestamp(Date updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	public long getRevisionCount() {
		return revisionCount;
	}

	public void setRevisionCount(long revisionCount) {
		this.revisionCount = revisionCount;
	}



}
