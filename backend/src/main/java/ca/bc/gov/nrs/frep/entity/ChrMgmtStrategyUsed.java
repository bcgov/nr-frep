package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "CHR_MGMT_STRATEGY_USED", schema = "THE")
public class ChrMgmtStrategyUsed implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chrMgmtStrategyUsedIdSeq")
	@SequenceGenerator(name = "chrMgmtStrategyUsedIdSeq", sequenceName = "THE.CHR_MGMT_STRATEGY_USED_SEQ", allocationSize = 1)
	@Column(name = "CHR_MGMT_STRATEGY_USED_ID")
	private Long chrMgmtStrategyUsedId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_MGMT_STRATEGY_TYPE_CODE")
	private ChrMgmtStrategyTypeCode chrMgmtStrategyTypeCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID")
	private ChrFeatureDetail chrFeatureDetail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_RESERVE_TYPE_CODE")
	private ChrReserveTypeCode chrReserveTypeCode;

	@Column(name = "BUFFER_WIDTH_METERS")
	private BigDecimal bufferWidthMeters;

	@Column(name = "OTHER_STRATEGY")
	private String otherStrategy;

	@Column(name = "FULLY_CONSERVED_IND")
	private String fullyConservedInd;

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

	public ChrMgmtStrategyUsed() {
	}

	public ChrMgmtStrategyUsed(Long chrMgmtStrategyUsedId, ChrMgmtStrategyTypeCode chrMgmtStrategyTypeCode,
			ChrFeatureDetail chrFeatureDetail, String entryUserid, Date entryTimestamp, String updateUserid,
			Date updateTimestamp, long revisionCount) {
		this.chrMgmtStrategyUsedId = chrMgmtStrategyUsedId;
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
		this.chrFeatureDetail = chrFeatureDetail;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public ChrMgmtStrategyUsed(Long chrMgmtStrategyUsedId, ChrMgmtStrategyTypeCode chrMgmtStrategyTypeCode,
			ChrFeatureDetail chrFeatureDetail, ChrReserveTypeCode chrReserveTypeCode, BigDecimal bufferWidthMeters,
			String otherStrategy, String fullyConservedInd, String entryUserid, Date entryTimestamp,
			String updateUserid, Date updateTimestamp, long revisionCount) {
		this.chrMgmtStrategyUsedId = chrMgmtStrategyUsedId;
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
		this.chrFeatureDetail = chrFeatureDetail;
		this.chrReserveTypeCode = chrReserveTypeCode;
		this.bufferWidthMeters = bufferWidthMeters;
		this.otherStrategy = otherStrategy;
		this.fullyConservedInd = fullyConservedInd;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public Long getChrMgmtStrategyUsedId() {
		return chrMgmtStrategyUsedId;
	}

	public void setChrMgmtStrategyUsedId(Long chrMgmtStrategyUsedId) {
		this.chrMgmtStrategyUsedId = chrMgmtStrategyUsedId;
	}

	public ChrMgmtStrategyTypeCode getChrMgmtStrategyTypeCode() {
		return this.chrMgmtStrategyTypeCode;
	}

	public void setChrMgmtStrategyTypeCode(ChrMgmtStrategyTypeCode chrMgmtStrategyTypeCode) {
		this.chrMgmtStrategyTypeCode = chrMgmtStrategyTypeCode;
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

	public BigDecimal getBufferWidthMeters() {
		return this.bufferWidthMeters;
	}

	public void setBufferWidthMeters(BigDecimal bufferWidthMeters) {
		this.bufferWidthMeters = bufferWidthMeters;
	}

	public String getOtherStrategy() {
		return this.otherStrategy;
	}

	public void setOtherStrategy(String otherStrategy) {
		this.otherStrategy = otherStrategy;
	}

	public String getFullyConservedInd() {
		return this.fullyConservedInd;
	}

	public void setFullyConservedInd(String fullyConservedInd) {
		this.fullyConservedInd = fullyConservedInd;
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
