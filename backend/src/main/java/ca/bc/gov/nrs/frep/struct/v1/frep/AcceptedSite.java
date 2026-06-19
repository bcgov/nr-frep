package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "effectiveYear", "masterList", "orgUnitCode", "orgUnitName", "orgUnitNo", "protocolCode", "protocolName",
		"openingID", "checklistID", "evaluationDate",
		"licenseNumber", "cutBlock", "cuttingPermit",
		"status", "statusCode","downloadedBy",
		"targeted", "modified",
		"fREPAssessment", "saveOffline", "offline", "getDataIn",
		"photosNumber", "featuresNumber" })
public class AcceptedSite {

	@JsonProperty("effectiveYear")
	private String effectiveYear;
	@JsonProperty("masterList")
	private String masterList;
	@JsonProperty("orgUnitCode")
	private String orgUnitCode;
	@JsonProperty("orgUnitName")
	private String orgUnitName;
	@JsonProperty("orgUnitNo")
	private String orgUnitNo;
	@JsonProperty("protocolCode")
	private String protocolCode;
	@JsonProperty("protocolName")
	private String protocolName;

	@JsonProperty("openingID")
	private String openingID;
	@JsonProperty("checklistID")
	private String checklistID;
	@JsonProperty("evaluationDate")
	private String evaluationDate;

	@JsonProperty("licenseNumber")
	private String licenseNumber;
	@JsonProperty("cutBlock")
	private String cutBlock;
	@JsonProperty("cuttingPermit")
	private String cuttingPermit;

	@JsonProperty("status")
	private String status;
	@JsonProperty("statusCode")
	private String statusCode;
	@JsonProperty("downloadedBy")
	private String downloadedBy;

	// TODO To remove?
	@JsonProperty("targeted")
	private String targeted;
	@JsonProperty("modified")
	private String modified;

	// Quid?
	@JsonProperty("fREPAssessment")
	private String fREPAssessment;
	@JsonProperty("saveOffline")
	private String saveOffline;
	@JsonProperty("offline")
	private String offline;
	@JsonProperty("getDataIn")
	private String getDataIn;

	// TODO to remove
	@JsonProperty("photosNumber")
	private Integer photosNumber;
	@JsonProperty("featuresNumber")
	private Integer featuresNumber;

	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	@JsonProperty("openingID")
	public String getOpeningID() {
		return openingID;
	}
	@JsonProperty("openingID")
	public void setOpeningID(String openingID) {
		this.openingID = openingID;
	}

	@JsonProperty("checklistID")
	public String getChecklistID() {
		return checklistID;
	}
	@JsonProperty("checklistID")
	public void setChecklistID(String checklistID) {
		this.checklistID = checklistID;
	}

	@JsonProperty("licenseNumber")
	public String getLicenseNumber() {
		return licenseNumber;
	}
	@JsonProperty("licenseNumber")
	public void setLicenseNumber(String licenseNumber) {
		this.licenseNumber = licenseNumber;
	}

	@JsonProperty("cutBlock")
	public String getCutBlock() {
		return cutBlock;
	}
	@JsonProperty("cutBlock")
	public void setCutBlock(String cutBlock) {
		this.cutBlock = cutBlock;
	}

	@JsonProperty("cuttingPermit")
	public String getCuttingPermit() {
		return cuttingPermit;
	}
	@JsonProperty("cuttingPermit")
	public void setCuttingPermit(String cuttingPermit) {
		this.cuttingPermit = cuttingPermit;
	}

	@JsonProperty("fREPAssessment")
	public String getFREPAssessment() {
		return fREPAssessment;
	}

	@JsonProperty("fREPAssessment")
	public void setFREPAssessment(String fREPAssessment) {
		this.fREPAssessment = fREPAssessment;
	}

	@JsonProperty("evaluationDate")
	public String getEvaluationDate() {
		return evaluationDate;
	}
	@JsonProperty("evaluationDate")
	public void setEvaluationDate(String evaluationDate) {
		this.evaluationDate = evaluationDate;
	}

	@JsonProperty("status")
	public String getStatus() {
		return status;
	}
	@JsonProperty("status")
	public void setStatus(String status) {
		this.status = status;
	}
	@JsonProperty("statusCode")
	public String getStatusCode() {
		return statusCode;
	}
	@JsonProperty("statusCode")
	public void setStatusCode(String statusCode) {
		this.statusCode = statusCode;
	}
	@JsonProperty("downloadedBy")
	public String getDownloadedBy() {
		return downloadedBy;
	}
	@JsonProperty("downloadedBy")
	public void setDownloadedBy(String downloadedBy) {
		this.downloadedBy = downloadedBy;
	}

	@JsonProperty("effectiveYear")
	public String getEffectiveYear() {
		return effectiveYear;
	}
	@JsonProperty("effectiveYear")
	public void setEffectiveYear(String effectiveYear) {
		this.effectiveYear = effectiveYear;
	}
	@JsonProperty("masterList")
	public String getMasterList() {
		return masterList;
	}
	@JsonProperty("masterList")
	public void setMasterList(String masterList) {
		this.masterList = masterList;
	}

	@JsonProperty("orgUnitCode")
	public String getOrgUnitCode() {
		return orgUnitCode;
	}
	@JsonProperty("orgUnitCode")
	public void setOrgUnitCode(String orgUnitCode) {
		this.orgUnitCode = orgUnitCode;
	}
	@JsonProperty("orgUnitName")
	public String getOrgUnitName() {
		return orgUnitName;
	}
	@JsonProperty("orgUnitName")
	public void setOrgUnitName(String orgUnitName) {
		this.orgUnitName = orgUnitName;
	}
	@JsonProperty("orgUnitNo")
	public String getOrgUnitNo() {
		return orgUnitNo;
	}
	@JsonProperty("orgUnitNo")
	public void setOrgUnitNo(String orgUnitNo) {
		this.orgUnitNo = orgUnitNo;
	}

	@JsonProperty("protocolCode")
	public String getProtocolCode() {
		return protocolCode;
	}
	@JsonProperty("protocolCode")
	public void setProtocolCode(String protocolCode) {
		this.protocolCode = protocolCode;
	}
	@JsonProperty("protocolName")
	public String getProtocolName() {
		return protocolName;
	}
	@JsonProperty("protocolName")
	public void setProtocolName(String protocolName) {
		this.protocolName = protocolName;
	}

	@JsonProperty("targeted")
	public String getTargeted() {
		return targeted;
	}
	@JsonProperty("targeted")
	public void setTargeted(String targeted) {
		this.targeted = targeted;
	}

	@JsonProperty("modified")
	public String getModified() {
		return modified;
	}
	@JsonProperty("modified")
	public void setModified(String modified) {
		this.modified = modified;
	}

	@JsonProperty("saveOffline")
	public String getSaveOffline() {
		return saveOffline;
	}
	@JsonProperty("saveOffline")
	public void setSaveOffline(String saveOffline) {
		this.saveOffline = saveOffline;
	}

	@JsonProperty("offline")
	public String getOffline() {
		return offline;
	}
	@JsonProperty("offline")
	public void setOffline(String offline) {
		this.offline = offline;
	}

	@JsonProperty("getDataIn")
	public String getGetDataIn() {
		return getDataIn;
	}
	@JsonProperty("getDataIn")
	public void setGetDataIn(String getDataIn) {
		this.getDataIn = getDataIn;
	}

	@JsonProperty("photosNumber")
	public Integer getPhotosNumber() {
		return photosNumber;
	}
	@JsonProperty("photosNumber")
	public void setPhotosNumber(Integer photosNumber) {
		this.photosNumber = photosNumber;
	}

	@JsonProperty("featuresNumber")
	public Integer getFeaturesNumber() {
		return featuresNumber;
	}
	@JsonProperty("featuresNumber")
	public void setFeaturesNumber(Integer featuresNumber) {
		this.featuresNumber = featuresNumber;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}
	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

}
