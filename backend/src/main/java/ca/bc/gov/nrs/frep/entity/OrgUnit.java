package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ORG_UNIT", schema = "THE")
public class OrgUnit implements java.io.Serializable {

	@Id
	@Column(name = "ORG_UNIT_NO")
	private long orgUnitNo;
	@Column(name = "ORG_UNIT_CODE")
	private String orgUnitCode;
	@Column(name = "ORG_UNIT_NAME")
	private String orgUnitName;
	@Column(name = "LOCATION_CODE")
	private String locationCode;
	@Column(name = "AREA_CODE")
	private String areaCode;
	@Column(name = "TELEPHONE_NO")
	private String telephoneNo;
	@Column(name = "ORG_LEVEL_CODE")
	private String orgLevelCode;
	@Column(name = "OFFICE_NAME_CODE")
	private String officeNameCode;
	@Column(name = "ROLLUP_REGION_NO")
	private long rollupRegionNo;
	@Column(name = "ROLLUP_REGION_CODE")
	private String rollupRegionCode;
	@Column(name = "ROLLUP_DIST_NO")
	private long rollupDistNo;
	@Column(name = "ROLLUP_DIST_CODE")
	private String rollupDistCode;
	@Column(name = "EFFECTIVE_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date effectiveDate;
	@Column(name = "EXPIRY_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date expiryDate;
	@Column(name = "UPDATE_TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date updateTimestamp;
	@OneToMany(targetEntity = FrepSelectedSite.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "ORG_UNIT_NO", insertable = false, updatable = false)
	private Set frepSelectedSites = new HashSet(0);

	public OrgUnit() {
	}

	public OrgUnit(long orgUnitNo, String orgUnitCode, String orgUnitName, String locationCode, String areaCode,
			String telephoneNo, String orgLevelCode, String officeNameCode, long rollupRegionNo,
			String rollupRegionCode, long rollupDistNo, String rollupDistCode, Date effectiveDate, Date expiryDate) {
		this.orgUnitNo = orgUnitNo;
		this.orgUnitCode = orgUnitCode;
		this.orgUnitName = orgUnitName;
		this.locationCode = locationCode;
		this.areaCode = areaCode;
		this.telephoneNo = telephoneNo;
		this.orgLevelCode = orgLevelCode;
		this.officeNameCode = officeNameCode;
		this.rollupRegionNo = rollupRegionNo;
		this.rollupRegionCode = rollupRegionCode;
		this.rollupDistNo = rollupDistNo;
		this.rollupDistCode = rollupDistCode;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
	}

	public OrgUnit(long orgUnitNo, String orgUnitCode, String orgUnitName, String locationCode, String areaCode,
			String telephoneNo, String orgLevelCode, String officeNameCode, long rollupRegionNo,
			String rollupRegionCode, long rollupDistNo, String rollupDistCode, Date effectiveDate, Date expiryDate,
			Date updateTimestamp, Set frepSelectedSites) {
		this.orgUnitNo = orgUnitNo;
		this.orgUnitCode = orgUnitCode;
		this.orgUnitName = orgUnitName;
		this.locationCode = locationCode;
		this.areaCode = areaCode;
		this.telephoneNo = telephoneNo;
		this.orgLevelCode = orgLevelCode;
		this.officeNameCode = officeNameCode;
		this.rollupRegionNo = rollupRegionNo;
		this.rollupRegionCode = rollupRegionCode;
		this.rollupDistNo = rollupDistNo;
		this.rollupDistCode = rollupDistCode;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.frepSelectedSites = frepSelectedSites;
	}

	public long getOrgUnitNo() {
		return this.orgUnitNo;
	}

	public void setOrgUnitNo(long orgUnitNo) {
		this.orgUnitNo = orgUnitNo;
	}

	public String getOrgUnitCode() {
		return this.orgUnitCode;
	}

	public void setOrgUnitCode(String orgUnitCode) {
		this.orgUnitCode = orgUnitCode;
	}

	public String getOrgUnitName() {
		return this.orgUnitName;
	}

	public void setOrgUnitName(String orgUnitName) {
		this.orgUnitName = orgUnitName;
	}

	public String getLocationCode() {
		return this.locationCode;
	}

	public void setLocationCode(String locationCode) {
		this.locationCode = locationCode;
	}

	public String getAreaCode() {
		return this.areaCode;
	}

	public void setAreaCode(String areaCode) {
		this.areaCode = areaCode;
	}

	public String getTelephoneNo() {
		return this.telephoneNo;
	}

	public void setTelephoneNo(String telephoneNo) {
		this.telephoneNo = telephoneNo;
	}

	public String getOrgLevelCode() {
		return this.orgLevelCode;
	}

	public void setOrgLevelCode(String orgLevelCode) {
		this.orgLevelCode = orgLevelCode;
	}

	public String getOfficeNameCode() {
		return this.officeNameCode;
	}

	public void setOfficeNameCode(String officeNameCode) {
		this.officeNameCode = officeNameCode;
	}

	public long getRollupRegionNo() {
		return this.rollupRegionNo;
	}

	public void setRollupRegionNo(long rollupRegionNo) {
		this.rollupRegionNo = rollupRegionNo;
	}

	public String getRollupRegionCode() {
		return this.rollupRegionCode;
	}

	public void setRollupRegionCode(String rollupRegionCode) {
		this.rollupRegionCode = rollupRegionCode;
	}

	public long getRollupDistNo() {
		return this.rollupDistNo;
	}

	public void setRollupDistNo(long rollupDistNo) {
		this.rollupDistNo = rollupDistNo;
	}

	public String getRollupDistCode() {
		return this.rollupDistCode;
	}

	public void setRollupDistCode(String rollupDistCode) {
		this.rollupDistCode = rollupDistCode;
	}

	public Date getEffectiveDate() {
		return this.effectiveDate;
	}

	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public Date getExpiryDate() {
		return this.expiryDate;
	}

	public void setExpiryDate(Date expiryDate) {
		this.expiryDate = expiryDate;
	}

	public Date getUpdateTimestamp() {
		return this.updateTimestamp;
	}

	public void setUpdateTimestamp(Date updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	public Set getFrepSelectedSites() {
		return this.frepSelectedSites;
	}
	public void setFrepSelectedSites(Set frepSelectedSites) {
		this.frepSelectedSites = frepSelectedSites;
	}

}
