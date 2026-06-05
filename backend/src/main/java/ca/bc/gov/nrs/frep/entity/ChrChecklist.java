package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_CHECKLIST", schema = "THE")
public class ChrChecklist implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chrChecklistIdSeq")
	@SequenceGenerator(name = "chrChecklistIdSeq", sequenceName = "THE.CHR_CHECKLIST_SEQ", allocationSize = 1)
	@Column(name = "CHR_CHECKLIST_ID")
	private Long chrChecklistId;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "FREP_RESOURCE_VALUE_ID")
	private FrepResourceValue frepResourceValue;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "FREP_CHECKLIST_STATUS_CODE")
	private FrepChecklistStatusCode frepChecklistStatusCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_SITE_EVALUATION_CODE")
	private ChrSiteEvaluationCode chrSiteEvaluationCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "FREP_MRVA_RATING_CODE")
	private FrepMrvaRatingCode frepMrvaRatingCode;

	@Column(name = "ASSESSED_BY")
	private String assessedBy;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "EVALUATION_DATE")
	private Date evaluationDate;

	@Column(name = "FIRST_NATIONS_PLACENAME")
	private String firstNationsPlacename;

	@Column(name = "LOCATION_DESCRIPTION")
	private String locationDescription;

	@Column(name = "BLOCK_COMMENTS")
	private String blockComments;

	@Column(name = "LIMITING_OPERATNL_FACTORS_IND")
	private String limitingOperatnlFactorsInd;

	@Column(name = "LIMITING_OPERATNL_FACTORS_DESC")
	private String limitingOperatnlFactorsDesc;

	@Column(name = "EFFECTIVE_STRATS_USED_IND")
	private String effectiveStratsUsedInd;

	@Column(name = "EFFECTIVE_STRATS_USED_DESC")
	private String effectiveStratsUsedDesc;

	@Column(name = "ALTERNATE_STRATS_AVAIL_IND")
	private String alternateStratsAvailInd;

	@Column(name = "ALTERNATE_STRATS_AVAIL_DESC")
	private String alternateStratsAvailDesc;

	@Column(name = "EVALUATION_RATING_RATIONALE")
	private String evaluationRatingRationale;

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

	@Version
	@Column(name = "REVISION_COUNT", insertable = false)
	private long revisionCount;

	@OneToMany(targetEntity = ChrFeatureIdentity.class, fetch = FetchType.EAGER)
	@JoinColumn(name = "CHR_CHECKLIST_ID", insertable = false, updatable = false)
	private Set chrFeatureIdentities = new HashSet(0);

	@OneToMany(targetEntity = ChrChecklistParticipation.class, fetch = FetchType.EAGER)
	@JoinColumn(name = "CHR_CHECKLIST_ID", insertable = false, updatable = false)
	private Set chrChecklistParticipations = new HashSet(0);

	@OneToMany(targetEntity = ChrChecklistAttachment.class, fetch = FetchType.EAGER)
	@JoinColumn(name = "CHR_CHECKLIST_ID", insertable = false, updatable = false)
	private Set chrChecklistAttachments = new HashSet(0);

	@Column(name = "DEVICE_CHECKOUT_GUID")
	private byte[] deviceCheckoutGuid;

	public ChrChecklist() {
	}

	public ChrChecklist(FrepResourceValue frepResourceValue, FrepChecklistStatusCode frepChecklistStatusCode,
			String assessedBy, Date evaluationDate, String targetedSampleInd, String limitingOperatnlFactorsInd,
			String effectiveStratsUsedInd, String alternateStratsAvailInd, String evaluationRatingRationale,
			String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp, long revisionCount) {
		this.frepResourceValue = frepResourceValue;
		this.frepChecklistStatusCode = frepChecklistStatusCode;
		this.assessedBy = assessedBy;
		this.evaluationDate = evaluationDate;
		this.limitingOperatnlFactorsInd = limitingOperatnlFactorsInd;
		this.effectiveStratsUsedInd = effectiveStratsUsedInd;
		this.alternateStratsAvailInd = alternateStratsAvailInd;
		this.evaluationRatingRationale = evaluationRatingRationale;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public ChrChecklist(FrepResourceValue frepResourceValue, FrepChecklistStatusCode frepChecklistStatusCode,
			ChrSiteEvaluationCode frepSiteEvaluationCode, FrepMrvaRatingCode frepMrvaRatingCode, String assessedBy,
			Date evaluationDate, String firstNationsPlacename, String targetedSampleInd, String locationDescription, String blockComments,
			String limitingOperatnlFactorsInd, String limitingOperatnlFactorsDesc, String effectiveStratsUsedInd,
			String effectiveStratsUsedDesc, String alternateStratsAvailInd, String alternateStratsAvailDesc,
			String evaluationRatingRationale, String entryUserid, Date entryTimestamp, String updateUserid,
			Date updateTimestamp, long revisionCount, Set chrFeatureIdentities) {
		this.frepResourceValue = frepResourceValue;
		this.frepChecklistStatusCode = frepChecklistStatusCode;
		this.chrSiteEvaluationCode = frepSiteEvaluationCode;
		this.frepMrvaRatingCode = frepMrvaRatingCode;
		this.assessedBy = assessedBy;
		this.evaluationDate = evaluationDate;
		this.firstNationsPlacename = firstNationsPlacename;
		this.locationDescription = locationDescription;
		this.blockComments = blockComments;
		this.limitingOperatnlFactorsInd = limitingOperatnlFactorsInd;
		this.limitingOperatnlFactorsDesc = limitingOperatnlFactorsDesc;
		this.effectiveStratsUsedInd = effectiveStratsUsedInd;
		this.effectiveStratsUsedDesc = effectiveStratsUsedDesc;
		this.alternateStratsAvailInd = alternateStratsAvailInd;
		this.alternateStratsAvailDesc = alternateStratsAvailDesc;
		this.evaluationRatingRationale = evaluationRatingRationale;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
		this.chrFeatureIdentities = chrFeatureIdentities;
	}

	public Long getChrChecklistId() {
		return this.chrChecklistId;
	}

	public void setChrChecklistId(Long chrChecklistId) {
		this.chrChecklistId = chrChecklistId;
	}

	public FrepResourceValue getFrepResourceValue() {
		return this.frepResourceValue;
	}

	public void setFrepResourceValue(FrepResourceValue frepResourceValue) {
		this.frepResourceValue = frepResourceValue;
	}

	public FrepChecklistStatusCode getFrepChecklistStatusCode() {
		return this.frepChecklistStatusCode;
	}

	public void setFrepChecklistStatusCode(FrepChecklistStatusCode frepChecklistStatusCode) {
		this.frepChecklistStatusCode = frepChecklistStatusCode;
	}


	public FrepMrvaRatingCode getFrepMrvaRatingCode() {
		return this.frepMrvaRatingCode;
	}

	public void setFrepMrvaRatingCode(FrepMrvaRatingCode frepMrvaRatingCode) {
		this.frepMrvaRatingCode = frepMrvaRatingCode;
	}

	public String getAssessedBy() {
		return this.assessedBy;
	}

	public void setAssessedBy(String assessedBy) {
		this.assessedBy = assessedBy;
	}

	public String getFirstNationsPlacename() {
		return this.firstNationsPlacename;
	}

	public void setFirstNationsPlacename(String firstNationsPlacename) {
		this.firstNationsPlacename = firstNationsPlacename;
	}

	public String getLocationDescription() {
		return this.locationDescription;
	}

	public void setLocationDescription(String locationDescription) {
		this.locationDescription = locationDescription;
	}

	public String getBlockComments() {
		return this.blockComments;
	}

	public void setBlockComments(String blockComments) {
		this.blockComments = blockComments;
	}

	public String getLimitingOperatnlFactorsInd() {
		return this.limitingOperatnlFactorsInd;
	}

	public void setLimitingOperatnlFactorsInd(String limitingOperatnlFactorsInd) {
		this.limitingOperatnlFactorsInd = limitingOperatnlFactorsInd;
	}

	public String getLimitingOperatnlFactorsDesc() {
		return this.limitingOperatnlFactorsDesc;
	}

	public void setLimitingOperatnlFactorsDesc(String limitingOperatnlFactorsDesc) {
		this.limitingOperatnlFactorsDesc = limitingOperatnlFactorsDesc;
	}

	public String getEffectiveStratsUsedInd() {
		return this.effectiveStratsUsedInd;
	}

	public void setEffectiveStratsUsedInd(String effectiveStratsUsedInd) {
		this.effectiveStratsUsedInd = effectiveStratsUsedInd;
	}

	public String getEffectiveStratsUsedDesc() {
		return this.effectiveStratsUsedDesc;
	}

	public void setEffectiveStratsUsedDesc(String effectiveStratsUsedDesc) {
		this.effectiveStratsUsedDesc = effectiveStratsUsedDesc;
	}

	public String getAlternateStratsAvailInd() {
		return this.alternateStratsAvailInd;
	}

	public void setAlternateStratsAvailInd(String alternateStratsAvailInd) {
		this.alternateStratsAvailInd = alternateStratsAvailInd;
	}

	public String getAlternateStratsAvailDesc() {
		return this.alternateStratsAvailDesc;
	}

	public void setAlternateStratsAvailDesc(String alternateStratsAvailDesc) {
		this.alternateStratsAvailDesc = alternateStratsAvailDesc;
	}

	public String getEvaluationRatingRationale() {
		return this.evaluationRatingRationale;
	}

	public void setEvaluationRatingRationale(String evaluationRatingRationale) {
		this.evaluationRatingRationale = evaluationRatingRationale;
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

	public Set getChrFeatureIdentities() {
		return this.chrFeatureIdentities;
	}

	public void setChrFeatureIdentities(Set chrFeatureIdentities) {
		this.chrFeatureIdentities = chrFeatureIdentities;
	}

	public Set getChrChecklistParticipations() {
		return chrChecklistParticipations;
	}

	public void setChrChecklistParticipations(Set chrChecklistParticipations) {
		this.chrChecklistParticipations = chrChecklistParticipations;
	}

	public Date getEvaluationDate() {
		return evaluationDate;
	}

	public void setEvaluationDate(Date evaluationDate) {
		this.evaluationDate = evaluationDate;
	}

	public Set getChrChecklistAttachments() {
		return chrChecklistAttachments;
	}

	public void setChrChecklistAttachments(Set chrChecklistAttachments) {
		this.chrChecklistAttachments = chrChecklistAttachments;
	}

	public byte[] getDeviceCheckoutGuid() {
		return deviceCheckoutGuid;
	}

	public void setDeviceCheckoutGuid(byte[] deviceCheckoutGuid) {
		this.deviceCheckoutGuid = deviceCheckoutGuid;
	}

	public ChrSiteEvaluationCode getChrSiteEvaluationCode() {
		return chrSiteEvaluationCode;
	}

	public void setChrSiteEvaluationCode(ChrSiteEvaluationCode chrSiteEvaluationCode) {
		this.chrSiteEvaluationCode = chrSiteEvaluationCode;
	}

}
