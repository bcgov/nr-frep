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
@JsonPropertyOrder({
	    // Feature 1,2,3
	    "id", "featureLabel", "compositeFeatureInd", "featureDescriptionCode", "featureInfoSourceCode", "associatedFeatures", "featureComment", "featureDescription", "compositeFeature",
	    // Feature 4
	    "culturaltraildesignated", "burialSite", "nest", "culturaltrailundesignated", "cremationSite", "den", "traditionalUseSite", "ceremonialSite", "caveorotherKarst",
	    "spiritualSite", "cedarBarkStriparea", "rockOutcrop", "culturalDepression", "lithics", "ofCMTs", "ofCMTsNumber", "ofMonumentalCedars", "standofMonumentalCedar",
	    "individualMonumentalCedar", "other",
	    "otherdescription", "widthofFeature", "lengthofFeature", "areaofFeature", "chrRegisteredSite", "borden",
	    // Feature 5
	    "inharvestedarea", "adjacenttoblock", "adjacenttowater", "locationother", "locationotherdescription", "entirecutblock", "Inreserve", "Reservetype",
	    // Feature 6

	    // Feature 7
	    "managementStrategyFN", "sitepermitIssued", "managementStrategySP", "permit",
	    "bufferLengthFN","bufferLengthAIA","bufferLengthSP", "retaininharvestareaFN", "retaininharvestareaAIA", "retaininharvestareaSP",
	    "permanentReserveSP", "stubnonCMtsaIA", "stubCMtsabovescarFN", "stubCMtsabovescarAIA", "stubCMtsabovescarSP",
	    "leavestandingFN", "leavestandingAIA", "leavestandingSP",

	    // Feature 8
	    "unabletoLocate", "Modifiedblockboundary", "partiallyconservedinpermanentreserve", "partiallyconservedinpermanentreserveType", "forCompositeFeaturesInd",
	    "individualCMT",
	    "CommentsFeature", "checklistID", "FREPAssessment", "District", "tsa",
		"openingID", "siteorFeatureDescription",
		"post1846", "AdditionalCommentsonFormC",
		"noManagement", "q1isthereevidenceofdamagetothesiteorfeature",
		"q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse", "windthrow", "trailfeatures",
		"Q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature",
		"q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective",
		"q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature",
		"q6description",
		"Q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable",
		"Q7Description", "Description", "Source", "Pictures", "Status", "AssoFeatures", "OtherMP",
		"fullyconservedinpermanentreserveType", "partiallytemporaryreserve", "partiallytemporaryreservetype",
		"fullyconservedinpermanentreserve", "fullytemporaryreserve", "conserveRotationalReserveTypeFN", "conserveRotationalReserveTypeAIA", "conserveRotationalReserveTypeSP",
		"temporaryRetentionTypeFN", "temporaryRetentionTypeAIA", "temporaryRetentionTypeSP", "locationReservetype",
		"easting", "northing", "utmZone",
		"pre1846", "ageUnknown", "historicalUse",
		"modifyBlockBoundaryFN",
		"modifyBlockBoundaryAIA", "noManagementStrategyAIA", "retainBufferSP", "retainBufferAIA", "retainBufferFN",
		"crownorstandmodificationFN", "conserveinRotationalReserveFN",
		"permanentReserveFN", "permanentReserveAIA", "conserveinRotationalReserveAIA", "crownorstandmodificationAIA",
		"crownorstandmodificationSP",
		"conserveinRotationalReserveSP", "altersilvicultureSP",
		// TODO remove altersilviculturexx once we know for sure...
		"avoidSilvAvoidPlanting", "avoidSilvAvoidPlantingFN", "avoidSilvAvoidPlantingAIA", "avoidSilvAvoidPlantingSP", "avoidSilvAvoidSitePrep", "avoidSilvAvoidSitePrepFN", "avoidSilvAvoidSitePrepAIA", "avoidSilvAvoidSitePrepSP",
		"machineFreeZoneFN", "machineFreeZoneAIA", "machineFreeZoneSP", "harvestUnderSapFN", "harvestUnderSapAIA", "harvestUnderSapSP", "winterHarvestFrozenGroundFN", "winterHarvestFrozenGroundAIA", "winterHarvestFrozenGroundSP",
		"machineFreeZone", "harvestUnderSap", "winterHarvestFrozenGround",
		"stubnonCMTsSP", "datetheFeatureSP", "datetheFeatureAIA",
		"altersilvicultureAIA", "altersilvicultureFN",
		"stubnonCMTsFN", "datetheFeatureFN",
		"fullyconservedintemporaryreserve", "Permanentreservetype",
		"retainedinharvestareanobuffer", "compledcrownorstandmodification", "datedthefeature", "leftStanding",
		"otherActivities", "stubbed", "stubbedNon", "retainabuffer", "alteredsilviculture",
		"q1isthereevidenceofdamagetothesiteorfeature", "Harvestingq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"silvicultureq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"fireq2Wheredamagehasoccurredwhatisthemostlikelycause", "roadq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"windthrowq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"otherq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"livestockq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"IndustrialUseq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"recreationq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"safetyq2Wheredamagehasoccurredwhatisthemostlikelycause", "descriptionofdamage",
		"windthrowManagement", "estwindthrow",
		"windthrowTechniquenone", "otherTechnique", "windthrowTechniqueFeathering", "windthrowTechniqueRetentionBuffer",
		"windthrowTechniquePruning", "windthrowTechniqueTopping", "ifotherpleasedescribe", "canthetrailstillbelocated",
		"hasthetrailbeenmadelesspassble", "isthereevidenceofdamage", "traillength",
		"q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature",
		"q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective", "q5description",
		"featureRating",
		"featureRatingRationale",
		"SFDESCRIPTION", "Ifotherpleasedescribeotherq2Wheredamagehasoccurredwhatisthemostlikelycause",
		"q4description" })
public class Feature{

	@JsonProperty("compositeFeatureInd")
	private String compositeFeatureInd;
	@JsonProperty("featureLabel")
	private String featureLabel;
	@JsonProperty("compositeFeature")
	private String compositeFeature;
	@JsonProperty("associatedFeatures")
	private String[] associatedFeatures;
	@JsonProperty("featureDescriptionCode")
	private String featureDescriptionCode;
	@JsonProperty("featureInfoSourceCode")
	private String featureInfoSourceCode;
	@JsonProperty("Reservetype")
	private String reserveType;
	@JsonProperty("bufferWidthMeter")
	private String bufferWidthMeter;
	@JsonProperty("forCompositeFeaturesInd")
	private String forCompositeFeaturesInd;

	@JsonProperty("locationotherdescription")
	private String locationOtherDescription;
	@JsonProperty("checklistID")
	private String checklistID;
	@JsonProperty("FREPAssessment")
	private String fREPAssessment;
	@JsonProperty("District")
	private String district;
	@JsonProperty("tsa")
	private String tsa;
	@JsonProperty("openingID")
	private String openingID;
	@JsonProperty("FeatureID")
	private Integer featureID;
	@JsonProperty("siteorFeatureDescription")
	private String siteorFeatureDescription;
	@JsonProperty("den")
	private String den;
	@JsonProperty("featureComment")
	private String featureComment;
	@JsonProperty("featureDescription")
	private String featureDescription;




	@JsonProperty("lengthofFeature")
	private String lengthofFeature;
	@JsonProperty("chrRegisteredSite")
	private String chrRegisteredSite;
	@JsonProperty("post1846")
	private String post1846 = "false";
	@JsonProperty("AdditionalCommentsonFormC")
	private String additionalCommentsonFormC;
	@JsonProperty("noManagement")
	private String noManagement;
	@JsonProperty("q1isthereevidenceofdamagetothesiteorfeature")
	private String q1Isthereevidenceofdamagetothesiteorfeature;
	@JsonProperty("q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse")
	private String q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse;
	@JsonProperty("windthrow")
	private String windthrow;
	@JsonProperty("trailfeatures")
	private String trailfeatures;

	@JsonProperty("Description")
	private String description;
	@JsonProperty("Source")
	private String source;
	@JsonProperty("Pictures")
	private List<Picture> pictures = null;
	@JsonProperty("Status")
	private String status;
	@JsonProperty("AssoFeatures")
	private List<Integer> assoFeatures = null;
	@JsonProperty("otherPlannedManagementStrategy")
	private List<OtherPlannedManagementStrategy> otherPlannedManagementStrategy = new ArrayList<OtherPlannedManagementStrategy>();
	@JsonProperty("partiallyconservedinpermanentreserve")
	private String partiallyconservedinpermanentreserve;
	@JsonProperty("partiallyconservedinpermanentreserveType")
	private String partiallyconservedinpermanentreserveType;
	@JsonProperty("fullyconservedinpermanentreserveType")
	private String fullyconservedinpermanentreserveType;
	@JsonProperty("partiallytemporaryreserve")
	private String partiallytemporaryreserve;
	@JsonProperty("partiallytemporaryreservetype")
	private String partiallytemporaryreservetype;
	@JsonProperty("fullyconservedinpermanentreserve")
	private String fullyconservedinpermanentreserve;
	@JsonProperty("fullytemporaryreserve")
	private String fullytemporaryreserve;
	@JsonProperty("conserveRotationalReserveTypeFN")
	private String conserveRotationalReserveTypeFN;
	@JsonProperty("conserveRotationalReserveTypeAIA")
	private String conserveRotationalReserveTypeAIA;
	@JsonProperty("conserveRotationalReserveTypeSP")
	private String conserveRotationalReserveTypeSP;
	@JsonProperty("temporaryRetentionTypeFN")
	private String temporaryRetentionTypeFN;
	@JsonProperty("temporaryRetentionTypeAIA")
	private String temporaryRetentionTypeAIA;
	@JsonProperty("temporaryRetentionTypeSP")
	private String temporaryRetentionTypeSP;
	@JsonProperty("locationReservetype")
	private String locationReservetype;
	@JsonProperty("ofCMTs")
	private String ofCMTs;
	@JsonProperty("ofCMTsNumber")
	private String ofCMTsNumber;
	@JsonProperty("standofMonumentalCedar")
	private String standofMonumentalCedar;
	@JsonProperty("lithics")
	private String lithics;
	@JsonProperty("other")
	private String other;
	@JsonProperty("otherdescription")
	private String otherDescription;
	@JsonProperty("caveorotherKarst")
	private String caveorotherKarst;
	@JsonProperty("nest")
	private String nest;
	@JsonProperty("rockOutcrop")
	private String rockOutcrop;
	@JsonProperty("individualMonumentalCedar")
	private String individualMonumentalCedar;
	@JsonProperty("cedarBarkStriparea")
	private String cedarBarkStripArea;
	@JsonProperty("ceremonialSite")
	private String ceremonialSite;
	@JsonProperty("cremationSite")
	private String cremationSite;
	@JsonProperty("burialSite")
	private String burialSite;
	@JsonProperty("culturaltraildesignated")
	private String culturalTraildesignated;
	@JsonProperty("culturaltrailundesignated")
	private String culturalTrailundesignated;
	@JsonProperty("individualCMT")
	private String individualCMT;
	@JsonProperty("traditionalUseSite")
	private String traditionalUseSite;
	@JsonProperty("spiritualSite")
	private String spiritualSite;
	@JsonProperty("ofMonumentalCedars")
	private String ofMonumentalCedars;
	@JsonProperty("culturalDepression")
	private String culturalDepression;
	@JsonProperty("widthofFeature")
	private String widthofFeature;
	@JsonProperty("areaofFeature")
	private String areaofFeature;
	@JsonProperty("borden")
	private String borden;
	@JsonProperty("easting")
	private String easting;
	@JsonProperty("northing")
	private String northing;
	@JsonProperty("utmZone")
	private String utmZone;
	@JsonProperty("inharvestedarea")
	private String inharvestedarea;
	@JsonProperty("entirecutblock")
	private String entirecutblock;
	@JsonProperty("adjacenttoblock")
	private String adjacenttoblock;
	@JsonProperty("adjacenttowater")
	private String adjacenttowater;
	@JsonProperty("locationother")
	private String locationOther;
	@JsonProperty("Inreserve")
	private String inReserve;
	@JsonProperty("pre1846")
	private String pre1846;
	@JsonProperty("ageUnknown")
	private String ageUnknown;
	@JsonProperty("historicalUse")
	private String historicalUse;
	@JsonProperty("permit")
	private String permit;
	@JsonProperty("sitepermitIssued")
	private String sitePermitIssued;
	@JsonProperty("managementStrategySP")
	private String managementStrategySP;
	@JsonProperty("managementStrategyFN")
	private String managementStrategyFN;
	@JsonProperty("noManagementStrategyAIA")
	private String noManagementStrategyAIA;
	@JsonProperty("modifyBlockBoundaryFN")
	private String modifyBlockBoundaryFN;
	@JsonProperty("modifyBlockBoundaryAIA")
	private String modifyBlockBoundaryAIA;
	@JsonProperty("modifyBlockBoundarySP")
	private String modifyBlockBoundarySP;
	@JsonProperty("retainBufferSP")
	private String retainBufferSP;
	@JsonProperty("retainBufferAIA")
	private String retainBufferAIA;
	@JsonProperty("retainBufferFN")
	private String retainBufferFN;
	@JsonProperty("bufferLengthFN")
	private String bufferLengthFN;
	@JsonProperty("bufferLengthAIA")
	private String bufferLengthAIA;
	@JsonProperty("bufferLengthSP")
	private String bufferLengthSP;
	@JsonProperty("retaininharvestareaFN")
	private String retaininHarvestAreaFN;
	@JsonProperty("crownorstandmodificationFN")
	private String crownorstandmodificationFN;
	@JsonProperty("conserveinRotationalReserveFN")
	private String conserveinRotationalReserveFN;
	@JsonProperty("permanentReserveFN")
	private String permanentReserveFN;
	@JsonProperty("permanentReserveAIA")
	private String permanentReserveAIA;
	@JsonProperty("conserveinRotationalReserveAIA")
	private String conserveinRotationalReserveAIA;
	@JsonProperty("crownorstandmodificationAIA")
	private String crownorstandmodificationAIA;
	@JsonProperty("retaininharvestareaAIA")
	private String retaininHarvestAreaAIA;
	@JsonProperty("retaininharvestareaSP")
	private String retaininHarvestAreaSP;
	@JsonProperty("crownorstandmodificationSP")
	private String crownorstandmodificationSP;
	@JsonProperty("conserveinRotationalReserveSP")
	private String conserveinRotationalReserveSP;
	@JsonProperty("permanentReserveSP")
	private String permanentReserveSP;
	@JsonProperty("altersilvicultureSP")
	private String altersilvicultureSP;
	@JsonProperty("leavestandingSP")
	private String leaveStandingSP;
	@JsonProperty("stubnonCMTsSP")
	private String stubnonCMTsSP;
	@JsonProperty("stubCMtsabovescarSP")
	private String stubCMTsabovescarSP;
	@JsonProperty("datetheFeatureSP")
	private String datetheFeatureSP;
	@JsonProperty("datetheFeatureAIA")
	private String datetheFeatureAIA;
	@JsonProperty("stubCMtsabovescarAIA")
	private String stubCMTsabovescarAIA;
	@JsonProperty("stubnonCMtsaIA")
	private String stubnonCMTsAIA;
	@JsonProperty("leavestandingAIA")
	private String leaveStandingAIA;
	@JsonProperty("altersilvicultureAIA")
	private String altersilvicultureAIA;
	@JsonProperty("altersilvicultureFN")
	private String altersilvicultureFN;

	@JsonProperty("avoidSilvAvoidPlanting")
	private String avoidSilvAvoidPlanting;
	@JsonProperty("avoidSilvAvoidPlantingFN")
	private String avoidSilvAvoidPlantingFN;
	@JsonProperty("avoidSilvAvoidPlantingAIA")
	private String avoidSilvAvoidPlantingAIA;
	@JsonProperty("avoidSilvAvoidPlantingSP")
	private String avoidSilvAvoidPlantingSP;
	@JsonProperty("avoidSilvAvoidSitePrep")
	private String avoidSilvAvoidSitePrep;
	@JsonProperty("avoidSilvAvoidSitePrepFN")
	private String avoidSilvAvoidSitePrepFN;
	@JsonProperty("avoidSilvAvoidSitePrepAIA")
	private String avoidSilvAvoidSitePrepAIA;
	@JsonProperty("avoidSilvAvoidSitePrepSP")
	private String avoidSilvAvoidSitePrepSP;

	@JsonProperty("machineFreeZoneFN")
	private String machineFreeZoneFN;
	@JsonProperty("machineFreeZoneAIA")
	private String machineFreeZoneAIA;
	@JsonProperty("machineFreeZoneSP")
	private String machineFreeZoneSP;
	@JsonProperty("harvestUnderSapFN")
	private String harvestUnderSapFN;
	@JsonProperty("harvestUnderSapAIA")
	private String harvestUnderSapAIA;
	@JsonProperty("harvestUnderSapSP")
	private String harvestUnderSapSP;
	@JsonProperty("winterHarvestFrozenGroundFN")
	private String winterHarvestFrozenGroundFN;
	@JsonProperty("winterHarvestFrozenGroundAIA")
	private String winterHarvestFrozenGroundAIA;
	@JsonProperty("winterHarvestFrozenGroundSP")
	private String winterHarvestFrozenGroundSP;
	@JsonProperty("machineFreeZone")
	private String machineFreeZone;
	@JsonProperty("harvestUnderSap")
	private String harvestUnderSap;
	@JsonProperty("winterHarvestFrozenGround")
	private String winterHarvestFrozenGround;

	@JsonProperty("leavestandingFN")
	private String leaveStandingFN;
	@JsonProperty("stubnonCMTsFN")
	private String stubnonCMTsFN;
	@JsonProperty("datetheFeatureFN")
	private String datetheFeatureFN;
	@JsonProperty("stubCMtsabovescarFN")
	private String stubCMTsabovescarFN;
	@JsonProperty("unabletoLocate")
	private String unabletoLocate;
	@JsonProperty("fullyconservedintemporaryreserve")
	private String fullyconservedintemporaryreserve;
	@JsonProperty("Permanentreservetype")
	private String permanentreservetype;
	@JsonProperty("Modifiedblockboundary")
	private String modifiedblockboundary;
	@JsonProperty("retainedinharvestareanobuffer")
	private String retainedinharvestareanobuffer;
	@JsonProperty("compledcrownorstandmodification")
	private String compledCrownorstandmodification;
	@JsonProperty("datedthefeature")
	private String datedthefeature;
	@JsonProperty("leftStanding")
	private String leftStanding;
	@JsonProperty("otherActivities")
	private String otherActivities;
	@JsonProperty("stubbed")
	private String stubbed;
	@JsonProperty("stubbedNon")
	private String stubbedNon;
	@JsonProperty("retainabuffer")
	private String retainabuffer;
	@JsonProperty("bufferLengthManageEffectiveness")
	private String bufferLengthManageEffectiveness;
	@JsonProperty("alteredsilviculture")
	private String alteredsilviculture;
	@JsonProperty("Harvestingq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("silvicultureq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("fireq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String fireQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("roadq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String roadQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("windthrowq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("otherq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String otherQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("livestockq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("IndustrialUseq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("recreationq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("safetyq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("descriptionofdamage")
	private String descriptionofdamage;
	@JsonProperty("windthrowManagement")
	private String windthrowManagement;
	@JsonProperty("estwindthrow")
	private String estwindthrow;
	@JsonProperty("windthrowTechniquenone")
	private String windthrowTechniqueNone;
	@JsonProperty("otherTechnique")
	private String otherTechnique;
	@JsonProperty("windthrowTechniqueFeathering")
	private String windthrowTechniqueFeathering;
	@JsonProperty("windthrowTechniqueRetentionBuffer")
	private String windthrowTechniqueRetentionBuffer;
	@JsonProperty("windthrowTechniquePruning")
	private String windthrowTechniquePruning;
	@JsonProperty("windthrowTechniqueTopping")
	private String windthrowTechniqueTopping;
	@JsonProperty("ifotherpleasedescribe")
	private String ifotherpleasedescribe;
	@JsonProperty("canthetrailstillbelocated")
	private String canthetrailstillbelocated;
	@JsonProperty("hasthetrailbeenmadelesspassble")
	private String hasthetrailbeenmadelesspassble;
	@JsonProperty("isthereevidenceofdamage")
	private String isthereevidenceofdamage;
	@JsonProperty("traillength")
	private String trailLength;

	@JsonProperty("Q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature")
	@Deprecated
	private String q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature;
	@JsonProperty("q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective")
	private String q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective;
	@JsonProperty("q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature")
	private String q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature;
	@JsonProperty("q6description")
	private String q6Description;
	@JsonProperty("Q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable")
	@Deprecated
	private String q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable;
	@JsonProperty("Q7Description")
	private String q7Description;

	@JsonProperty("q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature")
	private String q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature;
	@JsonProperty("q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective")
	@Deprecated
	private String q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective;
	@JsonProperty("q5description")
	private String q5Description;
	@JsonProperty("featureRating")
	private String featureRating;
	@JsonProperty("featureRatingRationale")
	private String featureRatingRationale;
	@JsonProperty("id")
	private String id;
	@JsonProperty("SFDESCRIPTION")
	private String sFDESCRIPTION;
	@JsonProperty("Ifotherpleasedescribeotherq2Wheredamagehasoccurredwhatisthemostlikelycause")
	private String ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	@JsonProperty("q4description")
	private String q4Description;

	@JsonProperty("isOtherPlannedManagedStrategyFN")
	private String isOtherPlannedManagedStrategyFN;
	@JsonProperty("isOtherPlannedManagedStrategySP")
	private String isOtherPlannedManagedStrategySP;
	@JsonProperty("isOtherPlannedManagedStrategyAIA")
	private String isOtherPlannedManagedStrategyAIA;

	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	public Feature() {
		// Default all indicators to false.
		this.compositeFeatureInd = "false";
		this.forCompositeFeaturesInd = "false";
		this.culturalTraildesignated = "false";
		this.burialSite = "false";
		this.nest = "false";
		this.culturalTrailundesignated = "false";
		this.cremationSite = "false";
		this.den = "false";
		this.traditionalUseSite = "false";
	    this.ceremonialSite = "false";
	    this.caveorotherKarst = "false";
	    this.spiritualSite = "false";
	    this.cedarBarkStripArea = "false";
	    this.rockOutcrop = "false";
	    this.culturalDepression = "false";
	    this.lithics = "false";
	    this.ofCMTs = "false";
	    this.ofMonumentalCedars = "false";
	    this.individualMonumentalCedar = "false";
	    this.other = "false";
		this.locationOther = "false";
		this.chrRegisteredSite = "false";
		this.inharvestedarea = "false";
		this.post1846 = "false";
		this.q1Isthereevidenceofdamagetothesiteorfeature = "false";
		this.q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse = "false";
		this.windthrow = "false";
		this.trailfeatures = "false";
		this.q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective = "false";
		this.q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature = "false";
		this.adjacenttoblock = "false";
		this.entirecutblock = "false";
		this.adjacenttowater = "false";
		this.pre1846 = "false";
		this.ageUnknown = "false";
		this.historicalUse = "false";
		this.sitePermitIssued = "false";
		this.managementStrategyFN = "false";
		this.modifyBlockBoundaryFN = "false";
		this.modifyBlockBoundaryAIA = "false";
		this.retainBufferSP = "false";
		this.retainBufferAIA = "false";
		this.retainBufferFN = "false";
		this.retaininHarvestAreaFN = "false";
		this.crownorstandmodificationFN = "false";
		this.conserveinRotationalReserveFN = "false";
		this.permanentReserveFN = "false";
		this.permanentReserveAIA = "false";
		this.permanentReserveSP = "false";
		this.conserveinRotationalReserveAIA = "false";
		this.crownorstandmodificationAIA = "false";
		this.retaininHarvestAreaAIA = "false";
		this.retaininHarvestAreaSP = "false";
		this.crownorstandmodificationSP = "false";
		this.conserveinRotationalReserveSP = "false";
		this.altersilvicultureSP = "false";
		this.leaveStandingSP = "false";
		this.stubnonCMTsSP = "false";
		this.stubCMTsabovescarSP = "false";
		this.datetheFeatureSP = "false";
		this.datetheFeatureAIA = "false";
		this.stubCMTsabovescarAIA = "false";
		this.stubnonCMTsAIA = "false";
		this.leaveStandingAIA = "false";
		this.altersilvicultureAIA = "false";
		this.altersilvicultureFN = "false";
		this.leaveStandingFN = "false";
		this.unabletoLocate = "false";
		this.stubnonCMTsFN = "false";
		this.datetheFeatureFN = "false";
		this.stubCMTsabovescarFN = "false";
		this.modifiedblockboundary = "false";
		this.partiallytemporaryreserve = "false";
		this.fullyconservedinpermanentreserve = "false";
		this.retainedinharvestareanobuffer = "false";
		this.compledCrownorstandmodification = "false";
		this.datedthefeature = "false";
		this.leftStanding = "false";
		this.stubbed = "false";
		this.stubbedNon = "false";
		this.retainabuffer = "false";
		this.partiallyconservedinpermanentreserve = "false";
		this.alteredsilviculture = "false";
		this.harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.fireQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.roadQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.otherQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause = "false";
		this.windthrowManagement = "false";
		this.windthrowTechniqueNone = "false";
		this.otherTechnique = "false";
		this.windthrowTechniqueFeathering = "false";
		this.windthrowTechniqueRetentionBuffer = "false";
		this.windthrowTechniquePruning = "false";
		this.windthrowTechniqueTopping = "false";
		this.canthetrailstillbelocated = "false";
		this.hasthetrailbeenmadelesspassble = "false";
		this.isthereevidenceofdamage = "false";
		this.q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature = "false";
		this.inReserve = "false";
		this.modifyBlockBoundarySP = "false";
		this.fullyconservedinpermanentreserve = "false";
		this.fullyconservedintemporaryreserve = "false";
		this.noManagement = "false";
		this.machineFreeZoneFN = "false";
		this.machineFreeZoneAIA = "false";
		this.machineFreeZoneSP = "false";
		this.harvestUnderSapFN = "false";
		this.harvestUnderSapAIA = "false";
		this.harvestUnderSapSP = "false";
		this.winterHarvestFrozenGroundFN = "false";
		this.winterHarvestFrozenGroundAIA = "false";
		this.winterHarvestFrozenGroundSP = "false";
		this.machineFreeZone = "false";
		this.harvestUnderSap = "false";
		this.winterHarvestFrozenGround = "false";
		this.avoidSilvAvoidPlanting = "false";
		this.avoidSilvAvoidPlantingFN = "false";
		this.avoidSilvAvoidPlantingAIA = "false";
		this.avoidSilvAvoidPlantingSP = "false";
		this.avoidSilvAvoidSitePrep = "false";
		this.avoidSilvAvoidSitePrepFN = "false";
		this.avoidSilvAvoidSitePrepAIA = "false";
		this.avoidSilvAvoidSitePrepSP = "false";

	}

	@JsonProperty("compositeFeatureInd")
	public String getCompositeFeatureInd() {
		return compositeFeatureInd;
	}

	@JsonProperty("compositeFeatureInd")
	public void setCompositeFeatureInd(String compositeFeatureInd) {
		this.compositeFeatureInd = compositeFeatureInd;
	}

	@JsonProperty("featureLabel")
	public String getFeatureLabel() {
		return featureLabel;
	}

	@JsonProperty("featureLabel")
	public void setFeatureLabel(String featureLabel) {
		this.featureLabel = featureLabel;
	}

	@JsonProperty("compositeFeature")
	public String getCompositeFeature() {
		return compositeFeature;
	}

	@JsonProperty("compositeFeature")
	public void setCompositeFeature(String compositeFeature) {
		this.compositeFeature = compositeFeature;
	}

	@JsonProperty("associatedFeatures")
	public String[] getAssociatedFeatures() {
		return associatedFeatures;
	}

	@JsonProperty("associatedFeatures")
	public void setAssociatedFeatures(String[] associatedFeatures) {
		this.associatedFeatures = associatedFeatures;
	}

	@JsonProperty("locationother")
	public String getLocationOther() {
		return locationOther;
	}

	@JsonProperty("locationother")
	public void setLocationOther(String locationOther) {
		this.locationOther = locationOther;
	}

	@JsonProperty("locationotherdescription")
	public String getLocationOtherDescription() {
		return locationOtherDescription;
	}

	@JsonProperty("locationotherdescription")
	public void setLocationOtherDescription(String locationOtherDescription) {
		this.locationOtherDescription = locationOtherDescription;
	}

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

	@JsonProperty("District")
	public String getDistrict() {
		return district;
	}

	@JsonProperty("District")
	public void setDistrict(String district) {
		this.district = district;
	}

	@JsonProperty("tsa")
	public String getTsa() {
		return tsa;
	}

	@JsonProperty("tsa")
	public void setTSA(String tSA) {
		this.tsa = tSA;
	}

	@JsonProperty("openingID")
	public String getOpeningID() {
		return openingID;
	}

	@JsonProperty("openingID")
	public void setOpeningID(String openingID) {
		this.openingID = openingID;
	}

	@JsonProperty("siteorFeatureDescription")
	public String getSiteorFeatureDescription() {
		return siteorFeatureDescription;
	}

	@JsonProperty("siteorFeatureDescription")
	public void setSiteorFeatureDescription(String siteorFeatureDescription) {
		this.siteorFeatureDescription = siteorFeatureDescription;
	}

	@JsonProperty("den")
	public String getDen() {
		return den;
	}

	@JsonProperty("den")
	public void setDen(String den) {
		this.den = den;
	}

	@JsonProperty("featureComment")
	public String getFeatureComment() {
		return featureComment;
	}

	@JsonProperty("featureComment")
	public void setFeatureComment(String featureComment) {
		this.featureComment = featureComment;
	}

	@JsonProperty("featureDescription")
	public String getFeatureDescription() {
		return featureDescription;
	}

	@JsonProperty("featureDescription")
	public void setFeatureDescription(String featureDescription) {
		this.featureDescription = featureDescription;
	}

	@JsonProperty("lengthofFeature")
	public String getLengthofFeature() {
		return lengthofFeature;
	}

	@JsonProperty("lengthofFeature")
	public void setLengthofFeature(String lengthofFeature) {
		this.lengthofFeature = lengthofFeature;
	}

	@JsonProperty("chrRegisteredSite")
	public String getChrRegisteredSite() {
		return chrRegisteredSite;
	}

	@JsonProperty("chrRegisteredSite")
	public void setChrRegisteredSite(String chrRegisteredSite) {
		this.chrRegisteredSite = chrRegisteredSite;
	}

	@JsonProperty("inharvestedarea")
	public String getInharvestedarea() {
		return inharvestedarea;
	}

	@JsonProperty("inharvestedarea")
	public void setInharvestedarea(String inharvestedarea) {
		this.inharvestedarea = inharvestedarea;
	}

	@JsonProperty("post1846")
	public String getPost1846() {
		return post1846;
	}

	@JsonProperty("post1846")
	public void setPost1846(String post1846) {
		this.post1846 = post1846;
	}

	@JsonProperty("AdditionalCommentsonFormC")
	public String getAdditionalCommentsonFormC() {
		return additionalCommentsonFormC;
	}

	@JsonProperty("AdditionalCommentsonFormC")
	public void setAdditionalCommentsonFormC(String additionalCommentsonFormC) {
		this.additionalCommentsonFormC = additionalCommentsonFormC;
	}

	@JsonProperty("noManagement")
	public String getNoManagement() {
		return noManagement;
	}

	@JsonProperty("noManagement")
	public void setNoManagement(String noManagement) {
		this.noManagement = noManagement;
	}

	@JsonProperty("q1isthereevidenceofdamagetothesiteorfeature")
	public String getQ1Isthereevidenceofdamagetothesiteorfeature() {
		return q1Isthereevidenceofdamagetothesiteorfeature;
	}

	@JsonProperty("q1isthereevidenceofdamagetothesiteorfeature")
	public void setQ1Isthereevidenceofdamagetothesiteorfeature(String q1Isthereevidenceofdamagetothesiteorfeature) {
		this.q1Isthereevidenceofdamagetothesiteorfeature = q1Isthereevidenceofdamagetothesiteorfeature;
	}

	@JsonProperty("q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse")
	public String getQ3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse() {
		return q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse;
	}

	@JsonProperty("q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse")
	public void setQ3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse(
			String q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse) {
		this.q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse = q3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse;
	}

	@JsonProperty("windthrow")
	public String getWindthrow() {
		return windthrow;
	}

	@JsonProperty("windthrow")
	public void setWindthrow(String windthrow) {
		this.windthrow = windthrow;
	}

	@JsonProperty("trailfeatures")
	public String getTrailfeatures() {
		return trailfeatures;
	}

	@JsonProperty("trailfeatures")
	public void setTrailfeatures(String trailfeatures) {
		this.trailfeatures = trailfeatures;
	}

	@JsonProperty("Q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature")
	@Deprecated
	public String getQ4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature() {
		return q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature;
	}

	@JsonProperty("Q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature")
	@Deprecated
	public void setQ4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature(
			String q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature) {
		this.q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature = q4Werethereoperationalfactorthatlimitedchrmanagementoptionsforthisfeature;
	}

	@JsonProperty("q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective")
	public String getQ5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective() {
		return q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective;
	}

	@JsonProperty("q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective")
	public void setQ5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective(
			String q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective) {
		this.q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective = q5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective;
	}

	@JsonProperty("q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature")
	public String getQ6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature() {
		return q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature;
	}

	@JsonProperty("q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature")
	public void setQ6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature(
			String q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature) {
		this.q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature = q6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature;
	}

	@JsonProperty("q6description")
	public String getQ6Description() {
		return q6Description;
	}

	@JsonProperty("q6description")
	public void setQ6Description(String q6Description) {
		this.q6Description = q6Description;
	}

	@JsonProperty("Q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable")
	@Deprecated
	public String getQ7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable() {
		return q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable;
	}

	@JsonProperty("Q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable")
	@Deprecated
	public void setQ7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable(
			String q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable) {
		this.q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable = q7TowhatextentdidpracticesforthisfeaturemaintainCHRvaluegiventherecommendationsandopportunitiesthatwereavailable;
	}

	@JsonProperty("Q7Description")
	public String getQ7Description() {
		return q7Description;
	}

	@JsonProperty("Q7Description")
	public void setQ7Description(String q7Description) {
		this.q7Description = q7Description;
	}

	@JsonProperty("Description")
	public String getDescription() {
		return description;
	}

	@JsonProperty("Description")
	public void setDescription(String description) {
		this.description = description;
	}

	@JsonProperty("Source")
	public String getSource() {
		return source;
	}

	@JsonProperty("Source")
	public void setSource(String source) {
		this.source = source;
	}

	@JsonProperty("Pictures")
	public List<Picture> getPictures() {
		return pictures;
	}

	@JsonProperty("Pictures")
	public void setPictures(List<Picture> pictures) {
		this.pictures = pictures;
	}

	@JsonProperty("Status")
	public String getStatus() {
		return status;
	}

	@JsonProperty("Status")
	public void setStatus(String status) {
		this.status = status;
	}

	@JsonProperty("AssoFeatures")
	public List<Integer> getAssoFeatures() {
		return assoFeatures;
	}

	@JsonProperty("AssoFeatures")
	public void setAssoFeatures(List<Integer> assoFeatures) {
		this.assoFeatures = assoFeatures;
	}

	@JsonProperty("otherPlannedManagementStrategy")
	public List<OtherPlannedManagementStrategy> getOtherPlannedManagementStrategy() {
		return otherPlannedManagementStrategy;
	}

	@JsonProperty("otherPlannedManagementStrategy")
	public void setOtherMP(List<OtherPlannedManagementStrategy> otherPlannedManagementStrategy) {
		this.otherPlannedManagementStrategy = otherPlannedManagementStrategy;
	}

	@JsonProperty("partiallyconservedinpermanentreserve")
	public String getPartiallyconservedinpermanentreserve() {
		return partiallyconservedinpermanentreserve;
	}

	@JsonProperty("partiallyconservedinpermanentreserve")
	public void setPartiallyconservedinpermanentreserve(String partiallyconservedinpermanentreserve) {
		this.partiallyconservedinpermanentreserve = partiallyconservedinpermanentreserve;
	}

	@JsonProperty("partiallyconservedinpermanentreserveType")
	public String getPartiallyconservedinpermanentreserveType() {
		return partiallyconservedinpermanentreserveType;
	}

	@JsonProperty("partiallyconservedinpermanentreserveType")
	public void setPartiallyconservedinpermanentreserveType(String partiallyconservedinpermanentreserveType) {
		this.partiallyconservedinpermanentreserveType = partiallyconservedinpermanentreserveType;
	}

	@JsonProperty("fullyconservedinpermanentreserveType")
	public String getFullyconservedinpermanentreserveType() {
		return fullyconservedinpermanentreserveType;
	}

	@JsonProperty("fullyconservedinpermanentreserveType")
	public void setFullyconservedinpermanentreserveType(String fullyconservedinpermanentreserveType) {
		this.fullyconservedinpermanentreserveType = fullyconservedinpermanentreserveType;
	}

	@JsonProperty("partiallytemporaryreserve")
	public String getPartiallytemporaryreserve() {
		return partiallytemporaryreserve;
	}

	@JsonProperty("partiallytemporaryreserve")
	public void setPartiallytemporaryreserve(String partiallytemporaryreserve) {
		this.partiallytemporaryreserve = partiallytemporaryreserve;
	}

	@JsonProperty("partiallytemporaryreservetype")
	public String getPartiallytemporaryreservetype() {
		return partiallytemporaryreservetype;
	}

	@JsonProperty("partiallytemporaryreservetype")
	public void setPartiallytemporaryreservetype(String partiallytemporaryreservetype) {
		this.partiallytemporaryreservetype = partiallytemporaryreservetype;
	}

	@JsonProperty("fullyconservedinpermanentreserve")
	public String getFullyconservedinpermanentreserve() {
		return fullyconservedinpermanentreserve;
	}

	@JsonProperty("fullyconservedinpermanentreserve")
	public void setFullyconservedinpermanentreserve(String fullyconservedinpermanentreserve) {
		this.fullyconservedinpermanentreserve = fullyconservedinpermanentreserve;
	}

	@JsonProperty("fullytemporaryreserve")
	public String getFullytemporaryreserve() {
		return fullytemporaryreserve;
	}

	@JsonProperty("fullytemporaryreserve")
	public void setFullytemporaryreserve(String fullytemporaryreserve) {
		this.fullytemporaryreserve = fullytemporaryreserve;
	}

	@JsonProperty("conserveRotationalReserveTypeFN")
	public String getConserveRotationalReserveTypeFN() {
		return conserveRotationalReserveTypeFN;
	}

	@JsonProperty("conserveRotationalReserveTypeFN")
	public void setConserveRotationalReserveTypeFN(String conserveRotationalReserveTypeFN) {
		this.conserveRotationalReserveTypeFN = conserveRotationalReserveTypeFN;
	}

	@JsonProperty("conserveRotationalReserveTypeAIA")
	public String getConserveRotationalReserveTypeAIA() {
		return conserveRotationalReserveTypeAIA;
	}

	@JsonProperty("conserveRotationalReserveTypeAIA")
	public void setConserveRotationalReserveTypeAIA(String conserveRotationalReserveTypeAIA) {
		this.conserveRotationalReserveTypeAIA = conserveRotationalReserveTypeAIA;
	}

	@JsonProperty("conserveRotationalReserveTypeSP")
	public String getConserveRotationalReserveTypeSP() {
		return conserveRotationalReserveTypeSP;
	}

	@JsonProperty("conserveRotationalReserveTypeSP")
	public void setConserveRotationalReserveTypeSP(String conserveRotationalReserveTypeSP) {
		this.conserveRotationalReserveTypeSP = conserveRotationalReserveTypeSP;
	}

	@JsonProperty("temporaryRetentionTypeFN")
	public String getTemporaryRetentionTypeFN() {
		return temporaryRetentionTypeFN;
	}

	@JsonProperty("temporaryRetentionTypeFN")
	public void setTemporaryRetentionTypeFN(String temporaryRetentionTypeFN) {
		this.temporaryRetentionTypeFN = temporaryRetentionTypeFN;
	}

	@JsonProperty("temporaryRetentionTypeAIA")
	public String getTemporaryRetentionTypeAIA() {
		return temporaryRetentionTypeAIA;
	}

	@JsonProperty("temporaryRetentionTypeAIA")
	public void setTemporaryRetentionTypeAIA(String temporaryRetentionTypeAIA) {
		this.temporaryRetentionTypeAIA = temporaryRetentionTypeAIA;
	}

	@JsonProperty("temporaryRetentionTypeSP")
	public String getTemporaryRetentionTypeSP() {
		return temporaryRetentionTypeSP;
	}

	@JsonProperty("temporaryRetentionTypeSP")
	public void setTemporaryRetentionTypeSP(String temporaryRetentionTypeSP) {
		this.temporaryRetentionTypeSP = temporaryRetentionTypeSP;
	}

	@JsonProperty("locationReservetype")
	public String getLocationReservetype() {
		return locationReservetype;
	}

	@JsonProperty("locationReservetype")
	public void setLocationReservetype(String locationReservetype) {
		this.locationReservetype = locationReservetype;
	}

	@JsonProperty("ofCMTs")
	public String getOfCMTs() {
		return ofCMTs;
	}

	@JsonProperty("ofCMTs")
	public void setOfCMTs(String ofCMTs) {
		this.ofCMTs = ofCMTs;
	}

	@JsonProperty("ofCMTsNumber")
	public String getOfCMTsNumber() {
		return ofCMTsNumber;
	}

	@JsonProperty("ofCMTsNumber")
	public void setOfCMTsNumber(String ofCMTsNumber) {
		this.ofCMTsNumber = ofCMTsNumber;
	}

	@JsonProperty("standofMonumentalCedar")
	public String getStandofMonumentalCedar() {
		return standofMonumentalCedar;
	}

	@JsonProperty("standofMonumentalCedar")
	public void setStandofMonumentalCedar(String standofMonumentalCedar) {
		this.standofMonumentalCedar = standofMonumentalCedar;
	}

	@JsonProperty("lithics")
	public String getLithics() {
		return lithics;
	}

	@JsonProperty("lithics")
	public void setLithics(String lithics) {
		this.lithics = lithics;
	}

	@JsonProperty("other")
	public String getOther() {
		return other;
	}

	@JsonProperty("other")
	public void setOther(String other) {
		this.other = other;
	}

	@JsonProperty("otherdescription")
	public String getOtherDescription() {
		return otherDescription;
	}

	@JsonProperty("otherdescription")
	public void setOtherDescription(String otherDescription) {
		this.otherDescription = otherDescription;
	}

	@JsonProperty("caveorotherKarst")
	public String getCaveorotherKarst() {
		return caveorotherKarst;
	}

	@JsonProperty("caveorotherKarst")
	public void setCaveorotherKarst(String caveorotherKarst) {
		this.caveorotherKarst = caveorotherKarst;
	}

	@JsonProperty("nest")
	public String getNest() {
		return nest;
	}

	@JsonProperty("nest")
	public void setNest(String nest) {
		this.nest = nest;
	}

	@JsonProperty("rockOutcrop")
	public String getRockOutcrop() {
		return rockOutcrop;
	}

	@JsonProperty("rockOutcrop")
	public void setRockOutcrop(String rockOutcrop) {
		this.rockOutcrop = rockOutcrop;
	}

	@JsonProperty("individualMonumentalCedar")
	public String getIndividualMonumentalCedar() {
		return individualMonumentalCedar;
	}

	@JsonProperty("individualMonumentalCedar")
	public void setIndividualMonumentalCedar(String individualMonumentalCedar) {
		this.individualMonumentalCedar = individualMonumentalCedar;
	}

	@JsonProperty("cedarBarkStriparea")
	public String getCedarBarkStripArea() {
		return cedarBarkStripArea;
	}

	@JsonProperty("cedarBarkStriparea")
	public void setCedarBarkStripArea(String cedarBarkStripArea) {
		this.cedarBarkStripArea = cedarBarkStripArea;
	}

	@JsonProperty("ceremonialSite")
	public String getCeremonialSite() {
		return ceremonialSite;
	}

	@JsonProperty("ceremonialSite")
	public void setCeremonialSite(String ceremonialSite) {
		this.ceremonialSite = ceremonialSite;
	}

	@JsonProperty("cremationSite")
	public String getCremationSite() {
		return cremationSite;
	}

	@JsonProperty("cremationSite")
	public void setCremationSite(String cremationSite) {
		this.cremationSite = cremationSite;
	}

	@JsonProperty("burialSite")
	public String getBurialSite() {
		return burialSite;
	}

	@JsonProperty("burialSite")
	public void setBurialSite(String burialSite) {
		this.burialSite = burialSite;
	}

	@JsonProperty("culturaltraildesignated")
	public String getCulturalTraildesignated() {
		return culturalTraildesignated;
	}

	@JsonProperty("culturaltraildesignated")
	public void setCulturalTraildesignated(String culturalTraildesignated) {
		this.culturalTraildesignated = culturalTraildesignated;
	}

	@JsonProperty("culturaltrailundesignated")
	public String getCulturalTrailundesignated() {
		return culturalTrailundesignated;
	}

	@JsonProperty("culturaltrailundesignated")
	public void setCulturalTrailundesignated(String culturalTrailundesignated) {
		this.culturalTrailundesignated = culturalTrailundesignated;
	}

	@JsonProperty("individualCMT")
	public String getIndividualCMT() {
		return individualCMT;
	}

	@JsonProperty("individualCMT")
	public void setIndividualCMT(String individualCMT) {
		this.individualCMT = individualCMT;
	}

	@JsonProperty("traditionalUseSite")
	public String getTraditionalUseSite() {
		return traditionalUseSite;
	}

	@JsonProperty("traditionalUseSite")
	public void setTraditionalUseSite(String traditionalUseSite) {
		this.traditionalUseSite = traditionalUseSite;
	}

	@JsonProperty("spiritualSite")
	public String getSpiritualSite() {
		return spiritualSite;
	}

	@JsonProperty("spiritualSite")
	public void setSpiritualSite(String spiritualSite) {
		this.spiritualSite = spiritualSite;
	}

	@JsonProperty("ofMonumentalCedars")
	public String getOfMonumentalCedars() {
		return ofMonumentalCedars;
	}

	@JsonProperty("ofMonumentalCedars")
	public void setOfMonumentalCedars(String ofMonumentalCedars) {
		this.ofMonumentalCedars = ofMonumentalCedars;
	}

	@JsonProperty("culturalDepression")
	public String getCulturalDepression() {
		return culturalDepression;
	}

	@JsonProperty("culturalDepression")
	public void setCulturalDepression(String culturalDepression) {
		this.culturalDepression = culturalDepression;
	}

	@JsonProperty("widthofFeature")
	public String getWidthofFeature() {
		return widthofFeature;
	}

	@JsonProperty("widthofFeature")
	public void setWidthofFeature(String widthofFeature) {
		this.widthofFeature = widthofFeature;
	}

	@JsonProperty("areaofFeature")
	public String getAreaofFeature() {
		return areaofFeature;
	}

	@JsonProperty("areaofFeature")
	public void setAreaofFeature(String areaofFeature) {
		this.areaofFeature = areaofFeature;
	}

	@JsonProperty("borden")
	public String getBorden() {
		return borden;
	}

	@JsonProperty("borden")
	public void setBorden(String borden) {
		this.borden = borden;
	}

	@JsonProperty("easting")
	public String getEasting() {
		return easting;
	}

	@JsonProperty("easting")
	public void setEasting(String easting) {
		this.easting = easting;
	}

	@JsonProperty("northing")
	public String getNorthing() {
		return northing;
	}

	@JsonProperty("northing")
	public void setNorthing(String northing) {
		this.northing = northing;
	}

	@JsonProperty("utmZone")
	public String getUtmZone() {
		return utmZone;
	}

	@JsonProperty("utmZone")
	public void setUtmZone(String utmZone) {
		this.utmZone = utmZone;
	}

	@JsonProperty("adjacenttoblock")
	public String getAdjacenttoblock() {
		return adjacenttoblock;
	}

	@JsonProperty("adjacenttoblock")
	public void setAdjacenttoblock(String adjacenttoblock) {
		this.adjacenttoblock = adjacenttoblock;
	}

	@JsonProperty("entirecutblock")
	public String getEntirecutblock() {
		return entirecutblock;
	}

	@JsonProperty("entirecutblock")
	public void setEntirecutblock(String entirecutblock) {
		this.entirecutblock = entirecutblock;
	}

	@JsonProperty("adjacenttowater")
	public String getAdjacenttowater() {
		return adjacenttowater;
	}

	@JsonProperty("adjacenttowater")
	public void setAdjacenttowater(String adjacenttowater) {
		this.adjacenttowater = adjacenttowater;
	}

	@JsonProperty("Inreserve")
	public String getInReserve() {
		return inReserve;
	}

	@JsonProperty("Inreserve")
	public void setInReserve(String inReserve) {
		this.inReserve = inReserve;
	}

	@JsonProperty("pre1846")
	public String getPre1846() {
		return pre1846;
	}

	@JsonProperty("pre1846")
	public void setPre1846(String pre1846) {
		this.pre1846 = pre1846;
	}

	@JsonProperty("ageUnknown")
	public String getAgeUnknown() {
		return ageUnknown;
	}

	@JsonProperty("ageUnknown")
	public void setAgeUnknown(String ageUnknown) {
		this.ageUnknown = ageUnknown;
	}

	@JsonProperty("historicalUse")
	public String getHistoricalUse() {
		return historicalUse;
	}

	@JsonProperty("historicalUse")
	public void setHistoricalUse(String historicalUse) {
		this.historicalUse = historicalUse;
	}

	@JsonProperty("permit")
	public String getPermit() {
		return permit;
	}

	@JsonProperty("permit")
	public void setPermit(String permit) {
		this.permit = permit;
	}

	@JsonProperty("sitepermitIssued")
	public String getSitePermitIssued() {
		return sitePermitIssued;
	}

	@JsonProperty("sitepermitIssued")
	public void setSitePermitIssued(String sitePermitIssued) {
		this.sitePermitIssued = sitePermitIssued;
	}

	@JsonProperty("managementStrategySP")
	public String getManagementStrategySP() {
		return managementStrategySP;
	}

	@JsonProperty("managementStrategySP")
	public void setManagementStrategySP(String managementStrategySP) {
		this.managementStrategySP = managementStrategySP;
	}

	@JsonProperty("managementStrategyFN")
	public String getManagementStrategyFN() {
		return managementStrategyFN;
	}

	@JsonProperty("managementStrategyFN")
	public void setManagementStrategyFN(String managementStrategyFN) {
		this.managementStrategyFN = managementStrategyFN;
	}

	@JsonProperty("noManagementStrategyAIA")
	public String getNoManagementStrategyAIA() {
		return noManagementStrategyAIA;
	}

	@JsonProperty("noManagementStrategyAIA")
	public void setNoManagementStrategyAIA(String noManagementStrategyAIA) {
		this.noManagementStrategyAIA = noManagementStrategyAIA;
	}

	@JsonProperty("modifyBlockBoundaryFN")
	public String getModifyBlockBoundaryFN() {
		return modifyBlockBoundaryFN;
	}

	@JsonProperty("modifyBlockBoundaryFN")
	public void setModifyBlockBoundaryFN(String modifyBlockBoundaryFN) {
		this.modifyBlockBoundaryFN = modifyBlockBoundaryFN;
	}

	@JsonProperty("modifyBlockBoundaryAIA")
	public String getModifyBlockBoundaryAIA() {
		return modifyBlockBoundaryAIA;
	}

	@JsonProperty("modifyBlockBoundaryAIA")
	public void setModifyBlockBoundaryAIA(String modifyBlockBoundaryAIA) {
		this.modifyBlockBoundaryAIA = modifyBlockBoundaryAIA;
	}

	@JsonProperty("modifyBlockBoundarySP")
	public String getModifyBlockBoundarySP() {
		return modifyBlockBoundarySP;
	}

	@JsonProperty("modifyBlockBoundarySP")
	public void setModifyBlockBoundarySP(String modifyBlockBoundarySP) {
		this.modifyBlockBoundarySP = modifyBlockBoundarySP;
	}

	@JsonProperty("retainBufferSP")
	public String getRetainBufferSP() {
		return retainBufferSP;
	}

	@JsonProperty("retainBufferSP")
	public void setRetainBufferSP(String retainBufferSP) {
		this.retainBufferSP = retainBufferSP;
	}

	@JsonProperty("retainBufferAIA")
	public String getRetainBufferAIA() {
		return retainBufferAIA;
	}

	@JsonProperty("retainBufferAIA")
	public void setRetainBufferAIA(String retainBufferAIA) {
		this.retainBufferAIA = retainBufferAIA;
	}

	@JsonProperty("retainBufferFN")
	public String getRetainBufferFN() {
		return retainBufferFN;
	}

	@JsonProperty("retainBufferFN")
	public void setRetainBufferFN(String retainBufferFN) {
		this.retainBufferFN = retainBufferFN;
	}

	@JsonProperty("bufferLengthFN")
	public String getBufferLengthFN() {
		return bufferLengthFN;
	}

	@JsonProperty("bufferLengthFN")
	public void setBufferLengthFN(String bufferLengthFN) {
		this.bufferLengthFN = bufferLengthFN;
	}

	@JsonProperty("bufferLengthAIA")
	public String getBufferLengthAIA() {
		return bufferLengthAIA;
	}

	@JsonProperty("bufferLengthAIA")
	public void setBufferLengthAIA(String bufferLengthAIA) {
		this.bufferLengthAIA = bufferLengthAIA;
	}

	@JsonProperty("bufferLengthSP")
	public String getBufferLengthSP() {
		return bufferLengthSP;
	}

	@JsonProperty("bufferLengthSP")
	public void setBufferLengthSP(String bufferLengthSP) {
		this.bufferLengthSP = bufferLengthSP;
	}

	@JsonProperty("retaininharvestareaFN")
	public String getRetaininHarvestAreaFN() {
		return retaininHarvestAreaFN;
	}

	@JsonProperty("retaininharvestareaFN")
	public void setRetaininHarvestAreaFN(String retaininHarvestAreaFN) {
		this.retaininHarvestAreaFN = retaininHarvestAreaFN;
	}

	@JsonProperty("crownorstandmodificationFN")
	public String getCrownorstandmodificationFN() {
		return crownorstandmodificationFN;
	}

	@JsonProperty("crownorstandmodificationFN")
	public void setCrownorstandmodificationFN(String crownorstandmodificationFN) {
		this.crownorstandmodificationFN = crownorstandmodificationFN;
	}

	@JsonProperty("conserveinRotationalReserveFN")
	public String getConserveinRotationalReserveFN() {
		return conserveinRotationalReserveFN;
	}

	@JsonProperty("conserveinRotationalReserveFN")
	public void setConserveinRotationalReserveFN(String conserveinRotationalReserveFN) {
		this.conserveinRotationalReserveFN = conserveinRotationalReserveFN;
	}

	@JsonProperty("permanentReserveFN")
	public String getPermanentReserveFN() {
		return permanentReserveFN;
	}

	@JsonProperty("permanentReserveFN")
	public void setPermanentReserveFN(String permanentReserveFN) {
		this.permanentReserveFN = permanentReserveFN;
	}

	@JsonProperty("permanentReserveAIA")
	public String getPermanentReserveAIA() {
		return permanentReserveAIA;
	}

	@JsonProperty("permanentReserveAIA")
	public void setPermanentReserveAIA(String permanentReserveAIA) {
		this.permanentReserveAIA = permanentReserveAIA;
	}

	@JsonProperty("conserveinRotationalReserveAIA")
	public String getConserveinRotationalReserveAIA() {
		return conserveinRotationalReserveAIA;
	}

	@JsonProperty("conserveinRotationalReserveAIA")
	public void setConserveinRotationalReserveAIA(String conserveinRotationalReserveAIA) {
		this.conserveinRotationalReserveAIA = conserveinRotationalReserveAIA;
	}

	@JsonProperty("crownorstandmodificationAIA")
	public String getCrownorstandmodificationAIA() {
		return crownorstandmodificationAIA;
	}

	@JsonProperty("crownorstandmodificationAIA")
	public void setCrownorstandmodificationAIA(String crownorstandmodificationAIA) {
		this.crownorstandmodificationAIA = crownorstandmodificationAIA;
	}

	@JsonProperty("retaininharvestareaAIA")
	public String getRetaininHarvestAreaAIA() {
		return retaininHarvestAreaAIA;
	}

	@JsonProperty("retaininharvestareaAIA")
	public void setRetaininHarvestAreaAIA(String retaininHarvestAreaAIA) {
		this.retaininHarvestAreaAIA = retaininHarvestAreaAIA;
	}

	@JsonProperty("retaininharvestareaSP")
	public String getRetaininHarvestAreaSP() {
		return retaininHarvestAreaSP;
	}

	@JsonProperty("retaininharvestareaSP")
	public void setRetaininHarvestAreaSP(String retaininHarvestAreaSP) {
		this.retaininHarvestAreaSP = retaininHarvestAreaSP;
	}

	@JsonProperty("crownorstandmodificationSP")
	public String getCrownorstandmodificationSP() {
		return crownorstandmodificationSP;
	}

	@JsonProperty("crownorstandmodificationSP")
	public void setCrownorstandmodificationSP(String crownorstandmodificationSP) {
		this.crownorstandmodificationSP = crownorstandmodificationSP;
	}

	@JsonProperty("conserveinRotationalReserveSP")
	public String getConserveinRotationalReserveSP() {
		return conserveinRotationalReserveSP;
	}

	@JsonProperty("conserveinRotationalReserveSP")
	public void setConserveinRotationalReserveSP(String conserveinRotationalReserveSP) {
		this.conserveinRotationalReserveSP = conserveinRotationalReserveSP;
	}

	@JsonProperty("permanentReserveSP")
	public String getPermanentReserveSP() {
		return permanentReserveSP;
	}

	@JsonProperty("permanentReserveSP")
	public void setPermanentReserveSP(String permanentReserveSP) {
		this.permanentReserveSP = permanentReserveSP;
	}

	@JsonProperty("altersilvicultureSP")
	public String getAltersilvicultureSP() {
		return altersilvicultureSP;
	}

	@JsonProperty("altersilvicultureSP")
	public void setAltersilvicultureSP(String altersilvicultureSP) {
		this.altersilvicultureSP = altersilvicultureSP;
	}

	@JsonProperty("leavestandingSP")
	public String getLeaveStandingSP() {
		return leaveStandingSP;
	}

	@JsonProperty("leavestandingSP")
	public void setLeaveStandingSP(String leaveStandingSP) {
		this.leaveStandingSP = leaveStandingSP;
	}

	@JsonProperty("stubnonCMTsSP")
	public String getStubnonCMTsSP() {
		return stubnonCMTsSP;
	}

	@JsonProperty("stubnonCMTsSP")
	public void setStubnonCMTsSP(String stubnonCMTsSP) {
		this.stubnonCMTsSP = stubnonCMTsSP;
	}

	@JsonProperty("stubCMtsabovescarSP")
	public String getStubCMTsabovescarSP() {
		return stubCMTsabovescarSP;
	}

	@JsonProperty("stubCMtsabovescarSP")
	public void setStubCMTsabovescarSP(String stubCMTsabovescarSP) {
		this.stubCMTsabovescarSP = stubCMTsabovescarSP;
	}

	@JsonProperty("datetheFeatureSP")
	public String getDatetheFeatureSP() {
		return datetheFeatureSP;
	}

	@JsonProperty("datetheFeatureSP")
	public void setDatetheFeatureSP(String datetheFeatureSP) {
		this.datetheFeatureSP = datetheFeatureSP;
	}

	@JsonProperty("datetheFeatureAIA")
	public String getDatetheFeatureAIA() {
		return datetheFeatureAIA;
	}

	@JsonProperty("datetheFeatureAIA")
	public void setDatetheFeatureAIA(String datetheFeatureAIA) {
		this.datetheFeatureAIA = datetheFeatureAIA;
	}

	@JsonProperty("stubCMtsabovescarAIA")
	public String getStubCMTsabovescarAIA() {
		return stubCMTsabovescarAIA;
	}

	@JsonProperty("stubCMtsabovescarAIA")
	public void setStubCMTsabovescarAIA(String stubCMTsabovescarAIA) {
		this.stubCMTsabovescarAIA = stubCMTsabovescarAIA;
	}

	@JsonProperty("stubnonCMtsaIA")
	public String getStubnonCMTsAIA() {
		return stubnonCMTsAIA;
	}

	@JsonProperty("stubnonCMtsaIA")
	public void setStubnonCMTsAIA(String stubnonCMTsAIA) {
		this.stubnonCMTsAIA = stubnonCMTsAIA;
	}

	@JsonProperty("leavestandingAIA")
	public String getLeaveStandingAIA() {
		return leaveStandingAIA;
	}

	@JsonProperty("leavestandingAIA")
	public void setLeaveStandingAIA(String leaveStandingAIA) {
		this.leaveStandingAIA = leaveStandingAIA;
	}

	@JsonProperty("altersilvicultureAIA")
	public String getAltersilvicultureAIA() {
		return altersilvicultureAIA;
	}

	@JsonProperty("altersilvicultureAIA")
	public void setAltersilvicultureAIA(String altersilvicultureAIA) {
		this.altersilvicultureAIA = altersilvicultureAIA;
	}

	@JsonProperty("altersilvicultureFN")
	public String getAltersilvicultureFN() {
		return altersilvicultureFN;
	}

	@JsonProperty("altersilvicultureFN")
	public void setAltersilvicultureFN(String altersilvicultureFN) {
		this.altersilvicultureFN = altersilvicultureFN;
	}

	@JsonProperty("leavestandingFN")
	public String getLeaveStandingFN() {
		return leaveStandingFN;
	}

	@JsonProperty("leavestandingFN")
	public void setLeaveStandingFN(String leaveStandingFN) {
		this.leaveStandingFN = leaveStandingFN;
	}

	@JsonProperty("stubnonCMTsFN")
	public String getStubnonCMTsFN() {
		return stubnonCMTsFN;
	}

	@JsonProperty("stubnonCMTsFN")
	public void setStubnonCMTsFN(String stubnonCMTsFN) {
		this.stubnonCMTsFN = stubnonCMTsFN;
	}

	@JsonProperty("datetheFeatureFN")
	public String getDatetheFeatureFN() {
		return datetheFeatureFN;
	}

	@JsonProperty("datetheFeatureFN")
	public void setDatetheFeatureFN(String datetheFeatureFN) {
		this.datetheFeatureFN = datetheFeatureFN;
	}

	@JsonProperty("stubCMtsabovescarFN")
	public String getStubCMTsabovescarFN() {
		return stubCMTsabovescarFN;
	}

	@JsonProperty("stubCMtsabovescarFN")
	public void setStubCMTsabovescarFN(String stubCMTsabovescarFN) {
		this.stubCMTsabovescarFN = stubCMTsabovescarFN;
	}

	@JsonProperty("unabletoLocate")
	public String getUnabletoLocate() {
		return unabletoLocate;
	}

	@JsonProperty("unabletoLocate")
	public void setUnabletoLocate(String unabletoLocate) {
		this.unabletoLocate = unabletoLocate;
	}

	@JsonProperty("fullyconservedintemporaryreserve")
	public String getFullyconservedintemporaryreserve() {
		return fullyconservedintemporaryreserve;
	}

	@JsonProperty("fullyconservedintemporaryreserve")
	public void setFullyconservedintemporaryreserve(String fullyconservedintemporaryreserve) {
		this.fullyconservedintemporaryreserve = fullyconservedintemporaryreserve;
	}

	@JsonProperty("Permanentreservetype")
	public String getPermanentreservetype() {
		return permanentreservetype;
	}

	@JsonProperty("Permanentreservetype")
	public void setPermanentreservetype(String permanentreservetype) {
		this.permanentreservetype = permanentreservetype;
	}

	@JsonProperty("Modifiedblockboundary")
	public String getModifiedblockboundary() {
		return modifiedblockboundary;
	}

	@JsonProperty("Modifiedblockboundary")
	public void setModifiedblockboundary(String modifiedblockboundary) {
		this.modifiedblockboundary = modifiedblockboundary;
	}

	@JsonProperty("retainedinharvestareanobuffer")
	public String getRetainedinharvestareanobuffer() {
		return retainedinharvestareanobuffer;
	}

	@JsonProperty("retainedinharvestareanobuffer")
	public void setRetainedinharvestareanobuffer(String retainedinharvestareanobuffer) {
		this.retainedinharvestareanobuffer = retainedinharvestareanobuffer;
	}

	@JsonProperty("compledcrownorstandmodification")
	public String getCompledCrownorstandmodification() {
		return compledCrownorstandmodification;
	}

	@JsonProperty("compledcrownorstandmodification")
	public void setCompledCrownorstandmodification(String compledCrownorstandmodification) {
		this.compledCrownorstandmodification = compledCrownorstandmodification;
	}

	@JsonProperty("datedthefeature")
	public String getDatedthefeature() {
		return datedthefeature;
	}

	@JsonProperty("datedthefeature")
	public void setDatedthefeature(String datedthefeature) {
		this.datedthefeature = datedthefeature;
	}

	@JsonProperty("leftStanding")
	public String getLeftStanding() {
		return leftStanding;
	}

	@JsonProperty("leftStanding")
	public void setLeftStanding(String leftStanding) {
		this.leftStanding = leftStanding;
	}

	@JsonProperty("otherActivities")
	public String getOtherActivities() {
		return otherActivities;
	}

	@JsonProperty("otherActivities")
	public void setOtherActivities(String otherActivities) {
		this.otherActivities = otherActivities;
	}

	@JsonProperty("stubbed")
	public String getStubbed() {
		return stubbed;
	}

	@JsonProperty("stubbed")
	public void setStubbed(String stubbed) {
		this.stubbed = stubbed;
	}

	@JsonProperty("stubbedNon")
	public String getStubbedNon() {
		return stubbedNon;
	}

	@JsonProperty("stubbedNon")
	public void setStubbedNon(String stubbedNon) {
		this.stubbedNon = stubbedNon;
	}

	@JsonProperty("retainabuffer")
	public String getRetainabuffer() {
		return retainabuffer;
	}

	@JsonProperty("retainabuffer")
	public void setRetainabuffer(String retainabuffer) {
		this.retainabuffer = retainabuffer;
	}

	@JsonProperty("bufferLengthManageEffectiveness")
	public String getBufferLengthManageEffectiveness() {
		return bufferLengthManageEffectiveness;
	}

	@JsonProperty("bufferLengthManageEffectiveness")
	public void setBufferLengthManageEffectiveness(String bufferLengthManageEffectiveness) {
		this.bufferLengthManageEffectiveness = bufferLengthManageEffectiveness;
	}

	@JsonProperty("alteredsilviculture")
	public String getAlteredsilviculture() {
		return alteredsilviculture;
	}

	@JsonProperty("alteredsilviculture")
	public void setAlteredsilviculture(String alteredsilviculture) {
		this.alteredsilviculture = alteredsilviculture;
	}

	@JsonProperty("Harvestingq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getHarvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("Harvestingq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setHarvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause = harvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("silvicultureq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getSilvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("silvicultureq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setSilvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause = silvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("fireq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getFireQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return fireQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("fireq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setFireQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String fireQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.fireQ2Wheredamagehasoccurredwhatisthemostlikelycause = fireQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("roadq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getRoadQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return roadQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("roadq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setRoadQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String roadQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.roadQ2Wheredamagehasoccurredwhatisthemostlikelycause = roadQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("windthrowq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getWindthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("windthrowq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setWindthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause = windthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("otherq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return otherQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("otherq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String otherQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.otherQ2Wheredamagehasoccurredwhatisthemostlikelycause = otherQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("livestockq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getLivestockQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("livestockq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setLivestockQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause = livestockQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("IndustrialUseq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getIndustrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("IndustrialUseq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setIndustrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause = industrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("recreationq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getRecreationQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("recreationq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setRecreationQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause = recreationQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("safetyq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getSafetyQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("safetyq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setSafetyQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause = safetyQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("descriptionofdamage")
	public String getDescriptionofdamage() {
		return descriptionofdamage;
	}

	@JsonProperty("descriptionofdamage")
	public void setDescriptionofdamage(String descriptionofdamage) {
		this.descriptionofdamage = descriptionofdamage;
	}

	@JsonProperty("windthrowManagement")
	public String getWindthrowManagement() {
		return windthrowManagement;
	}

	@JsonProperty("windthrowManagement")
	public void setWindthrowManagement(String windthrowManagement) {
		this.windthrowManagement = windthrowManagement;
	}

	@JsonProperty("estwindthrow")
	public String getEstwindthrow() {
		return estwindthrow;
	}

	@JsonProperty("estwindthrow")
	public void setEstwindthrow(String estwindthrow) {
		this.estwindthrow = estwindthrow;
	}

	@JsonProperty("windthrowTechniquenone")
	public String getWindthrowTechniqueNone() {
		return windthrowTechniqueNone;
	}

	@JsonProperty("windthrowTechniquenone")
	public void setWindthrowTechniqueNone(String windthrowTechniqueNone) {
		this.windthrowTechniqueNone = windthrowTechniqueNone;
	}

	@JsonProperty("otherTechnique")
	public String getOtherTechnique() {
		return otherTechnique;
	}

	@JsonProperty("otherTechnique")
	public void setOtherTechnique(String otherTechnique) {
		this.otherTechnique = otherTechnique;
	}

	@JsonProperty("windthrowTechniqueFeathering")
	public String getWindthrowTechniqueFeathering() {
		return windthrowTechniqueFeathering;
	}

	@JsonProperty("windthrowTechniqueFeathering")
	public void setWindthrowTechniqueFeathering(String windthrowTechniqueFeathering) {
		this.windthrowTechniqueFeathering = windthrowTechniqueFeathering;
	}

	@JsonProperty("windthrowTechniqueRetentionBuffer")
	public String getWindthrowTechniqueRetentionBuffer() {
		return windthrowTechniqueRetentionBuffer;
	}

	@JsonProperty("windthrowTechniqueRetentionBuffer")
	public void setWindthrowTechniqueRetentionBuffer(String windthrowTechniqueRetentionBuffer) {
		this.windthrowTechniqueRetentionBuffer = windthrowTechniqueRetentionBuffer;
	}

	@JsonProperty("windthrowTechniquePruning")
	public String getWindthrowTechniquePruning() {
		return windthrowTechniquePruning;
	}

	@JsonProperty("windthrowTechniquePruning")
	public void setWindthrowTechniquePruning(String windthrowTechniquePruning) {
		this.windthrowTechniquePruning = windthrowTechniquePruning;
	}

	@JsonProperty("windthrowTechniqueTopping")
	public String getWindthrowTechniqueTopping() {
		return windthrowTechniqueTopping;
	}

	@JsonProperty("windthrowTechniqueTopping")
	public void setWindthrowTechniqueTopping(String windthrowTechniqueTopping) {
		this.windthrowTechniqueTopping = windthrowTechniqueTopping;
	}

	@JsonProperty("ifotherpleasedescribe")
	public String getIfotherpleasedescribe() {
		return ifotherpleasedescribe;
	}

	@JsonProperty("ifotherpleasedescribe")
	public void setIfotherpleasedescribe(String ifotherpleasedescribe) {
		this.ifotherpleasedescribe = ifotherpleasedescribe;
	}

	@JsonProperty("canthetrailstillbelocated")
	public String getCanthetrailstillbelocated() {
		return canthetrailstillbelocated;
	}

	@JsonProperty("canthetrailstillbelocated")
	public void setCanthetrailstillbelocated(String canthetrailstillbelocated) {
		this.canthetrailstillbelocated = canthetrailstillbelocated;
	}

	@JsonProperty("hasthetrailbeenmadelesspassble")
	public String getHasthetrailbeenmadelesspassble() {
		return hasthetrailbeenmadelesspassble;
	}

	@JsonProperty("hasthetrailbeenmadelesspassble")
	public void setHasthetrailbeenmadelesspassble(String hasthetrailbeenmadelesspassble) {
		this.hasthetrailbeenmadelesspassble = hasthetrailbeenmadelesspassble;
	}

	@JsonProperty("isthereevidenceofdamage")
	public String getIsthereevidenceofdamage() {
		return isthereevidenceofdamage;
	}

	@JsonProperty("isthereevidenceofdamage")
	public void setIsthereevidenceofdamage(String isthereevidenceofdamage) {
		this.isthereevidenceofdamage = isthereevidenceofdamage;
	}

	@JsonProperty("traillength")
	public String getTrailLength() {
		return trailLength;
	}

	@JsonProperty("traillength")
	public void setTrailLength(String trailLength) {
		this.trailLength = trailLength;
	}

	@JsonProperty("q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature")
	public String getQ4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature() {
		return q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature;
	}

	@JsonProperty("q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature")
	public void setQ4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature(
			String q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature) {
		this.q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature = q4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature;
	}

	@Deprecated
	@JsonProperty("q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective")
	public String getQ5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective() {
		return q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective;
	}

	@Deprecated
	@JsonProperty("q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective")
	public void setQ5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective(
			String q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective) {
		this.q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective = q5weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective;
	}

	@JsonProperty("q5description")
	public String getQ5Description() {
		return q5Description;
	}

	@JsonProperty("q5description")
	public void setQ5Description(String q5Description) {
		this.q5Description = q5Description;
	}

	@JsonProperty("featureRating")
	public String getFeatureRating() {
		return featureRating;
	}

	@JsonProperty("featureRating")
	public void setFeatureRating(String featureRating) {
		this.featureRating = featureRating;
	}

	@JsonProperty("featureRatingRationale")
	public String getFeatureRatingRationale() {
		return featureRatingRationale;
	}

	@JsonProperty("featureRatingRationale")
	public void setFeatureRatingRationale(String featureRatingRationale) {
		this.featureRatingRationale = featureRatingRationale;
	}

	@JsonProperty("id")
	public void setId(String id) {
		this.id = id;
	}

	@JsonProperty("id")
	public String getId() {
		return this.id;
	}

	@JsonProperty("SFDESCRIPTION")
	public String getSFDESCRIPTION() {
		return sFDESCRIPTION;
	}

	@JsonProperty("SFDESCRIPTION")
	public void setSFDESCRIPTION(String sFDESCRIPTION) {
		this.sFDESCRIPTION = sFDESCRIPTION;
	}

	@JsonProperty("Ifotherpleasedescribeotherq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public String getIfotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause() {
		return ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("Ifotherpleasedescribeotherq2Wheredamagehasoccurredwhatisthemostlikelycause")
	public void setIfotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause(
			String ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause) {
		this.ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause = ifotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause;
	}

	@JsonProperty("q4description")
	public String getQ4Description() {
		return q4Description;
	}

	@JsonProperty("q4description")
	public void setQ4Description(String q4Description) {
		this.q4Description = q4Description;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	@JsonProperty("featureDescriptionCode")
	public String getFeatureDescriptionCode() {
		return featureDescriptionCode;
	}

	@JsonProperty("featureDescriptionCode")
	public void setFeatureDescriptionCode(String featureDescriptionCode) {
		this.featureDescriptionCode = featureDescriptionCode;
	}

	@JsonProperty("featureInfoSourceCode")
	public String getFeatureInfoSourceCode() {
		return featureInfoSourceCode;
	}

	@JsonProperty("featureInfoSourceCode")
	public void setFeatureInfoSourceCode(String featureInfoSourceCode) {
		this.featureInfoSourceCode = featureInfoSourceCode;
	}

	@JsonProperty("Reservetype")
	public String getReserveType() {
		return reserveType;
	}

	@JsonProperty("Reservetype")
	public void setReserveType(String reserveType) {
		this.reserveType = reserveType;
	}

	@JsonProperty("bufferWidthMeter")
	public String getBufferWidthMeter() {
		return bufferWidthMeter;
	}

	@JsonProperty("bufferWidthMeter")
	public void setBufferWidthMeter(String bufferWidthMeter) {
		this.bufferWidthMeter = bufferWidthMeter;
	}

	@JsonProperty("forCompositeFeaturesInd")
	public String getForCompositeFeaturesInd() {
		return forCompositeFeaturesInd;
	}

	@JsonProperty("forCompositeFeaturesInd")
	public void setForCompositeFeaturesInd(String forCompositeFeatures) {
		this.forCompositeFeaturesInd = forCompositeFeatures;
	}


	@JsonProperty("isOtherPlannedManagedStrategyFN")
	public String getIsOtherPlannedManagedStrategyFN() {
		return isOtherPlannedManagedStrategyFN;
	}

	@JsonProperty("isOtherPlannedManagedStrategyFN")
	public void setIsOtherPlannedManagedStrategyFN(String isOtherPlannedManagedStrategyFN) {
		this.isOtherPlannedManagedStrategyFN = isOtherPlannedManagedStrategyFN;
	}

	@JsonProperty("isOtherPlannedManagedStrategySP")
	public String getIsOtherPlannedManagedStrategySP() {
		return isOtherPlannedManagedStrategySP;
	}

	@JsonProperty("isOtherPlannedManagedStrategySP")
	public void setIsOtherPlannedManagedStrategySP(String isOtherPlannedManagedStrategySP) {
		this.isOtherPlannedManagedStrategySP = isOtherPlannedManagedStrategySP;
	}

	@JsonProperty("isOtherPlannedManagedStrategyAIA")
	public String getIsOtherPlannedManagedStrategyAIA() {
		return isOtherPlannedManagedStrategyAIA;
	}

	public void setIsOtherPlannedManagedStrategyAIA(String isOtherPlannedManagedStrategyAIA) {
		this.isOtherPlannedManagedStrategyAIA = isOtherPlannedManagedStrategyAIA;
	}

	@JsonProperty("machineFreeZoneFN")
	public String getMachineFreeZoneFN() {
		return machineFreeZoneFN;
	}

	@JsonProperty("machineFreeZoneFN")
	public void setMachineFreeZoneFN(String machineFreeZoneFN) {
		this.machineFreeZoneFN = machineFreeZoneFN;
	}

	@JsonProperty("machineFreeZoneAIA")
	public String getMachineFreeZoneAIA() {
		return machineFreeZoneAIA;
	}

	@JsonProperty("machineFreeZoneAIA")
	public void setMachineFreeZoneAIA(String machineFreeZoneAIA) {
		this.machineFreeZoneAIA = machineFreeZoneAIA;
	}

	@JsonProperty("machineFreeZoneSP")
	public String getMachineFreeZoneSP() {
		return machineFreeZoneSP;
	}

	@JsonProperty("machineFreeZoneSP")
	public void setMachineFreeZoneSP(String machineFreeZoneSP) {
		this.machineFreeZoneSP = machineFreeZoneSP;
	}

	@JsonProperty("harvestUnderSapFN")
	public String getHarvestUnderSapFN() {
		return harvestUnderSapFN;
	}

	@JsonProperty("harvestUnderSapFN")
	public void setHarvestUnderSapFN(String harvestUnderSapFN) {
		this.harvestUnderSapFN = harvestUnderSapFN;
	}

	@JsonProperty("harvestUnderSapAIA")
	public String getHarvestUnderSapAIA() {
		return harvestUnderSapAIA;
	}

	@JsonProperty("harvestUnderSapAIA")
	public void setHarvestUnderSapAIA(String harvestUnderSapAIA) {
		this.harvestUnderSapAIA = harvestUnderSapAIA;
	}

	@JsonProperty("harvestUnderSapSP")
	public String getHarvestUnderSapSP() {
		return harvestUnderSapSP;
	}

	@JsonProperty("harvestUnderSapSP")
	public void setHarvestUnderSapSP(String harvestUnderSapSP) {
		this.harvestUnderSapSP = harvestUnderSapSP;
	}

	@JsonProperty("winterHarvestFrozenGroundFN")
	public String getWinterHarvestFrozenGroundFN() {
		return winterHarvestFrozenGroundFN;
	}

	@JsonProperty("winterHarvestFrozenGroundFN")
	public void setWinterHarvestFrozenGroundFN(String winterHarvestFrozenGroundFN) {
		this.winterHarvestFrozenGroundFN = winterHarvestFrozenGroundFN;
	}

	@JsonProperty("winterHarvestFrozenGroundAIA")
	public String getWinterHarvestFrozenGroundAIA() {
		return winterHarvestFrozenGroundAIA;
	}

	@JsonProperty("winterHarvestFrozenGroundAIA")
	public void setWinterHarvestFrozenGroundAIA(String winterHarvestFrozenGroundAIA) {
		this.winterHarvestFrozenGroundAIA = winterHarvestFrozenGroundAIA;
	}

	@JsonProperty("winterHarvestFrozenGroundSP")
	public String getWinterHarvestFrozenGroundSP() {
		return winterHarvestFrozenGroundSP;
	}

	@JsonProperty("winterHarvestFrozenGroundSP")
	public void setWinterHarvestFrozenGroundSP(String winterHarvestFrozenGroundSP) {
		this.winterHarvestFrozenGroundSP = winterHarvestFrozenGroundSP;
	}

	@JsonProperty("machineFreeZone")
	public String getMachineFreeZone() {
		return machineFreeZone;
	}

	@JsonProperty("machineFreeZone")
	public void setMachineFreeZone(String machineFreeZone) {
		this.machineFreeZone = machineFreeZone;
	}

	@JsonProperty("harvestUnderSap")
	public String getHarvestUnderSap() {
		return harvestUnderSap;
	}

	@JsonProperty("harvestUnderSap")
	public void setHarvestUnderSap(String harvestUnderSap) {
		this.harvestUnderSap = harvestUnderSap;
	}

	@JsonProperty("winterHarvestFrozenGround")
	public String getWinterHarvestFrozenGround() {
		return winterHarvestFrozenGround;
	}

	@JsonProperty("winterHarvestFrozenGround")
	public void setWinterHarvestFrozenGround(String winterHarvestFrozenGround) {
		this.winterHarvestFrozenGround = winterHarvestFrozenGround;
	}

	@JsonProperty("avoidSilvAvoidPlanting")
	public String getAvoidSilvAvoidPlanting() {
		return avoidSilvAvoidPlanting;
	}

	@JsonProperty("avoidSilvAvoidPlanting")
	public void setAvoidSilvAvoidPlanting(String avoidSilvAvoidPlanting) {
		this.avoidSilvAvoidPlanting = avoidSilvAvoidPlanting;
	}

	@JsonProperty("avoidSilvAvoidPlantingFN")
	public String getAvoidSilvAvoidPlantingFN() {
		return avoidSilvAvoidPlantingFN;
	}

	@JsonProperty("avoidSilvAvoidPlantingFN")
	public void setAvoidSilvAvoidPlantingFN(String avoidSilvAvoidPlantingFN) {
		this.avoidSilvAvoidPlantingFN = avoidSilvAvoidPlantingFN;
	}

	@JsonProperty("avoidSilvAvoidPlantingAIA")
	public String getAvoidSilvAvoidPlantingAIA() {
		return avoidSilvAvoidPlantingAIA;
	}

	@JsonProperty("avoidSilvAvoidPlantingAIA")
	public void setAvoidSilvAvoidPlantingAIA(String avoidSilvAvoidPlantingAIA) {
		this.avoidSilvAvoidPlantingAIA = avoidSilvAvoidPlantingAIA;
	}

	@JsonProperty("avoidSilvAvoidPlantingSP")
	public String getAvoidSilvAvoidPlantingSP() {
		return avoidSilvAvoidPlantingSP;
	}

	@JsonProperty("avoidSilvAvoidPlantingSP")
	public void setAvoidSilvAvoidPlantingSP(String avoidSilvAvoidPlantingSP) {
		this.avoidSilvAvoidPlantingSP = avoidSilvAvoidPlantingSP;
	}

	@JsonProperty("avoidSilvAvoidSitePrep")
	public String getAvoidSilvAvoidSitePrep() {
		return avoidSilvAvoidSitePrep;
	}

	@JsonProperty("avoidSilvAvoidSitePrep")
	public void setAvoidSilvAvoidSitePrep(String avoidSilvAvoidSitePrep) {
		this.avoidSilvAvoidSitePrep = avoidSilvAvoidSitePrep;
	}

	@JsonProperty("avoidSilvAvoidSitePrepFN")
	public String getAvoidSilvAvoidSitePrepFN() {
		return avoidSilvAvoidSitePrepFN;
	}

	@JsonProperty("avoidSilvAvoidSitePrepFN")
	public void setAvoidSilvAvoidSitePrepFN(String avoidSilvAvoidSitePrepFN) {
		this.avoidSilvAvoidSitePrepFN = avoidSilvAvoidSitePrepFN;
	}

	@JsonProperty("avoidSilvAvoidSitePrepAIA")
	public String getAvoidSilvAvoidSitePrepAIA() {
		return avoidSilvAvoidSitePrepAIA;
	}

	@JsonProperty("avoidSilvAvoidSitePrepAIA")
	public void setAvoidSilvAvoidSitePrepAIA(String avoidSilvAvoidSitePrepAIA) {
		this.avoidSilvAvoidSitePrepAIA = avoidSilvAvoidSitePrepAIA;
	}

	@JsonProperty("avoidSilvAvoidSitePrepSP")
	public String getAvoidSilvAvoidSitePrepSP() {
		return avoidSilvAvoidSitePrepSP;
	}

	@JsonProperty("avoidSilvAvoidSitePrepSP")
	public void setAvoidSilvAvoidSitePrepSP(String avoidSilvAvoidSitePrepSP) {
		this.avoidSilvAvoidSitePrepSP = avoidSilvAvoidSitePrepSP;
	}

}
