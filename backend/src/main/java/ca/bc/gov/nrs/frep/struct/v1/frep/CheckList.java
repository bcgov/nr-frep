package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "checklistID", "downloadedBy", "downloadedDate", "FREPAssessment", "evaluationYear",
		"evaluationDate", "assessedBy", "NewDistrict", "district", "GeographicTSA", "ActualTSA", "Region",
		"LandscapeUnit", "FirstNation", "openingID", "openingNumber", "licensee", "cuttingPermit", "block", "Proponent",
		"LicencseeCheckfromRESULTS", "OpeningCategory", "client", "clientName", "yearOfHarvest", "firstNationsName",
		"generalLocation", "targeted", "Contact1Name", "Contact1Date", "Contact1Contacted", "Contact2Name",
		"Contact2Date", "Contact2Contacted", "Contact3Name", "Contact3Date", "Contact3Contacted",
		"FeatureID1Description", "FeatureID1Source", "FeatureID2Description", "FeatureID2Source", "FeatureID2Comments",
		"FeatureID3Description", "FeatureID3Source", "FeatureID4Description", "FeatureID4Source", "FeatureID4Comments",
		"FeatureID5Description", "FeatureID5Comments",
		"q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock", "Q1Comments",
		"q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues",
		"Q2Comments",
		"q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock",
		"Q3Comments",
		"ratingRationale",
		"Q4Comments", "AdditionalCommentsonFormC", "CommentsBlock", "CommentsFeature", "CommentsFeature1",
		"CommentsGeneral", "Comments", "features", "pictures", "FirstNations", "status", "offline", "Modified",
		"SaveOffline", "Source", "Description", "q8Comments", "q9Comments", "q10Comments", "rating",
		"FirstNationsName", "Inreserve", "ListAdd", "FirsNations", "Add", "Commentaires", "deviceCheckoutGuid", "mrva" })
public class CheckList {

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
	@JsonProperty("checklistID")
	private String checklistID;
	@JsonProperty("downloadedBy")
	private String downloadedBy;
	@JsonProperty("downloadedDate")
	private String downloadedDate;
	/** IDIR of the user who last updated the record (CHR_CHECKLIST.UPDATE_USERID). */
	@JsonProperty("updateUserid")
	private String updateUserid;
	/** When the record was last updated, formatted yyyy-MM-dd HH:mm:ss (CHR_CHECKLIST.UPDATE_TIMESTAMP). */
	@JsonProperty("updateTimestamp")
	private String updateTimestamp;
	@JsonProperty("FREPAssessment")
	private String fREPAssessment;
	@JsonProperty("evaluationYear")
	private Integer evaluationYear;
	@JsonProperty("evaluationDate")
	private String evaluationDate;
	@JsonProperty("assessedBy")
	private String assessedBy;
	@JsonProperty("NewDistrict")
	private String newDistrict;
	@JsonProperty("district")
	private String district;
	@JsonProperty("GeographicTSA")
	private String geographicTSA;
	@JsonProperty("ActualTSA")
	private String actualTSA;
	@JsonProperty("Region")
	private String region;
	@JsonProperty("LandscapeUnit")
	private String landscapeUnit;
	@JsonProperty("FirstNation")
	private String firstNation;
	@JsonProperty("openingID")
	private String openingID;
	@JsonProperty("openingNumber")
	private String openingNumber;
	@JsonProperty("licensee")
	private String licensee;
	@JsonProperty("cuttingPermit")
	private String cuttingPermit;
	@JsonProperty("block")
	private String block;
	@JsonProperty("Proponent")
	private String proponent;
	@JsonProperty("LicencseeCheckfromRESULTS")
	private String licencseeCheckfromRESULTS;
	@JsonProperty("OpeningCategory")
	private String openingCategory;
	@JsonProperty("client")
	private String client;
	@JsonProperty("clientName")
	private String clientName;
	@JsonProperty("yearOfHarvest")
	private String yearOfHarvest;
	@JsonProperty("firstNationsName")
	private String firstNationName;
	@JsonProperty("generalLocation")
	private String generalLocation;
	@JsonProperty("targeted")
	private String targeted;
	@JsonProperty("Contact1Name")
	private String contact1Name;
	@JsonProperty("Contact1Date")
	private String contact1Date;
	@JsonProperty("Contact1Contacted")
	private String contact1Contacted;
	@JsonProperty("Contact2Name")
	private String contact2Name;
	@JsonProperty("Contact2Date")
	private String contact2Date;
	@JsonProperty("Contact2Contacted")
	private String contact2Contacted;
	@JsonProperty("Contact3Name")
	private String contact3Name;
	@JsonProperty("Contact3Date")
	private String contact3Date;
	@JsonProperty("Contact3Contacted")
	private String contact3Contacted;
	@JsonProperty("FeatureID1Description")
	private String featureID1Description;
	@JsonProperty("FeatureID1Source")
	private String featureID1Source;
	@JsonProperty("FeatureID2Description")
	private String featureID2Description;
	@JsonProperty("FeatureID2Source")
	private String featureID2Source;
	@JsonProperty("FeatureID2Comments")
	private String featureID2Comments;
	@JsonProperty("FeatureID3Description")
	private String featureID3Description;
	@JsonProperty("FeatureID3Source")
	private String featureID3Source;
	@JsonProperty("FeatureID4Description")
	private String featureID4Description;
	@JsonProperty("FeatureID4Source")
	private String featureID4Source;
	@JsonProperty("FeatureID4Comments")
	private String featureID4Comments;
	@JsonProperty("FeatureID5Description")
	private String featureID5Description;
	@JsonProperty("FeatureID5Comments")
	private String featureID5Comments;
	@JsonProperty("q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock")
	private String q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock;
	@JsonProperty("Q1Comments")
	private String q1Comments;
	@JsonProperty("q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues")
	private String q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues;
	@JsonProperty("Q2Comments")
	private String q2Comments;
	@JsonProperty("q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock")
	private String q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock;
	@JsonProperty("Q3Comments")
	private String q3Comments;
	@JsonProperty("ratingRationale")
	private String ratingRationale;
	@JsonProperty("Q4Comments")
	private String q4Comments;
	@JsonProperty("AdditionalCommentsonFormC")
	private String additionalCommentsonFormC;
	@JsonProperty("CommentsBlock")
	private String commentsBlock;
	@JsonProperty("CommentsFeature")
	private String commentsFeature;
	@JsonProperty("CommentsFeature1")
	private String commentsFeature1;
	@JsonProperty("CommentsGeneral")
	private String commentsGeneral;
	@JsonProperty("Comments")
	private String comments;
	@JsonProperty("features")
	private List<Feature> features = new ArrayList<Feature>();

	// NOTE: This is a DC UI driven attribute to provide attribute to label names and is used to make DC attribute field labels available server side.
	@JsonProperty("labels")
	private List<Label> labels = new ArrayList<Label>();

	@JsonProperty("pictures")
	private List<Picture> pictures = new ArrayList<Picture>();
	@JsonProperty("contacts")
	private List<Contact> contacts = new ArrayList<Contact>();
	@JsonProperty("status")
	private String status;
	@JsonProperty("offline")
	private String offline;
	@JsonProperty("Modified")
	private String modified;
	@JsonProperty("SaveOffline")
	private String saveOffline;
	@JsonProperty("Source")
	private String source;
	@JsonProperty("Description")
	private String description;
	@JsonProperty("q8Comments")
	private String q8Comments;
	@JsonProperty("q9Comments")
	private String q9Comments;
	@JsonProperty("q10Comments")
	private String q10Comments;
	@JsonProperty("rating")
	private String rating;
	@JsonProperty("FirstNationsName")
	private String firstNationsName;
	@JsonProperty("Inreserve")
	private String inreserve;
	@JsonProperty("ListAdd")
	private String listAdd;
	@JsonProperty("FirsNations")
	private List<Object> firsNations = null;
	@JsonProperty("Add")
	private String add;
	@JsonProperty("Commentaires")
	private String commentaires;
	@JsonProperty("deviceCheckoutGuid")
	private String deviceCheckoutGuid;
	@JsonProperty("revisionCount")
	private String revisionCount;
	@JsonProperty("mrvaRatingCode")
	private String mrvaRatingCode = "";


	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	@JsonProperty("checklistID")
	public String getChecklistID() {
		return checklistID;
	}

	@JsonProperty("checklistID")
	public void setChecklistID(String checklistID) {
		this.checklistID = checklistID;
	}

	@JsonProperty("FREPAssessment")
	public String getFREPAssessment() {
		return fREPAssessment;
	}

	@JsonProperty("FREPAssessment")
	public void setFREPAssessment(String fREPAssessment) {
		this.fREPAssessment = fREPAssessment;
	}

	@JsonProperty("evaluationYear")
	public Integer getEvaluationYear() {
		return evaluationYear;
	}

	@JsonProperty("evaluationYear")
	public void setEvaluationYear(Integer evaluationYear) {
		this.evaluationYear = evaluationYear;
	}

	@JsonProperty("evaluationDate")
	public String getEvaluationDate() {
		return evaluationDate;
	}

	@JsonProperty("evaluationDate")
	public void setEvaluationDate(String evaluationDate) {
		this.evaluationDate = evaluationDate;
	}

	@JsonProperty("assessedBy")
	public String getAssessedBy() {
		return assessedBy;
	}

	@JsonProperty("assessedBy")
	public void setAssessedBy(String assessedBy) {
		this.assessedBy = assessedBy;
	}

	@JsonProperty("NewDistrict")
	public String getNewDistrict() {
		return newDistrict;
	}

	@JsonProperty("NewDistrict")
	public void setNewDistrict(String newDistrict) {
		this.newDistrict = newDistrict;
	}

	@JsonProperty("district")
	public String getDistrict() {
		return district;
	}

	@JsonProperty("district")
	public void setDistrict(String district) {
		this.district = district;
	}

	@JsonProperty("GeographicTSA")
	public String getGeographicTSA() {
		return geographicTSA;
	}

	@JsonProperty("GeographicTSA")
	public void setGeographicTSA(String geographicTSA) {
		this.geographicTSA = geographicTSA;
	}

	@JsonProperty("ActualTSA")
	public String getActualTSA() {
		return actualTSA;
	}

	@JsonProperty("ActualTSA")
	public void setActualTSA(String actualTSA) {
		this.actualTSA = actualTSA;
	}

	@JsonProperty("Region")
	public String getRegion() {
		return region;
	}

	@JsonProperty("Region")
	public void setRegion(String region) {
		this.region = region;
	}

	@JsonProperty("LandscapeUnit")
	public String getLandscapeUnit() {
		return landscapeUnit;
	}

	@JsonProperty("LandscapeUnit")
	public void setLandscapeUnit(String landscapeUnit) {
		this.landscapeUnit = landscapeUnit;
	}

	@JsonProperty("FirstNation")
	public String getFirstNation() {
		return firstNation;
	}

	@JsonProperty("FirstNation")
	public void setFirstNation(String firstNation) {
		this.firstNation = firstNation;
	}

	@JsonProperty("openingID")
	public String getOpeningID() {
		return openingID;
	}

	@JsonProperty("openingID")
	public void setOpeningID(String openingID) {
		this.openingID = openingID;
	}

	@JsonProperty("openingNumber")
	public String getOpeningNumber() {
		return openingNumber;
	}

	@JsonProperty("openingNumber")
	public void setOpeningNumber(String openingNumber) {
		this.openingNumber = openingNumber;
	}

	@JsonProperty("licensee")
	public String getLicensee() {
		return licensee;
	}

	@JsonProperty("licensee")
	public void setLicensee(String licensee) {
		this.licensee = licensee;
	}

	@JsonProperty("block")
	public String getBlock() {
		return block;
	}

	@JsonProperty("cuttingPermit")
	public String getCuttingPermit() {
		return cuttingPermit;
	}

	@JsonProperty("cuttingPermit")
	public void setCuttingPermit(String cuttingPermit) {
		this.cuttingPermit = cuttingPermit;
	}

	@JsonProperty("block")
	public void setBlock(String block) {
		this.block = block;
	}

	@JsonProperty("Proponent")
	public String getProponent() {
		return proponent;
	}

	@JsonProperty("Proponent")
	public void setProponent(String proponent) {
		this.proponent = proponent;
	}

	@JsonProperty("LicencseeCheckfromRESULTS")
	public String getLicencseeCheckfromRESULTS() {
		return licencseeCheckfromRESULTS;
	}

	@JsonProperty("LicencseeCheckfromRESULTS")
	public void setLicencseeCheckfromRESULTS(String licencseeCheckfromRESULTS) {
		this.licencseeCheckfromRESULTS = licencseeCheckfromRESULTS;
	}

	@JsonProperty("OpeningCategory")
	public String getOpeningCategory() {
		return openingCategory;
	}

	@JsonProperty("OpeningCategory")
	public void setOpeningCategory(String openingCategory) {
		this.openingCategory = openingCategory;
	}

	@JsonProperty("client")
	public String getClient() {
		return client;
	}

	@JsonProperty("client")
	public void setClient(String client) {
		this.client = client;
	}

	@JsonProperty("clientName")
	public String getClientName() {
		return clientName;
	}

	@JsonProperty("clientName")
	public void setClientName(String clientName) {
		this.clientName = clientName;
	}

	@JsonProperty("yearOfHarvest")
	public String getYearOfHarvest() {
		return yearOfHarvest;
	}

	@JsonProperty("yearOfHarvest")
	public void setYearOfHarvest(String yearOfHarvest) {
		this.yearOfHarvest = yearOfHarvest;
	}

	@JsonProperty("firstNationsName")
	public String getFirstNationName() {
		return firstNationName;
	}

	@JsonProperty("firstNationsName")
	public void setFirstNationName(String firstNationName) {
		this.firstNationName = firstNationName;
	}

	@JsonProperty("generalLocation")
	public String getGeneralLocation() {
		return generalLocation;
	}

	@JsonProperty("generalLocation")
	public void setGeneralLocation(String generalLocation) {
		this.generalLocation = generalLocation;
	}

	@JsonProperty("targeted")
	public String getTargeted() {
		return targeted;
	}

	@JsonProperty("targeted")
	public void setTargeted(String targeted) {
		this.targeted = targeted;
	}

	@JsonProperty("Contact1Name")
	public String getContact1Name() {
		return contact1Name;
	}

	@JsonProperty("Contact1Name")
	public void setContact1Name(String contact1Name) {
		this.contact1Name = contact1Name;
	}

	@JsonProperty("Contact1Date")
	public String getContact1Date() {
		return contact1Date;
	}

	@JsonProperty("Contact1Date")
	public void setContact1Date(String contact1Date) {
		this.contact1Date = contact1Date;
	}

	@JsonProperty("Contact1Contacted")
	public String getContact1Contacted() {
		return contact1Contacted;
	}

	@JsonProperty("Contact1Contacted")
	public void setContact1Contacted(String contact1Contacted) {
		this.contact1Contacted = contact1Contacted;
	}

	@JsonProperty("Contact2Name")
	public String getContact2Name() {
		return contact2Name;
	}

	@JsonProperty("Contact2Name")
	public void setContact2Name(String contact2Name) {
		this.contact2Name = contact2Name;
	}

	@JsonProperty("Contact2Date")
	public String getContact2Date() {
		return contact2Date;
	}

	@JsonProperty("Contact2Date")
	public void setContact2Date(String contact2Date) {
		this.contact2Date = contact2Date;
	}

	@JsonProperty("Contact2Contacted")
	public String getContact2Contacted() {
		return contact2Contacted;
	}

	@JsonProperty("Contact2Contacted")
	public void setContact2Contacted(String contact2Contacted) {
		this.contact2Contacted = contact2Contacted;
	}

	@JsonProperty("Contact3Name")
	public String getContact3Name() {
		return contact3Name;
	}

	@JsonProperty("Contact3Name")
	public void setContact3Name(String contact3Name) {
		this.contact3Name = contact3Name;
	}

	@JsonProperty("Contact3Date")
	public String getContact3Date() {
		return contact3Date;
	}

	@JsonProperty("Contact3Date")
	public void setContact3Date(String contact3Date) {
		this.contact3Date = contact3Date;
	}

	@JsonProperty("Contact3Contacted")
	public String getContact3Contacted() {
		return contact3Contacted;
	}

	@JsonProperty("Contact3Contacted")
	public void setContact3Contacted(String contact3Contacted) {
		this.contact3Contacted = contact3Contacted;
	}

	@JsonProperty("FeatureID1Description")
	public String getFeatureID1Description() {
		return featureID1Description;
	}

	@JsonProperty("FeatureID1Description")
	public void setFeatureID1Description(String featureID1Description) {
		this.featureID1Description = featureID1Description;
	}

	@JsonProperty("FeatureID1Source")
	public String getFeatureID1Source() {
		return featureID1Source;
	}

	@JsonProperty("FeatureID1Source")
	public void setFeatureID1Source(String featureID1Source) {
		this.featureID1Source = featureID1Source;
	}

	@JsonProperty("FeatureID2Description")
	public String getFeatureID2Description() {
		return featureID2Description;
	}

	@JsonProperty("FeatureID2Description")
	public void setFeatureID2Description(String featureID2Description) {
		this.featureID2Description = featureID2Description;
	}

	@JsonProperty("FeatureID2Source")
	public String getFeatureID2Source() {
		return featureID2Source;
	}

	@JsonProperty("FeatureID2Source")
	public void setFeatureID2Source(String featureID2Source) {
		this.featureID2Source = featureID2Source;
	}

	@JsonProperty("FeatureID2Comments")
	public String getFeatureID2Comments() {
		return featureID2Comments;
	}

	@JsonProperty("FeatureID2Comments")
	public void setFeatureID2Comments(String featureID2Comments) {
		this.featureID2Comments = featureID2Comments;
	}

	@JsonProperty("FeatureID3Description")
	public String getFeatureID3Description() {
		return featureID3Description;
	}

	@JsonProperty("FeatureID3Description")
	public void setFeatureID3Description(String featureID3Description) {
		this.featureID3Description = featureID3Description;
	}

	@JsonProperty("FeatureID3Source")
	public String getFeatureID3Source() {
		return featureID3Source;
	}

	@JsonProperty("FeatureID3Source")
	public void setFeatureID3Source(String featureID3Source) {
		this.featureID3Source = featureID3Source;
	}

	@JsonProperty("FeatureID4Description")
	public String getFeatureID4Description() {
		return featureID4Description;
	}

	@JsonProperty("FeatureID4Description")
	public void setFeatureID4Description(String featureID4Description) {
		this.featureID4Description = featureID4Description;
	}

	@JsonProperty("FeatureID4Source")
	public String getFeatureID4Source() {
		return featureID4Source;
	}

	@JsonProperty("FeatureID4Source")
	public void setFeatureID4Source(String featureID4Source) {
		this.featureID4Source = featureID4Source;
	}

	@JsonProperty("FeatureID4Comments")
	public String getFeatureID4Comments() {
		return featureID4Comments;
	}

	@JsonProperty("FeatureID4Comments")
	public void setFeatureID4Comments(String featureID4Comments) {
		this.featureID4Comments = featureID4Comments;
	}

	@JsonProperty("FeatureID5Description")
	public String getFeatureID5Description() {
		return featureID5Description;
	}

	@JsonProperty("FeatureID5Description")
	public void setFeatureID5Description(String featureID5Description) {
		this.featureID5Description = featureID5Description;
	}

	@JsonProperty("FeatureID5Comments")
	public String getFeatureID5Comments() {
		return featureID5Comments;
	}

	@JsonProperty("FeatureID5Comments")
	public void setFeatureID5Comments(String featureID5Comments) {
		this.featureID5Comments = featureID5Comments;
	}

	@JsonProperty("q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock")
	public String getQ8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock() {
		return q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock;
	}

	@JsonProperty("q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock")
	public void setQ8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock(
			String q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock) {
		this.q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock = q8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock;
	}

	@JsonProperty("Q1Comments")
	public String getQ1Comments() {
		return q1Comments;
	}

	@JsonProperty("Q1Comments")
	public void setQ1Comments(String q1Comments) {
		this.q1Comments = q1Comments;
	}

	@JsonProperty("q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues")
	public String getQ9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues() {
		return q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues;
	}

	@JsonProperty("q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues")
	public void setQ9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues(
			String q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues) {
		this.q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues = q9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues;
	}

	@JsonProperty("Q2Comments")
	public String getQ2Comments() {
		return q2Comments;
	}

	@JsonProperty("Q2Comments")
	public void setQ2Comments(String q2Comments) {
		this.q2Comments = q2Comments;
	}

	@JsonProperty("q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock")
	public String getQ10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock() {
		return q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock;
	}

	@JsonProperty("q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock")
	public void setQ10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock(
			String q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock) {
		this.q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock = q10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock;
	}

	@JsonProperty("Q3Comments")
	public String getQ3Comments() {
		return q3Comments;
	}

	@JsonProperty("Q3Comments")
	public void setQ3Comments(String q3Comments) {
		this.q3Comments = q3Comments;
	}

	@JsonProperty("ratingRationale")
	public String getRatingRationale() {
		return ratingRationale;
	}

	@JsonProperty("ratingRationale")
	public void setRatingRationale(String ratingRationale) {
		this.ratingRationale = ratingRationale;
	}

	@JsonProperty("Q4Comments")
	public String getQ4Comments() {
		return q4Comments;
	}

	@JsonProperty("Q4Comments")
	public void setQ4Comments(String q4Comments) {
		this.q4Comments = q4Comments;
	}

	@JsonProperty("AdditionalCommentsonFormC")
	public String getAdditionalCommentsonFormC() {
		return additionalCommentsonFormC;
	}

	@JsonProperty("AdditionalCommentsonFormC")
	public void setAdditionalCommentsonFormC(String additionalCommentsonFormC) {
		this.additionalCommentsonFormC = additionalCommentsonFormC;
	}

	@JsonProperty("CommentsBlock")
	public String getCommentsBlock() {
		return commentsBlock;
	}

	@JsonProperty("CommentsBlock")
	public void setCommentsBlock(String commentsBlock) {
		this.commentsBlock = commentsBlock;
	}

	@JsonProperty("CommentsFeature")
	public String getCommentsFeature() {
		return commentsFeature;
	}

	@JsonProperty("CommentsFeature")
	public void setCommentsFeature(String commentsFeature) {
		this.commentsFeature = commentsFeature;
	}

	@JsonProperty("CommentsFeature1")
	public String getCommentsFeature1() {
		return commentsFeature1;
	}

	@JsonProperty("CommentsFeature1")
	public void setCommentsFeature1(String commentsFeature1) {
		this.commentsFeature1 = commentsFeature1;
	}

	@JsonProperty("CommentsGeneral")
	public String getCommentsGeneral() {
		return commentsGeneral;
	}

	@JsonProperty("CommentsGeneral")
	public void setCommentsGeneral(String commentsGeneral) {
		this.commentsGeneral = commentsGeneral;
	}

	@JsonProperty("Comments")
	public String getComments() {
		return comments;
	}

	@JsonProperty("Comments")
	public void setComments(String comments) {
		this.comments = comments;
	}

	@JsonProperty("features")
	public List<Feature> getFeatures() {
		return features;
	}

	@JsonProperty("features")
	public void setFeatures(List<Feature> features) {
		this.features = features;
	}

	@JsonProperty("pictures")
	public List<Picture> getPictures() {
		return pictures;
	}

	@JsonProperty("pictures")
	public void setPictures(List<Picture> pictures) {
		this.pictures = pictures;
	}

	@JsonProperty("contacts")
	public List<Contact> getContacts() {
		return contacts;
	}

	@JsonProperty("contacts")
	public void setContacts(List<Contact> contacts) {
		this.contacts = contacts;
	}

	@JsonProperty("status")
	public String getStatus() {
		return status;
	}

	@JsonProperty("status")
	public void setStatus(String status) {
		this.status = status;
	}

	@JsonProperty("offline")
	public String getOffline() {
		return offline;
	}

	@JsonProperty("offline")
	public void setOffline(String offline) {
		this.offline = offline;
	}

	@JsonProperty("Modified")
	public String getModified() {
		return modified;
	}

	@JsonProperty("Modified")
	public void setModified(String modified) {
		this.modified = modified;
	}

	@JsonProperty("SaveOffline")
	public String getSaveOffline() {
		return saveOffline;
	}

	@JsonProperty("SaveOffline")
	public void setSaveOffline(String saveOffline) {
		this.saveOffline = saveOffline;
	}

	@JsonProperty("Source")
	public String getSource() {
		return source;
	}

	@JsonProperty("Source")
	public void setSource(String source) {
		this.source = source;
	}

	@JsonProperty("Description")
	public String getDescription() {
		return description;
	}

	@JsonProperty("Description")
	public void setDescription(String description) {
		this.description = description;
	}

	@JsonProperty("q8Comments")
	public String getQ8Comments() {
		return q8Comments;
	}

	@JsonProperty("q8Comments")
	public void setQ8Comments(String q8Comments) {
		this.q8Comments = q8Comments;
	}

	@JsonProperty("q9Comments")
	public String getQ9Comments() {
		return q9Comments;
	}

	@JsonProperty("q9Comments")
	public void setQ9Comments(String q9Comments) {
		this.q9Comments = q9Comments;
	}

	@JsonProperty("q10Comments")
	public String getQ10Comments() {
		return q10Comments;
	}

	@JsonProperty("q10Comments")
	public void setQ10Comments(String q10Comments) {
		this.q10Comments = q10Comments;
	}

	@JsonProperty("FirstNationsName")
	public String getFirstNationsName() {
		return firstNationsName;
	}

	@JsonProperty("rating")
	public String getRating() {
		return rating;
	}

	@JsonProperty("rating")
	public void setRating(String rating) {
		this.rating = rating;
	}

	@JsonProperty("FirstNationsName")
	public void setFirstNationsName(String firstNationsName) {
		this.firstNationsName = firstNationsName;
	}

	@JsonProperty("Inreserve")
	public String getInreserve() {
		return inreserve;
	}

	@JsonProperty("Inreserve")
	public void setInreserve(String inreserve) {
		this.inreserve = inreserve;
	}

	@JsonProperty("ListAdd")
	public String getListAdd() {
		return listAdd;
	}

	@JsonProperty("ListAdd")
	public void setListAdd(String listAdd) {
		this.listAdd = listAdd;
	}

	@JsonProperty("FirsNations")
	public List<Object> getFirsNations() {
		return firsNations;
	}

	@JsonProperty("FirsNations")
	public void setFirsNations(List<Object> firsNations) {
		this.firsNations = firsNations;
	}

	@JsonProperty("Add")
	public String getAdd() {
		return add;
	}

	@JsonProperty("Add")
	public void setAdd(String add) {
		this.add = add;
	}

	@JsonProperty("Commentaires")
	public String getCommentaires() {
		return commentaires;
	}

	@JsonProperty("Commentaires")
	public void setCommentaires(String commentaires) {
		this.commentaires = commentaires;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	@JsonProperty("downloadedBy")
	public String getDownloadedBy() {
		return downloadedBy;
	}

	@JsonProperty("downloadedBy")
	public void setDownloadedBy(String downloadedBy) {
		this.downloadedBy = downloadedBy;
	}

	@JsonProperty("downloadedDate")
	public String getDownloadedDate() {
		return downloadedDate;
	}

	@JsonProperty("downloadedDate")
	public void setDownloadedDate(String downloadedDate) {
		this.downloadedDate = downloadedDate;
	}

	@JsonProperty("updateUserid")
	public String getUpdateUserid() {
		return updateUserid;
	}

	@JsonProperty("updateUserid")
	public void setUpdateUserid(String updateUserid) {
		this.updateUserid = updateUserid;
	}

	@JsonProperty("updateTimestamp")
	public String getUpdateTimestamp() {
		return updateTimestamp;
	}

	@JsonProperty("updateTimestamp")
	public void setUpdateTimestamp(String updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	@JsonProperty("deviceCheckoutGuid")
	public String getDeviceCheckoutGuid() {
		return deviceCheckoutGuid;
	}

	@JsonProperty("deviceCheckoutGuid")
	public void setDeviceCheckoutGuid(String deviceCheckoutGuid) {
		this.deviceCheckoutGuid = deviceCheckoutGuid;
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

	@JsonProperty("labels")
	public List<Label> getLabels() {
		return labels;
	}

	@JsonProperty("labels")
	public void setLabels(List<Label> labels) {
		this.labels = labels;
	}

	public String getRevisionCount() {
		return revisionCount;
	}

	public void setRevisionCount(String revisionCount) {
		this.revisionCount = revisionCount;
	}

	@JsonProperty("mrvaRatingCode")
	public String getMrvaRatingCode() {
		return mrvaRatingCode;
	}

	@JsonProperty("mrvaRatingCode")
	public void setMrvaRatingCode(String mrvaRatingCode) {
		this.mrvaRatingCode = mrvaRatingCode;
	}

}
