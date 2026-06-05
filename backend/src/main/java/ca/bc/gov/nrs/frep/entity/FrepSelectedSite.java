package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "FREP_SELECTED_SITE", schema = "THE")
public class FrepSelectedSite implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "frepSelectedSiteIdSeq")
	@SequenceGenerator(name = "frepSelectedSiteIdSeq", sequenceName = "THE.FREP_SELECTED_SITE_SEQ", allocationSize = 1)
	@Column(name = "FREP_SELECTED_SITE_ID")
	private Long frepSelectedSiteId;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "FREP_SELECTED_SITE_CODE")
	private FrepSelectedSiteCode frepSelectedSiteCode;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "CLIENT_NUMBER")
	private ForestClient forestClient;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "ORG_UNIT_NO")
	private OrgUnit orgUnit;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "EFFECTIVE_YEAR")
	private FrepEvaluationYear frepEvaluationYear;

	@Column(name = "OPENING_ID")
	private long openingId;
	@Column(name = "CUT_BLOCK_OPEN_ADMIN_ID")
	private long cutBlockOpenAdminId;
	@Column(name = "RANDOM_LIST_ORDER_NUMBER")
	private Integer randomListOrderNumber;
	@Column(name = "DISTURBANCE_CODE")
	private String disturbanceCode;
	@Column(name = "MAPSHEET_GRID")
	private String mapsheetGrid;
	@Column(name = "MAPSHEET_LETTER")
	private String mapsheetLetter;
	@Column(name = "MAPSHEET_SQUARE")
	private String mapsheetSquare;
	@Column(name = "MAPSHEET_QUAD")
	private String mapsheetQuad;
	@Column(name = "MAPSHEET_SUB_QUAD")
	private String mapsheetSubQuad;
	@Column(name = "OPENING_NUMBER")
	private String openingNumber;
	@Column(name = "FOREST_FILE_ID")
	private String forestFileId;
	@Column(name = "CUTTING_PERMIT_ID")
	private String cuttingPermitId;
	@Column(name = "CUT_BLOCK_ID")
	private String cutBlockId;
	@Column(name = "CB_SKEY")
	private Long cbSkey;
	@Column(name = "HVA_SKEY")
	private Long hvaSkey;
	@Column(name = "OPENING_PRIME_LICENCE_IND")
	private String openingPrimeLicenceInd;
	@Column(name = "DISTURBANCE_START_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date disturbanceStartDate;
	@Column(name = "DISTURBANCE_END_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date disturbanceEndDate;
	@Column(name = "MGMT_UNIT_TYPE_CODE")
	private String mgmtUnitTypeCode;
	@Column(name = "MGMT_UNIT_ID")
	private String mgmtUnitId;
	@Column(name = "FILE_CLIENT_TYPE_CODE")
	private String fileClientTypeCode;
	@Column(name = "EXHIBIT_AREA")
	private BigDecimal exhibitArea;
	@Column(name = "OPENING_GROSS_AREA")
	private BigDecimal openingGrossArea;
	@Column(name = "DISTURBANCE_GROSS_AREA")
	private BigDecimal disturbanceGrossArea;
	@Column(name = "NAR_AREA")
	private BigDecimal narArea;
	@Column(name = "REVISION_COUNT")
	private int revisionCount;
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
	@OneToMany(targetEntity = FrepResourceValue.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "FREP_SELECTED_SITE_ID", insertable = false, updatable = false)
	private Set frepResourceValues = new HashSet(0);

	public FrepSelectedSite() {
	}

	public FrepSelectedSite(FrepSelectedSiteCode frepSelectedSiteCode, OrgUnit orgUnit,
			FrepEvaluationYear frepEvaluationYear, long openingId, long cutBlockOpenAdminId, int revisionCount,
			String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp) {
		this.frepSelectedSiteCode = frepSelectedSiteCode;
		this.orgUnit = orgUnit;
		this.frepEvaluationYear = frepEvaluationYear;
		this.openingId = openingId;
		this.cutBlockOpenAdminId = cutBlockOpenAdminId;
		this.revisionCount = revisionCount;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
	}

	public FrepSelectedSite(FrepSelectedSiteCode frepSelectedSiteCode, ForestClient forestClient, OrgUnit orgUnit,
			FrepEvaluationYear frepEvaluationYear, long openingId, long cutBlockOpenAdminId,
			Integer randomListOrderNumber, String disturbanceCode, String mapsheetGrid, String mapsheetLetter,
			String mapsheetSquare, String mapsheetQuad, String mapsheetSubQuad, String openingNumber,
			String forestFileId, String cuttingPermitId, String cutBlockId, Long cbSkey, Long hvaSkey,
			String openingPrimeLicenceInd, Date disturbanceStartDate, Date disturbanceEndDate, String mgmtUnitTypeCode,
			String mgmtUnitId, String fileClientTypeCode, BigDecimal exhibitArea, BigDecimal openingGrossArea,
			BigDecimal disturbanceGrossArea, BigDecimal narArea, int revisionCount, String entryUserid,
			Date entryTimestamp, String updateUserid, Date updateTimestamp, Set frepResourceValues) {
		this.frepSelectedSiteCode = frepSelectedSiteCode;
		this.forestClient = forestClient;
		this.orgUnit = orgUnit;
		this.frepEvaluationYear = frepEvaluationYear;
		this.openingId = openingId;
		this.cutBlockOpenAdminId = cutBlockOpenAdminId;
		this.randomListOrderNumber = randomListOrderNumber;
		this.disturbanceCode = disturbanceCode;
		this.mapsheetGrid = mapsheetGrid;
		this.mapsheetLetter = mapsheetLetter;
		this.mapsheetSquare = mapsheetSquare;
		this.mapsheetQuad = mapsheetQuad;
		this.mapsheetSubQuad = mapsheetSubQuad;
		this.openingNumber = openingNumber;
		this.forestFileId = forestFileId;
		this.cuttingPermitId = cuttingPermitId;
		this.cutBlockId = cutBlockId;
		this.cbSkey = cbSkey;
		this.hvaSkey = hvaSkey;
		this.openingPrimeLicenceInd = openingPrimeLicenceInd;
		this.disturbanceStartDate = disturbanceStartDate;
		this.disturbanceEndDate = disturbanceEndDate;
		this.mgmtUnitTypeCode = mgmtUnitTypeCode;
		this.mgmtUnitId = mgmtUnitId;
		this.fileClientTypeCode = fileClientTypeCode;
		this.exhibitArea = exhibitArea;
		this.openingGrossArea = openingGrossArea;
		this.disturbanceGrossArea = disturbanceGrossArea;
		this.narArea = narArea;
		this.revisionCount = revisionCount;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.frepResourceValues = frepResourceValues;
	}

	public Long getFrepSelectedSiteId() {
		return this.frepSelectedSiteId;
	}
	public void setFrepSelectedSiteId(Long frepSelectedSiteId) {
		this.frepSelectedSiteId = frepSelectedSiteId;
	}

	public long getOpeningId() {
		return this.openingId;
	}
	public void setOpeningId(long openingId) {
		this.openingId = openingId;
	}

	public FrepSelectedSiteCode getFrepSelectedSiteCode() {
		return this.frepSelectedSiteCode;
	}
	public void setFrepSelectedSiteCode(FrepSelectedSiteCode frepSelectedSiteCode) {
		this.frepSelectedSiteCode = frepSelectedSiteCode;
	}

	public ForestClient getForestClient() {
		return this.forestClient;
	}
	public void setForestClient(ForestClient forestClient) {
		this.forestClient = forestClient;
	}

	public OrgUnit getOrgUnit() {
		return this.orgUnit;
	}
	public void setOrgUnit(OrgUnit orgUnit) {
		this.orgUnit = orgUnit;
	}

	public FrepEvaluationYear getFrepEvaluationYear() {
		return this.frepEvaluationYear;
	}
	public void setFrepEvaluationYear(FrepEvaluationYear frepEvaluationYear) {
		this.frepEvaluationYear = frepEvaluationYear;
	}

	public long getCutBlockOpenAdminId() {
		return this.cutBlockOpenAdminId;
	}
	public void setCutBlockOpenAdminId(long cutBlockOpenAdminId) {
		this.cutBlockOpenAdminId = cutBlockOpenAdminId;
	}

	public Integer getRandomListOrderNumber() {
		return this.randomListOrderNumber;
	}

	public void setRandomListOrderNumber(Integer randomListOrderNumber) {
		this.randomListOrderNumber = randomListOrderNumber;
	}

	public String getDisturbanceCode() {
		return this.disturbanceCode;
	}

	public void setDisturbanceCode(String disturbanceCode) {
		this.disturbanceCode = disturbanceCode;
	}

	public String getMapsheetGrid() {
		return this.mapsheetGrid;
	}

	public void setMapsheetGrid(String mapsheetGrid) {
		this.mapsheetGrid = mapsheetGrid;
	}

	public String getMapsheetLetter() {
		return this.mapsheetLetter;
	}

	public void setMapsheetLetter(String mapsheetLetter) {
		this.mapsheetLetter = mapsheetLetter;
	}

	public String getMapsheetSquare() {
		return this.mapsheetSquare;
	}

	public void setMapsheetSquare(String mapsheetSquare) {
		this.mapsheetSquare = mapsheetSquare;
	}

	public String getMapsheetQuad() {
		return this.mapsheetQuad;
	}

	public void setMapsheetQuad(String mapsheetQuad) {
		this.mapsheetQuad = mapsheetQuad;
	}

	public String getMapsheetSubQuad() {
		return this.mapsheetSubQuad;
	}

	public void setMapsheetSubQuad(String mapsheetSubQuad) {
		this.mapsheetSubQuad = mapsheetSubQuad;
	}

	public String getOpeningNumber() {
		return this.openingNumber;
	}

	public void setOpeningNumber(String openingNumber) {
		this.openingNumber = openingNumber;
	}

	public String getForestFileId() {
		return this.forestFileId;
	}

	public void setForestFileId(String forestFileId) {
		this.forestFileId = forestFileId;
	}

	public String getCuttingPermitId() {
		return this.cuttingPermitId;
	}

	public void setCuttingPermitId(String cuttingPermitId) {
		this.cuttingPermitId = cuttingPermitId;
	}

	public String getCutBlockId() {
		return this.cutBlockId;
	}

	public void setCutBlockId(String cutBlockId) {
		this.cutBlockId = cutBlockId;
	}

	public Long getCbSkey() {
		return this.cbSkey;
	}

	public void setCbSkey(Long cbSkey) {
		this.cbSkey = cbSkey;
	}

	public Long getHvaSkey() {
		return this.hvaSkey;
	}

	public void setHvaSkey(Long hvaSkey) {
		this.hvaSkey = hvaSkey;
	}

	public String getOpeningPrimeLicenceInd() {
		return this.openingPrimeLicenceInd;
	}

	public void setOpeningPrimeLicenceInd(String openingPrimeLicenceInd) {
		this.openingPrimeLicenceInd = openingPrimeLicenceInd;
	}

	public Date getDisturbanceStartDate() {
		return this.disturbanceStartDate;
	}

	public void setDisturbanceStartDate(Date disturbanceStartDate) {
		this.disturbanceStartDate = disturbanceStartDate;
	}

	public Date getDisturbanceEndDate() {
		return this.disturbanceEndDate;
	}

	public void setDisturbanceEndDate(Date disturbanceEndDate) {
		this.disturbanceEndDate = disturbanceEndDate;
	}

	public String getMgmtUnitTypeCode() {
		return this.mgmtUnitTypeCode;
	}

	public void setMgmtUnitTypeCode(String mgmtUnitTypeCode) {
		this.mgmtUnitTypeCode = mgmtUnitTypeCode;
	}

	public String getMgmtUnitId() {
		return this.mgmtUnitId;
	}

	public void setMgmtUnitId(String mgmtUnitId) {
		this.mgmtUnitId = mgmtUnitId;
	}

	public String getFileClientTypeCode() {
		return this.fileClientTypeCode;
	}

	public void setFileClientTypeCode(String fileClientTypeCode) {
		this.fileClientTypeCode = fileClientTypeCode;
	}

	public BigDecimal getExhibitArea() {
		return this.exhibitArea;
	}

	public void setExhibitArea(BigDecimal exhibitArea) {
		this.exhibitArea = exhibitArea;
	}

	public BigDecimal getOpeningGrossArea() {
		return this.openingGrossArea;
	}

	public void setOpeningGrossArea(BigDecimal openingGrossArea) {
		this.openingGrossArea = openingGrossArea;
	}

	public BigDecimal getDisturbanceGrossArea() {
		return this.disturbanceGrossArea;
	}

	public void setDisturbanceGrossArea(BigDecimal disturbanceGrossArea) {
		this.disturbanceGrossArea = disturbanceGrossArea;
	}

	public BigDecimal getNarArea() {
		return this.narArea;
	}

	public void setNarArea(BigDecimal narArea) {
		this.narArea = narArea;
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

	public Set getFrepResourceValues() {
		return this.frepResourceValues;
	}

	public void setFrepResourceValues(Set frepResourceValues) {
		this.frepResourceValues = frepResourceValues;
	}

}
