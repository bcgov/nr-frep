package ca.bc.gov.nrs.frep.mapper;

import ca.bc.gov.nrs.frep.entity.ChrAssociatedFeatureXref;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.entity.ChrChecklistAttachment;
import ca.bc.gov.nrs.frep.entity.ChrChecklistParticipation;
import ca.bc.gov.nrs.frep.entity.ChrFeatWindthrTreatXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureAgeXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureDamageAgentXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureIdentity;
import ca.bc.gov.nrs.frep.entity.ChrFeatureInfoSourceXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureLocationDetail;
import ca.bc.gov.nrs.frep.entity.ChrFeatureTypeXref;
import ca.bc.gov.nrs.frep.entity.ChrMgmtStrategyPlanned;
import ca.bc.gov.nrs.frep.entity.ChrMgmtStrategyUsed;
import ca.bc.gov.nrs.frep.entity.ForestClient;
import ca.bc.gov.nrs.frep.struct.v1.frep.AcceptedSite;
import ca.bc.gov.nrs.frep.struct.v1.frep.CheckList;
import ca.bc.gov.nrs.frep.struct.v1.frep.Contact;
import ca.bc.gov.nrs.frep.struct.v1.frep.Feature;
import ca.bc.gov.nrs.frep.struct.v1.frep.OtherPlannedManagementStrategy;
import ca.bc.gov.nrs.frep.struct.v1.frep.Picture;
import ca.bc.gov.nrs.frep.util.UuidUtils;
import ca.bc.gov.nrs.frep.ChrConstants;
import ca.bc.gov.nrs.frep.util.ChrDateUtils;
import ca.bc.gov.nrs.frep.util.ChrStringUtils;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.UUID;

public final class CheckListMapper extends FrepMapper {

	/*
	 * Map the passed objects to the the checklist resource object.
	 *
	 * @param  chrChecklist   The model object containing checklist data.
	 * @param  acceptedSite   Resource object populated with accepted site data.
	 * @param  userId         Id of a FREP user.
	 * @param  downloadedDate Date a checklist was downloaded.
	 * @return CheckList      The checklist resource object.
	 */
	public static CheckList getChecklist(ChrChecklist chrChecklist, AcceptedSite acceptedSite, String userId, String downloadedDate, String attachmentDirPath, String objStorageHost, String objStorageBucketName, String objStorageUser, String objStorageKey) throws Exception {
		CheckList resource = new CheckList();
		resource.setChecklistID(chrChecklist.getChrChecklistId().toString());
		resource.setRevisionCount(Long.toString(chrChecklist.getRevisionCount()));
// TODO downloadBy/downloadedDate params can be set outside this method on the resource directory... remove params and update directly to resource in CheckListAction for /takeChecklistOffine.
		resource.setDownloadedBy(userId);
		resource.setDownloadedDate(downloadedDate);

		// If device checkout guid exits convert the database stored byte representation to string and set on the resource.
		if (chrChecklist.getDeviceCheckoutGuid() != null) {
			UUID uuid = UuidUtils.asUuid(chrChecklist.getDeviceCheckoutGuid());
			resource.setDeviceCheckoutGuid(uuid.toString());
		}

		resource.setMasterList(acceptedSite.getMasterList());
		resource.setEffectiveYear(acceptedSite.getEffectiveYear());
		resource.setOrgUnitCode(acceptedSite.getOrgUnitCode());
		resource.setOrgUnitName(acceptedSite.getOrgUnitName());
		resource.setOrgUnitNo(acceptedSite.getOrgUnitNo());

		// The following checklist mappings are ordered as data appears in the DC. Screen mapping sections are identified
		// to help distinguish subject area of mappings...

		// 1. Opening Information ***************************************************************************************************************************************
		resource.setEvaluationDate(acceptedSite.getEvaluationDate());
		resource.setAssessedBy(chrChecklist.getAssessedBy());
		resource.setUpdateUserid(chrChecklist.getUpdateUserid());
		resource.setUpdateTimestamp(ChrDateUtils.formatDateTime(chrChecklist.getUpdateTimestamp()));
        // Already have these in acceptedSites, probably don't need to get
		resource.setDistrict(acceptedSite.getOrgUnitCode()+"-"+acceptedSite.getOrgUnitName());
		resource.setOpeningID(acceptedSite.getOpeningID());
		// Opening number (mapsheet designator, e.g. "93A 026 0.0 110") is the formatted mapsheet, not
		// the raw OPENING_NUMBER column — set by the service via THE.frep_formatted_mapsheet so it
		// matches the Biodiversity header and Accepted Sites list.
		resource.setLicensee(acceptedSite.getLicenseNumber());
		resource.setCuttingPermit(acceptedSite.getCuttingPermit());
		resource.setBlock(acceptedSite.getCutBlock());

		// Client can be null when there is no cutting permit/block numbers. Client number + name
		// mirror the Biodiversity header, which shows both.
		ForestClient forestClient = chrChecklist.getFrepResourceValue().getFrepSelectedSite().getForestClient();
		if (forestClient != null) {
			resource.setClient(forestClient.getClientNumber());
			resource.setClientName(forestClient.getClientName());
		}

		resource.setYearOfHarvest(ChrDateUtils.formatYear(chrChecklist.getFrepResourceValue().getFrepSelectedSite().getDisturbanceEndDate()));
		resource.setFirstNationName(chrChecklist.getFirstNationsPlacename());
		resource.setGeneralLocation(chrChecklist.getLocationDescription());
		resource.setStatus(chrChecklist.getFrepChecklistStatusCode().getFrepChecklistStatusCode());
		resource.setTargeted(isResourceTargeted(chrChecklist.getFrepResourceValue()));

		// Block Summary
		resource.setQ8WerethereoperationalfactorsthatlimitedCHRmanagementoptionsonthisblock(ChrStringUtils.indicatorToBooleanStr(chrChecklist.getLimitingOperatnlFactorsInd()));
		resource.setQ8Comments(chrChecklist.getLimitingOperatnlFactorsDesc());
		resource.setQ9WeretheremanagementstrategiesandorpracticesusedonthisblockthatwereparticularlyeffectiveinmanagingCHRvalues(ChrStringUtils.indicatorToBooleanStr(chrChecklist.getEffectiveStratsUsedInd()));
		resource.setQ9Comments(chrChecklist.getEffectiveStratsUsedDesc());
		resource.setQ10AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreduceimpactsonCHRvaluesonthisblock(ChrStringUtils.indicatorToBooleanStr(chrChecklist.getAlternateStratsAvailInd()));
		resource.setQ10Comments(chrChecklist.getAlternateStratsAvailDesc());
		if (chrChecklist.getChrSiteEvaluationCode() != null) {
			resource.setRating(chrChecklist.getChrSiteEvaluationCode().getChrSiteEvaluationCode());
		}
		if (chrChecklist.getFrepMrvaRatingCode() != null) {
			resource.setMrvaRatingCode(chrChecklist.getFrepMrvaRatingCode().getFrepMrvaRatingCode());
		}
		resource.setRatingRationale(chrChecklist.getEvaluationRatingRationale());

		// Comments
		resource.setCommentaires(chrChecklist.getBlockComments());

		// 2. First Nation and/or Proponent Contacts ********************************************************************************************************************
		if (chrChecklist.getChrChecklistParticipations() != null && chrChecklist.getChrChecklistParticipations().size() > 0) {
			LinkedHashSet<ChrChecklistParticipation> hset = new LinkedHashSet<ChrChecklistParticipation>(chrChecklist.getChrChecklistParticipations());
			Iterator<ChrChecklistParticipation> it = hset.iterator();
			while(it.hasNext()) {
				ChrChecklistParticipation ccParticipation = (ChrChecklistParticipation)it.next();
				Contact contact = new Contact();
				contact.setId(ccParticipation.getChrChecklistParticipant().getChrChecklistParticipantId().toString());
				contact.setFirstName(ccParticipation.getChrChecklistParticipant().getFirstName());
				contact.setLastName(ccParticipation.getChrChecklistParticipant().getLastName());
				contact.setRoleCode(ccParticipation.getChrParticipantRoleCode());
				contact.setOrganization(ccParticipation.getChrChecklistParticipant().getOrganizationName());
				contact.setContactedInd(ChrStringUtils.indicatorToBooleanStr(ccParticipation.getContactedInd()));
				contact.setContactedDate(ChrDateUtils.formatDate(ccParticipation.getContactedDate()));
				contact.setAttendingOnSiteInd(ChrStringUtils.indicatorToBooleanStr(ccParticipation.getAttendingOnSiteInd()));
				resource.getContacts().add(contact);
			}
		}

// 3. Summary of Known Cultural Heritage Sited and Features
		if (chrChecklist.getChrFeatureIdentities() != null && chrChecklist.getChrFeatureIdentities().size() > 0) {
			HashSet<ChrFeatureIdentity> hset = new HashSet<ChrFeatureIdentity>(chrChecklist.getChrFeatureIdentities());
			Iterator<ChrFeatureIdentity> it = hset.iterator();
			while(it.hasNext()) {
				ChrFeatureIdentity cFeatureIdentity = (ChrFeatureIdentity)it.next();
				Feature feature = new Feature();
				feature.setId(cFeatureIdentity.getChrFeatureId().toString());
				feature.setFeatureLabel(cFeatureIdentity.getFeatureLabel());
				feature.setCompositeFeatureInd(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getCompositeFeatureInd()));

				// Get composite feature
				HashSet<ChrFeatureIdentity> hsetChrFeatureIdentity = new HashSet<ChrFeatureIdentity>(chrChecklist.getChrFeatureIdentities());
				Iterator<ChrFeatureIdentity> itChrFeatureIdentity = hsetChrFeatureIdentity.iterator();
				while(itChrFeatureIdentity.hasNext()) {
					ChrFeatureIdentity chrFeatureIdentity = (ChrFeatureIdentity)itChrFeatureIdentity.next();
					if (chrFeatureIdentity.getChrFeatureId().equals(cFeatureIdentity.getCompositeChrFeatureIdentity())) {
						feature.setCompositeFeature(chrFeatureIdentity.getFeatureLabel());
						break;
					}
				}

				if (cFeatureIdentity.getChrFeatureClassCode() != null) {
					feature.setFeatureDescriptionCode(cFeatureIdentity.getChrFeatureClassCode().getChrFeatureClassCode());
				}
				feature.setFeatureComment(cFeatureIdentity.getComments());

				if (cFeatureIdentity.getChrFeatureInfoSourceXrefs() != null && cFeatureIdentity.getChrFeatureInfoSourceXrefs().size() > 0) {
					if (cFeatureIdentity.getChrFeatureInfoSourceXrefs().size() > 1) {
						throw new Exception("More than one CHR_FEATURE_INFO_SOURCE_XREF returned for feature when only 1 expected.");
					} else {
						HashSet<ChrFeatureInfoSourceXref> hsetFeatureInfoSourceXref = new HashSet<ChrFeatureInfoSourceXref>(cFeatureIdentity.getChrFeatureInfoSourceXrefs());
						Iterator<ChrFeatureInfoSourceXref> itChrFeatureInfoSourceXref = hsetFeatureInfoSourceXref.iterator();
						while(itChrFeatureInfoSourceXref.hasNext()) {
							ChrFeatureInfoSourceXref cFeatureInfoSourceXref = (ChrFeatureInfoSourceXref)itChrFeatureInfoSourceXref.next();
							feature.setFeatureInfoSourceCode(cFeatureInfoSourceXref.getChrFeatureInfoSourceCode().getChrFeatureInfoSourceCode());
						}
					}
				}

				// Associated Features
				if (cFeatureIdentity.getChrAssociatedFeatureXrefsForToChrFeatureId() != null && cFeatureIdentity.getChrAssociatedFeatureXrefsForToChrFeatureId().size() > 0) {
					HashSet<ChrAssociatedFeatureXref> hsetChrAssociatedFeatureXref = new HashSet<ChrAssociatedFeatureXref>(cFeatureIdentity.getChrAssociatedFeatureXrefsForToChrFeatureId());
					Iterator<ChrAssociatedFeatureXref> itChrAssociatedFeatureXref = hsetChrAssociatedFeatureXref.iterator();
					String[] associatedFeatures = new String[cFeatureIdentity.getChrAssociatedFeatureXrefsForToChrFeatureId().size()];
					int index = 0;
					while(itChrAssociatedFeatureXref.hasNext()) {
						ChrAssociatedFeatureXref chrAssociatedFeatureXref = (ChrAssociatedFeatureXref)itChrAssociatedFeatureXref.next();
						hsetChrFeatureIdentity = new HashSet<ChrFeatureIdentity>(chrChecklist.getChrFeatureIdentities());
						itChrFeatureIdentity = hsetChrFeatureIdentity.iterator();
						while(itChrFeatureIdentity.hasNext()) {
							ChrFeatureIdentity chrFeatureIdentity = (ChrFeatureIdentity)itChrFeatureIdentity.next();
							if (chrFeatureIdentity.getChrFeatureId().equals(chrAssociatedFeatureXref.getId().getFromChrFeatureId())) {
								associatedFeatures[index] = chrFeatureIdentity.getFeatureLabel();
								index++;
								break;
							}
						}
					}
					feature.setAssociatedFeatures(associatedFeatures);
				}

// 4. Site or Feature Description
				feature.setChrRegisteredSite(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getRegdArchaeologicalSiteInd()));
				feature.setPermit(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getPermitIssuedInd()));
				feature.setFeatureDescription(cFeatureIdentity.getChrFeatureDetail().getDescription());
				if (cFeatureIdentity.getChrFeatureDetail().getAreaWidthMeters() != null) {
					feature.setWidthofFeature(cFeatureIdentity.getChrFeatureDetail().getAreaWidthMeters().toString());
				}
				if (cFeatureIdentity.getChrFeatureDetail().getAreaLengthMeters() != null) {
					feature.setLengthofFeature(cFeatureIdentity.getChrFeatureDetail().getAreaLengthMeters().toString());
				}
				if (cFeatureIdentity.getChrFeatureDetail().getAreaHectares() != null) {
					feature.setAreaofFeature(cFeatureIdentity.getChrFeatureDetail().getAreaHectares().toString());
				}
				feature.setManagementStrategyFN(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getFnMgmtRecommendationsInd()));
				feature.setManagementStrategySP(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getSitePlanStratsRecommndInd()));
				feature.setBorden(cFeatureIdentity.getChrFeatureDetail().getBordenNo());

				if (cFeatureIdentity.getChrFeatureDetail().getChrFeatureTypeXrefs() != null && cFeatureIdentity.getChrFeatureDetail().getChrFeatureTypeXrefs().size() > 0) {
					HashSet<ChrFeatureTypeXref> hsetChrFeatureTypeXref = new HashSet<ChrFeatureTypeXref>(cFeatureIdentity.getChrFeatureDetail().getChrFeatureTypeXrefs());
					Iterator<ChrFeatureTypeXref> itChrFeatureTypeXref = hsetChrFeatureTypeXref.iterator();
					while(itChrFeatureTypeXref.hasNext()) {
						ChrFeatureTypeXref cFeatureTypeXref = (ChrFeatureTypeXref)itChrFeatureTypeXref.next();
						mapForChrFeatureTypeXref(feature, cFeatureTypeXref.getChrFeatureTypeCode().getChrFeatureTypeCode(), cFeatureTypeXref.getOtherDescription(), cFeatureTypeXref.getQuantity());
					}
				}

// 5. Location
				if (cFeatureIdentity.getChrFeatureDetail().getChrFeatureLocationDetails() != null && cFeatureIdentity.getChrFeatureDetail().getChrFeatureLocationDetails().size() > 0) {
					HashSet<ChrFeatureLocationDetail> hsetChrFeatureLocationDetail = new HashSet<ChrFeatureLocationDetail>(cFeatureIdentity.getChrFeatureDetail().getChrFeatureLocationDetails());
					Iterator<ChrFeatureLocationDetail> itrChrFeatureLocationDetail = hsetChrFeatureLocationDetail.iterator();
					while(itrChrFeatureLocationDetail.hasNext()) {
						ChrFeatureLocationDetail cFeatureLocationDetail = (ChrFeatureLocationDetail)itrChrFeatureLocationDetail.next();
						mapForChrFeatureLocationDetail(feature, cFeatureLocationDetail);
					}
				}

// 6. Age
				if (cFeatureIdentity.getChrFeatureDetail().getChrFeatureAgeXrefs() != null && cFeatureIdentity.getChrFeatureDetail().getChrFeatureAgeXrefs().size() > 0) {
					HashSet<ChrFeatureAgeXref> hsetChrFeatureAgeXref = new HashSet<ChrFeatureAgeXref>(cFeatureIdentity.getChrFeatureDetail().getChrFeatureAgeXrefs());
					Iterator<ChrFeatureAgeXref> itrChrFeatureAgeXref = hsetChrFeatureAgeXref.iterator();
					while(itrChrFeatureAgeXref.hasNext()) {
						ChrFeatureAgeXref cFeatureAgeXref = (ChrFeatureAgeXref)itrChrFeatureAgeXref.next();
						mapForChrFeatureAgeXref(feature, cFeatureAgeXref.getChrFeatureAgeCode().getChrFeatureAgeCode());
					}
				}
// 7. Management Planning
				feature.setPermit(cFeatureIdentity.getChrFeatureDetail().getPermitNumber());
				feature.setSitePermitIssued(ChrStringUtils.indicatorToBooleanStrInverseLogic(cFeatureIdentity.getChrFeatureDetail().getPermitIssuedInd()));
				if (cFeatureIdentity.getChrFeatureDetail().getChrMgmtStrategyPlanneds() != null && cFeatureIdentity.getChrFeatureDetail().getChrMgmtStrategyPlanneds().size() > 0) {
					HashSet<ChrMgmtStrategyPlanned> hsetChrMgmtStrategyPlanned = new HashSet<ChrMgmtStrategyPlanned>(cFeatureIdentity.getChrFeatureDetail().getChrMgmtStrategyPlanneds());
					Iterator<ChrMgmtStrategyPlanned> itrChrMgmtStrategyPlanned = hsetChrMgmtStrategyPlanned.iterator();
					while(itrChrMgmtStrategyPlanned.hasNext()) {
						ChrMgmtStrategyPlanned cMgmtStrategyPlanned = (ChrMgmtStrategyPlanned)itrChrMgmtStrategyPlanned.next();
						String chrReserveTypeCode = null;
						if (cMgmtStrategyPlanned.getChrReserveTypeCode() != null) {
							chrReserveTypeCode = cMgmtStrategyPlanned.getChrReserveTypeCode().getChrReserveTypeCode();
						}
						String bufferWidthMeters = null;
						if (cMgmtStrategyPlanned.getBufferWidthMeters() != null) {
							bufferWidthMeters = cMgmtStrategyPlanned.getBufferWidthMeters().toString();
						}
						mapForChrMgmtStrategyPlanned(feature,
								                     cMgmtStrategyPlanned.getChrMgmtStrategySourceCode().getChrMgmtStrategySourceCode(),
								                     cMgmtStrategyPlanned.getChrMgmtStrategyTypeCode().getChrMgmtStrategyTypeCode(),
								                     chrReserveTypeCode,
								                     bufferWidthMeters,
								                     cMgmtStrategyPlanned.getOtherStrategy());
					}
				}

// 8. Management Effectiveness
				feature.setForCompositeFeaturesInd(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getUniformStrategyAppliedInd()));
				feature.setUnabletoLocate(ChrStringUtils.indicatorToBooleanStrInverseLogic(cFeatureIdentity.getChrFeatureDetail().getFeatureLocatedInd()));
				feature.setNoManagement(ChrStringUtils.indicatorToBooleanStrInverseLogic(cFeatureIdentity.getChrFeatureDetail().getManagementAppliedInd()));
				if (cFeatureIdentity.getChrFeatureDetail().getChrMgmtStrategyUseds() != null && cFeatureIdentity.getChrFeatureDetail().getChrMgmtStrategyUseds().size() > 0) {
					HashSet<ChrMgmtStrategyUsed> hsetChrMgmtStrategyUsed = new HashSet<ChrMgmtStrategyUsed>(cFeatureIdentity.getChrFeatureDetail().getChrMgmtStrategyUseds());
					Iterator<ChrMgmtStrategyUsed> itrChrMgmtStrategyUsed = hsetChrMgmtStrategyUsed.iterator();
					while(itrChrMgmtStrategyUsed.hasNext()) {
						ChrMgmtStrategyUsed cMgmtStrategyUsed = (ChrMgmtStrategyUsed)itrChrMgmtStrategyUsed.next();
						String chrReserveTypeCode = null;
						if (cMgmtStrategyUsed.getChrReserveTypeCode() != null) {
							chrReserveTypeCode = cMgmtStrategyUsed.getChrReserveTypeCode().getChrReserveTypeCode();
						}
						String bufferWidthMeters = null;
						if (cMgmtStrategyUsed.getBufferWidthMeters() != null) {
							bufferWidthMeters = cMgmtStrategyUsed.getBufferWidthMeters().toString();
						}
						mapForChrMgmtStrategyUsed(feature,
								                  cMgmtStrategyUsed.getChrMgmtStrategyTypeCode().getChrMgmtStrategyTypeCode(),
								                  chrReserveTypeCode,
								                  bufferWidthMeters,
								                  cMgmtStrategyUsed.getFullyConservedInd(),
								                  cMgmtStrategyUsed.getOtherStrategy());
					}
				}

				// Q1 Is there evidence of damage to the site or feature.
				feature.setQ1Isthereevidenceofdamagetothesiteorfeature(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getEvidenceOfDamageInd()));
				if (cFeatureIdentity.getChrFeatureDetail().getChrFeatureDamageAgentXrefs() != null && cFeatureIdentity.getChrFeatureDetail().getChrFeatureDamageAgentXrefs().size() > 0) {
					HashSet<ChrFeatureDamageAgentXref> hsetChrFeatureDamageAgentXref = new HashSet<ChrFeatureDamageAgentXref>(cFeatureIdentity.getChrFeatureDetail().getChrFeatureDamageAgentXrefs());
					Iterator<ChrFeatureDamageAgentXref> itrChrFeatureDamageAgentXref = hsetChrFeatureDamageAgentXref.iterator();
					while(itrChrFeatureDamageAgentXref.hasNext()) {
						ChrFeatureDamageAgentXref chrFeatureDamageAgentXref = (ChrFeatureDamageAgentXref)itrChrFeatureDamageAgentXref.next();
						mapForChrFeatureDamageAgentXref(feature, chrFeatureDamageAgentXref.getChrFeatureDamageAgentCode().getChrFeatureDamageAgentCode(), chrFeatureDamageAgentXref.getOtherDescription());
					}
				}

				// Q3
				feature.setQ3Hasthesitebeenirreversiblydamagedorrenderedunsuitableforcontinueduse(cFeatureIdentity.getChrFeatureDetail().getDamageIrreversibleAnswerCd().getFrepChecklistAnswerCode());

				feature.setDescriptionofdamage(cFeatureIdentity.getChrFeatureDetail().getDamageDescription());

				// Windthrow management
				feature.setWindthrowManagement(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getWindthrowMgmtApplicableInd()));
				feature.setWindthrow(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getAreaWindfirmInd()));
				if (cFeatureIdentity.getChrFeatureDetail().getEstWindthrowPercent() != null) {
					feature.setEstwindthrow(cFeatureIdentity.getChrFeatureDetail().getEstWindthrowPercent().toString());
				}
				if (cFeatureIdentity.getChrFeatureDetail().getChrFeatWindthrTreatXrefs() != null && cFeatureIdentity.getChrFeatureDetail().getChrFeatWindthrTreatXrefs().size() > 0) {
					HashSet<ChrFeatWindthrTreatXref> hsetChrFeatWindthrTreatXref = new HashSet<ChrFeatWindthrTreatXref>(cFeatureIdentity.getChrFeatureDetail().getChrFeatWindthrTreatXrefs());
					Iterator<ChrFeatWindthrTreatXref> itrChrFeatWindthrTreatXref = hsetChrFeatWindthrTreatXref.iterator();
					while(itrChrFeatWindthrTreatXref.hasNext()) {
						ChrFeatWindthrTreatXref cFeatWindthrTreatXref = (ChrFeatWindthrTreatXref)itrChrFeatWindthrTreatXref.next();
						mapForChrFeatWindthrTreatXref(feature, cFeatWindthrTreatXref.getChrWindthrowTreatmentCode().getChrWindthrowTreatmentCode(), feature.getIfotherpleasedescribe());
					}
				}

				// Trail Features
				feature.setTrailfeatures(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getTrailFeaturesApplicableInd()));
				feature.setCanthetrailstillbelocated(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getTrailLocatableInd()));
				feature.setHasthetrailbeenmadelesspassble(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getTrailLessPassableInd()));
				feature.setIsthereevidenceofdamage(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getTrailAreaDamagedInd()));
				if (cFeatureIdentity.getChrFeatureDetail().getEstTrailDamagePercent() != null) {
					feature.setTrailLength(String.valueOf(cFeatureIdentity.getChrFeatureDetail().getEstTrailDamagePercent()));
				}

				// Summary
				feature.setQ4WerethereoperationalfactorthatlimitedCHRmanagementoptionsforthisfeature(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getLimitingOperatnlFactorsInd()));
				feature.setQ4Description(cFeatureIdentity.getChrFeatureDetail().getLimitingOperatnlFactorsDesc());
				feature.setQ5Weretheremanagementstrategiesandorpracticesusedforthisfeaturethatwereparticularlyeffective(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getEffectiveStratsUsedInd()));
				feature.setQ5Description(cFeatureIdentity.getChrFeatureDetail().getEffectiveStratsUsedDesc());
				feature.setQ6AretheremanagementstrategiesandorpracticesthatcouldhavebeenusedtoreducetheimpactonthisCHRfeature(ChrStringUtils.indicatorToBooleanStr(cFeatureIdentity.getChrFeatureDetail().getAlternateStratsAvailInd()));
				feature.setQ6Description(cFeatureIdentity.getChrFeatureDetail().getAlternateStratsAvailDesc());
				if (cFeatureIdentity.getChrFeatureDetail().getChrSiteEvaluationCode() != null) {
					feature.setFeatureRating(cFeatureIdentity.getChrFeatureDetail().getChrSiteEvaluationCode().getChrSiteEvaluationCode());
				}
				feature.setFeatureRatingRationale(cFeatureIdentity.getChrFeatureDetail().getEvaluationRatingRationale());

				resource.getFeatures().add(feature);
			}
		}

// photos
		LinkedHashSet<ChrChecklistAttachment> hsetChrChecklistAttachment =
				new LinkedHashSet<>(chrChecklist.getChrChecklistAttachments());
		Iterator<ChrChecklistAttachment> itrChrChecklistAttachment = hsetChrChecklistAttachment.iterator();
		while (itrChrChecklistAttachment.hasNext()) {
			ChrChecklistAttachment cChecklistAttachment = (ChrChecklistAttachment) itrChrChecklistAttachment.next();
			Picture picture = new Picture();
			picture.setId(cChecklistAttachment.getChrchecklistAttachmentId().toString());
			picture.setDescription(cChecklistAttachment.getDescription());
			picture.setMimeTypeCode("image/" + cChecklistAttachment.getMimeTypeCode().toLowerCase());
			picture.setFileName(cChecklistAttachment.getFileName());
			picture.setDate(ChrDateUtils.formatDate(cChecklistAttachment.getFileDate()));
			resource.getPictures().add(picture);
		}

		return resource;
	}

	/*
	 * This method handles the mapping of ChrFeatureTypeCode records to associated field indicators.
	 * @param feature            The feature to which a chrFeatureTypeCode is to be mapped.
	 * @param chrFeatureTypeCode The chrFeatureTypeCode code that is mapped to an indicator.
	 */
	private static void mapForChrFeatureTypeXref (Feature feature, String chrFeatureTypeCode, String otherDescription, Long quantity) {
		switch (chrFeatureTypeCode) {
			case ChrConstants.ChrFeatureTypeCode.CTDESIG:
				feature.setCulturalTraildesignated("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.CTUNDESIG:
				feature.setCulturalTrailundesignated("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.BURIALSITE:
				feature.setBurialSite("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.NEST:
				feature.setNest("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.CERMSITE:
				feature.setCeremonialSite("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.CREMATSITE:
				feature.setCremationSite("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.CAVE:
				feature.setCaveorotherKarst("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.DEN:
				feature.setDen("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.TUS:
				feature.setTraditionalUseSite("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.CEDARSTRIP:
				feature.setCedarBarkStripArea("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.ROCKOUTCRP:
				feature.setRockOutcrop("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.SPIRSITE:
				feature.setSpiritualSite("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.CMT:
				feature.setOfCMTs("true");
				if (quantity != null) {
					feature.setOfCMTsNumber(Long.toString(quantity));
				}
				break;
			case ChrConstants.ChrFeatureTypeCode.MONCEDAR:
				feature.setOfMonumentalCedars("true");
				if (quantity != null) {
					feature.setStandofMonumentalCedar(quantity.toString());
				}
				break;
			case ChrConstants.ChrFeatureTypeCode.CULTDEP:
				feature.setCulturalDepression("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.LITHICS:
				feature.setLithics("true");
				break;
			case ChrConstants.ChrFeatureTypeCode.OTH:
				feature.setOther("true");
				feature.setOtherDescription(otherDescription);
				break;
		}
	}

	/*
	 * This method handles the mapping of ChrFeatureAgeXref records to associated field indicators.
	 * @param feature           The feature to which a chrFeatureAgeCode is to be mapped.
	 * @param chrFeatureAgeCode The chrFeatureAgeCode that is mapped to an indicator.
	 */
	private static void mapForChrFeatureAgeXref (Feature feature, String chrFeatureAgeCode) {
		switch (chrFeatureAgeCode) {
			case ChrConstants.ChrFeatureAgeCode.PRE1846:
				feature.setPre1846("true");
				break;
			case ChrConstants.ChrFeatureAgeCode.POST1846:
				feature.setPost1846("true");
				break;
			case ChrConstants.ChrFeatureAgeCode.UNK:
				feature.setAgeUnknown("true");
				break;
			case ChrConstants.ChrFeatureAgeCode.HIST:
				feature.setHistoricalUse("true");
				break;
		}
	}

	private static void mapForChrFeatureLocationDetail(Feature feature, ChrFeatureLocationDetail cFeatureLocationDetail) {
		switch (cFeatureLocationDetail.getChrFeatureLocnContextCode().getChrFeatureLocnContextCode()) {
			case ChrConstants.ChrFeatureLocnContextCode.INHARV:
				feature.setInharvestedarea("true");
				break;
			case ChrConstants.ChrFeatureLocnContextCode.ADJBLK:
				feature.setAdjacenttoblock("true");
				break;
			case ChrConstants.ChrFeatureLocnContextCode.ADJWTR:
				feature.setAdjacenttowater("true");
				break;
			case ChrConstants.ChrFeatureLocnContextCode.OTH:
				feature.setLocationOther("true");
				feature.setLocationOtherDescription(cFeatureLocationDetail.getOtherDescription());
				break;
			case ChrConstants.ChrFeatureLocnContextCode.CUTBLK:
				feature.setEntirecutblock("true");
				break;
			case ChrConstants.ChrFeatureLocnContextCode.RESERV:
				feature.setInReserve("true");
				if (cFeatureLocationDetail.getChrReserveTypeCode() != null) {
					feature.setLocationReservetype(cFeatureLocationDetail.getChrReserveTypeCode().getChrReserveTypeCode());
				}
				break;
		}
	}

	/*
	 * This method handles the mapping of ChrMgmtStrategyPlanned records to associated field indicators.
	 * @param feature                  The feature to which a chrFeatureAgeCode is to be mapped.
	 * @param crMgmtStrategySourceCode The crMgmtStrategySourceCode that is mapped to an indicator.
	 * @param chrMgmtStrategyTypeCode  The chrMgmtStrategyTypeCode that is mapped to an indicator.
	 */
	private static void mapForChrMgmtStrategyPlanned(Feature feature, String crMgmtStrategySourceCode, String chrMgmtStrategyTypeCode, String chrReserveTypeCode, String bufferWidthMeters, String otherStrategy) {
		switch (crMgmtStrategySourceCode) {
			case ChrConstants.ChrMgmtStrategySourceCode.FN:
				mapForChrMgmtStrategyPlannedFN(feature, chrMgmtStrategyTypeCode, chrReserveTypeCode, bufferWidthMeters, otherStrategy);
				break;
			case ChrConstants.ChrMgmtStrategySourceCode.AIASAP:
				mapForChrMgmtStrategyPlannedAIA(feature, chrMgmtStrategyTypeCode, chrReserveTypeCode, bufferWidthMeters, otherStrategy);
				break;
			case ChrConstants.ChrMgmtStrategySourceCode.SP:
				mapForChrMgmtStrategyPlannedSP(feature, chrMgmtStrategyTypeCode, chrReserveTypeCode, bufferWidthMeters, otherStrategy);
				break;
		}
	}

	/*
	 * This method handles the mappings for ChrMgmtStrategyPlanned records of chrMgmtStrategySourceCode = FRSTNATION.
	 * NOTE: Including this one, there are 3 methods which follow the same pattern for each of the chrMgmtStrategySourceCode codes.
	 *       This is due to the fact the client codifies meaning into its name while also each indicator is a record in the database.
	 *       This isn't the greatest pattern however its what we current have to work with. If we add more chrMgmtStrategySourceCode codes
	 *       we can look at using java reflection to reduce this code pattern reappearing.
	 */
	private static void mapForChrMgmtStrategyPlannedFN (Feature feature, String chrMgmtStrategyTypeCode, String chrReserveTypeCode, String bufferWidthMeters, String otherStrategy) {
		switch (chrMgmtStrategyTypeCode) {
			case ChrConstants.ChrMgmtStrategyTypeCode.MODBLOCK:
				feature.setModifyBlockBoundaryFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.BUFFER:
				feature.setRetainBufferFN("true");
				feature.setBufferLengthFN(bufferWidthMeters);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.NOBUFF:
				feature.setRetaininHarvestAreaFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.CROWNMOD:
				feature.setCrownorstandmodificationFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES:
				feature.setConserveinRotationalReserveFN("true");
				feature.setConserveRotationalReserveTypeFN(chrReserveTypeCode);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES:
				feature.setPermanentReserveFN("true");
				feature.setTemporaryRetentionTypeFN(chrReserveTypeCode);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.DATEFEAT:
				feature.setDatetheFeatureFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.STUBCMT:
				feature.setStubCMTsabovescarFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.STUBNONCMT:
				feature.setStubnonCMTsFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.LEAVESTAND:
				feature.setLeaveStandingFN("true");
				break;
//TODO remove once we know for sure... srpint 2
/*
			case ChrConstants.ChrMgmtStrategyTypeCode.ALTERSILV:
				feature.setAltersilvicultureFN("true");
				break;
*/
			case ChrConstants.ChrMgmtStrategyTypeCode.AVOIDPLNT:
				feature.setAvoidSilvAvoidPlantingFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.AVOIDSPREP:
				feature.setAvoidSilvAvoidSitePrepFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.MFZ:
				feature.setMachineFreeZoneFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.HARVSTSAP:
				feature.setHarvestUnderSapFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.WINTERHARV:
				feature.setWinterHarvestFrozenGroundFN("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.OTH:
			    boolean exists = false;
				for (OtherPlannedManagementStrategy opms: feature.getOtherPlannedManagementStrategy()) {
					if (ChrStringUtils.hasAValue(otherStrategy) && opms.getOtherStrategy().equals(otherStrategy)) {
						opms.setFnInd("true");
						exists = true;
						break;
					}
				}

				if (!exists) {
					OtherPlannedManagementStrategy opms = new OtherPlannedManagementStrategy (otherStrategy, "true", "false", "false");
					feature.getOtherPlannedManagementStrategy().add(opms);
				}

				break;
		}
	}

	/*
	 * This method handles the mappings for ChrMgmtStrategyPlanned records of chrMgmtStrategySourceCode = AIA.
	 * NOTE: Including this one, there are 3 methods which follow the same pattern for each of the chrMgmtStrategySourceCode codes.
	 *       This is due to the fact the client codifies meaning into its name while also each indicator is a record in the database.
	 *       This isn't the greatest pattern however its what we current have to work with. If we add more chrMgmtStrategySourceCode codes
	 *       we can look at using java reflection to reduce this code pattern reappearing.
	 */
	private static void mapForChrMgmtStrategyPlannedAIA (Feature feature, String chrMgmtStrategyTypeCode, String chrReserveTypeCode, String bufferWidthMeters, String otherStrategy) {
		switch (chrMgmtStrategyTypeCode) {
			case ChrConstants.ChrMgmtStrategyTypeCode.MODBLOCK:
				feature.setModifyBlockBoundaryAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.BUFFER:
				feature.setRetainBufferAIA("true");
				feature.setBufferLengthAIA(bufferWidthMeters);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.NOBUFF:
				feature.setRetaininHarvestAreaAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.CROWNMOD:
				feature.setCrownorstandmodificationAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES:
				feature.setConserveinRotationalReserveAIA("true");
				feature.setConserveRotationalReserveTypeAIA(chrReserveTypeCode);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES:
				feature.setPermanentReserveAIA("true");
				feature.setTemporaryRetentionTypeAIA(chrReserveTypeCode);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.DATEFEAT:
				feature.setDatetheFeatureAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.STUBCMT:
				feature.setStubCMTsabovescarAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.STUBNONCMT:
				feature.setStubnonCMTsAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.LEAVESTAND:
				feature.setLeaveStandingAIA("true");
				break;
/*
			case ChrConstants.ChrMgmtStrategyTypeCode.ALTERSILV:
				feature.setAltersilvicultureAIA("true");
				break;
*/
			case ChrConstants.ChrMgmtStrategyTypeCode.AVOIDPLNT:
				feature.setAvoidSilvAvoidPlantingAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.AVOIDSPREP:
				feature.setAvoidSilvAvoidSitePrepAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.MFZ:
				feature.setMachineFreeZoneAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.HARVSTSAP:
				feature.setHarvestUnderSapAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.WINTERHARV:
				feature.setWinterHarvestFrozenGroundAIA("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.OTH:
			    boolean exists = false;
				for (OtherPlannedManagementStrategy opms: feature.getOtherPlannedManagementStrategy()) {
					if (ChrStringUtils.hasAValue(otherStrategy) && opms.getOtherStrategy().equals(otherStrategy)) {
						opms.setAiaInd("true");
						exists = true;
						break;
					}
				}

				if (!exists) {
					OtherPlannedManagementStrategy opms = new OtherPlannedManagementStrategy (otherStrategy, "false", "true", "false");
					feature.getOtherPlannedManagementStrategy().add(opms);
				}

				break;
		}
	}

	/*
	 * This method handles the mappings for ChrMgmtStrategyPlanned records of chrMgmtStrategySourceCode = SITEPLAN.
	 * NOTE: Including this one, there are 3 methods which follow the same pattern for each of the chrMgmtStrategySourceCode codes.
	 *       This is due to the fact the client codifies meaning into its name while also each indicator is a record in the database.
	 *       This isn't the greatest pattern however its what we current have to work with. If we add more chrMgmtStrategySourceCode codes
	 *       we can look at using java reflection to reduce this code pattern reappearing.
	 */
	private static void mapForChrMgmtStrategyPlannedSP (Feature feature, String chrMgmtStrategyTypeCode, String chrReserveTypeCode, String bufferWidthMeters, String otherStrategy) {
		switch (chrMgmtStrategyTypeCode) {
			case ChrConstants.ChrMgmtStrategyTypeCode.MODBLOCK:
				feature.setModifyBlockBoundarySP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.BUFFER:
				feature.setRetainBufferSP("true");
				feature.setBufferLengthSP(bufferWidthMeters);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.NOBUFF:
				feature.setRetaininHarvestAreaSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.CROWNMOD:
				feature.setCrownorstandmodificationSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES:
				feature.setConserveinRotationalReserveSP("true");
				feature.setConserveRotationalReserveTypeSP(chrReserveTypeCode);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES:
				feature.setPermanentReserveSP("true");
				feature.setTemporaryRetentionTypeSP(chrReserveTypeCode);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.DATEFEAT:
				feature.setDatetheFeatureSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.STUBCMT:
				feature.setStubCMTsabovescarSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.STUBNONCMT:
				feature.setStubnonCMTsSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.LEAVESTAND:
				feature.setLeaveStandingSP("true");
				break;
/*
			case ChrConstants.ChrMgmtStrategyTypeCode.ALTERSILV:
				feature.setAltersilvicultureSP("true");
				break;
*/
			case ChrConstants.ChrMgmtStrategyTypeCode.AVOIDPLNT:
				feature.setAvoidSilvAvoidPlantingSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.AVOIDSPREP:
				feature.setAvoidSilvAvoidSitePrepSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.MFZ:
				feature.setMachineFreeZoneSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.HARVSTSAP:
				feature.setHarvestUnderSapSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.WINTERHARV:
				feature.setWinterHarvestFrozenGroundSP("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.OTH:
			    boolean exists = false;
				for (OtherPlannedManagementStrategy opms: feature.getOtherPlannedManagementStrategy()) {
					if (ChrStringUtils.hasAValue(otherStrategy) && opms.getOtherStrategy().equals(otherStrategy)) {
						opms.setSpInd("true");
						exists = true;
						break;
					}
				}

				if (!exists) {
					OtherPlannedManagementStrategy opms = new OtherPlannedManagementStrategy (otherStrategy, "false", "false", "true");
					feature.getOtherPlannedManagementStrategy().add(opms);
				}

				break;
		}
	}

	private static void mapForChrMgmtStrategyUsed(Feature feature, String chrMgmtStrategyTypeCode, String chrReserveTypeCode, String bufferWidthMeters, String fullConservedInd, String otherStrategy) {
		switch (chrMgmtStrategyTypeCode) {
			case ChrConstants.ChrMgmtStrategyTypeCode.TEMPRES:
				if (fullConservedInd.equals("N")) {
					feature.setPartiallytemporaryreserve("true");
					feature.setPartiallytemporaryreservetype(chrReserveTypeCode);
				} else if (fullConservedInd.equals("Y")) {
					feature.setFullyconservedintemporaryreserve("true");
					feature.setFullytemporaryreserve(chrReserveTypeCode);
				}
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.ROTATRES:
				if (fullConservedInd.equals("N")) {
					feature.setPartiallyconservedinpermanentreserve("true");
					feature.setPartiallyconservedinpermanentreserveType(chrReserveTypeCode);
				} else if (fullConservedInd.equals("Y")) {
					feature.setFullyconservedinpermanentreserve("true");
					feature.setFullyconservedinpermanentreserveType(chrReserveTypeCode);
				}
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.MODBLOCK:
				feature.setModifiedblockboundary("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.BUFFER:
				feature.setRetainabuffer("true");
				feature.setBufferWidthMeter(bufferWidthMeters);
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.CROWNMOD:
				feature.setCompledCrownorstandmodification("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.DATEFEAT:
				feature.setDatedthefeature("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.NOBUFF:
				feature.setRetainedinharvestareanobuffer("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.LEAVESTAND:
				feature.setLeftStanding("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.STUBCMT:
				feature.setStubbed("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.STUBNONCMT:
				feature.setStubbedNon("true");
				break;
//TODO remove once we know for sure...
/*
			case ChrConstants.ChrMgmtStrategyTypeCode.ALTERSILV:
				feature.setAlteredsilviculture("true");
				break;
*/
			case ChrConstants.ChrMgmtStrategyTypeCode.AVOIDPLNT:
				feature.setAvoidSilvAvoidPlanting("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.AVOIDSPREP:
				feature.setAvoidSilvAvoidSitePrep("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.MFZ:
				feature.setMachineFreeZone("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.HARVSTSAP:
				feature.setHarvestUnderSap("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.WINTERHARV:
				feature.setWinterHarvestFrozenGround("true");
				break;
			case ChrConstants.ChrMgmtStrategyTypeCode.OTH:
				feature.setOtherActivities(otherStrategy);
				break;
		}
	}

	private static void mapForChrFeatureDamageAgentXref(Feature feature, String chrFeatureDamageAgentCode, String otherDescription) {
		switch (chrFeatureDamageAgentCode) {
			case ChrConstants.ChrFeatureDamageAgentCode.HARV:
				feature.setHarvestingQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.SAFETY:
				feature.setSafetyQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.SIL:
				feature.setSilvicultureQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.RECUSE:
				feature.setRecreationQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.FIRE:
				feature.setFireQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.INDUSTR:
				feature.setIndustrialUseQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.ROADBD:
				feature.setRoadQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.LVS:
				feature.setLivestockQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.WINDTHR:
				feature.setWindthrowQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				break;
			case ChrConstants.ChrFeatureDamageAgentCode.OTH:
				feature.setOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause("true");
				feature.setIfotherpleasedescribeOtherQ2Wheredamagehasoccurredwhatisthemostlikelycause(otherDescription);
				break;
		}
	}

	private static void mapForChrFeatWindthrTreatXref(Feature feature, String chrWindthrowTreatmentCode, String otherDescription) {
		switch (chrWindthrowTreatmentCode) {
			case ChrConstants.ChrWindthrowTreatmentCode.NONE:
				feature.setWindthrowTechniqueNone("true");
				break;
			case ChrConstants.ChrWindthrowTreatmentCode.BUFFER:
				feature.setWindthrowTechniqueRetentionBuffer("true");
				break;
			case ChrConstants.ChrWindthrowTreatmentCode.PRUNING:
				feature.setWindthrowTechniquePruning("true");
				break;
			case ChrConstants.ChrWindthrowTreatmentCode.FEATHERING:
				feature.setWindthrowTechniqueFeathering("true");
				break;
			case ChrConstants.ChrWindthrowTreatmentCode.TOPPING:
				feature.setWindthrowTechniqueTopping("true");
				break;
			case ChrConstants.ChrWindthrowTreatmentCode.OTHER:
				feature.setOtherTechnique("true");
				feature.setIfotherpleasedescribe(otherDescription);
				break;
		}
	}

}

