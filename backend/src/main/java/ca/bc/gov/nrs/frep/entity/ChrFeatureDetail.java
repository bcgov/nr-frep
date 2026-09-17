package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_FEATURE_DETAIL", schema = "THE")
public class ChrFeatureDetail implements java.io.Serializable {

	@Id
	@Column(name = "CHR_FEATURE_ID")
	private long chrFeatureId;
	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "CHR_FEATURE_ID")
	private ChrFeatureIdentity chrFeatureIdentity;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_SITE_EVALUATION_CODE")
	private ChrSiteEvaluationCode chrSiteEvaluationCode;
	@Column(name = "DESCRIPTION")
	private String description;
	@Column(name = "AREA_WIDTH_METERS")
	private BigDecimal areaWidthMeters;
	@Column(name = "AREA_LENGTH_METERS")
	private BigDecimal areaLengthMeters;
	@Column(name = "AREA_HECTARES")
	private BigDecimal areaHectares;
	@Column(name = "REGD_ARCHAEOLOGICAL_SITE_IND")
	private String regdArchaeologicalSiteInd;
	@Column(name = "BORDEN_NO")
	private String bordenNo;
	@Column(name = "FN_MGMT_RECOMMENDATIONS_IND")
	private String fnMgmtRecommendationsInd;
	@Column(name = "PERMIT_ISSUED_IND")
	private String permitIssuedInd;
	@Column(name = "PERMIT_NUMBER")
	private String permitNumber;
	@Column(name = "SITE_PLAN_STRATS_RECOMMND_IND")
	private String sitePlanStratsRecommndInd;
	@Column(name = "FEATURE_LOCATED_IND")
	private String featureLocatedInd;
	@Column(name = "UNIFORM_STRATEGY_APPLIED_IND")
	private String uniformStrategyAppliedInd;
	@Column(name = "MANAGEMENT_APPLIED_IND")
	private String managementAppliedInd;
	@Column(name = "EVIDENCE_OF_DAMAGE_IND")
	private String evidenceOfDamageInd;
	@Column(name = "DAMAGE_DESCRIPTION")
	private String damageDescription;

	// TODO REMOVE...
	//private String damageIrreversibleInd;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "DAMAGE_IRREVERSIBLE_ANSWER_CD")
	private FrepChecklistAnswerCode damageIrreversibleAnswerCd;

	@Column(name = "WINDTHROW_MGMT_APPLICABLE_IND")
	private String windthrowMgmtApplicableInd;
	@Column(name = "AREA_WINDFIRM_IND")
	private String areaWindfirmInd;
	@Column(name = "TRAIL_FEATURES_APPLICABLE_IND")
	private String trailFeaturesApplicableInd;
	@Column(name = "TRAIL_LOCATABLE_IND")
	private String trailLocatableInd;
	@Column(name = "TRAIL_AREA_DAMAGED_IND")
	private String trailAreaDamagedInd;
	@Column(name = "TRAIL_LESS_PASSABLE_IND")
	private String trailLessPassableInd;
	@Column(name = "EST_TRAIL_DAMAGE_PERCENT")
	private Short estTrailDamagePercent;
	@Column(name = "EST_WINDTHROW_PERCENT")
	private Short estWindthrowPercent;
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
	// Inert while CheckListMapper walks feature-by-feature (no sibling collections are
	// registered when one is initialised); effective as soon as the details are loaded up
	// front. Kept so that fix does not also have to remember this.
	@BatchSize(size = 25)
	@OneToMany(targetEntity = ChrMgmtStrategyUsed.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrMgmtStrategyUseds = new HashSet(0);
	// Inert while CheckListMapper walks feature-by-feature (no sibling collections are
	// registered when one is initialised); effective as soon as the details are loaded up
	// front. Kept so that fix does not also have to remember this.
	@BatchSize(size = 25)
	@OneToMany(targetEntity = ChrMgmtStrategyPlanned.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrMgmtStrategyPlanneds = new HashSet(0);
	// Inert while CheckListMapper walks feature-by-feature (no sibling collections are
	// registered when one is initialised); effective as soon as the details are loaded up
	// front. Kept so that fix does not also have to remember this.
	@BatchSize(size = 25)
	@OneToMany(targetEntity = ChrFeatureAgeXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrFeatureAgeXrefs = new HashSet(0);
	// Inert while CheckListMapper walks feature-by-feature (no sibling collections are
	// registered when one is initialised); effective as soon as the details are loaded up
	// front. Kept so that fix does not also have to remember this.
	@BatchSize(size = 25)
	@OneToMany(targetEntity = ChrFeatureLocationDetail.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrFeatureLocationDetails = new HashSet(0);
	// Inert while CheckListMapper walks feature-by-feature (no sibling collections are
	// registered when one is initialised); effective as soon as the details are loaded up
	// front. Kept so that fix does not also have to remember this.
	@BatchSize(size = 25)
	@OneToMany(targetEntity = ChrFeatureTypeXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrFeatureTypeXrefs = new HashSet(0);
	// Inert while CheckListMapper walks feature-by-feature (no sibling collections are
	// registered when one is initialised); effective as soon as the details are loaded up
	// front. Kept so that fix does not also have to remember this.
	@BatchSize(size = 25)
	@OneToMany(targetEntity = ChrFeatWindthrTreatXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrFeatWindthrTreatXrefs = new HashSet(0);
	// Inert while CheckListMapper walks feature-by-feature (no sibling collections are
	// registered when one is initialised); effective as soon as the details are loaded up
	// front. Kept so that fix does not also have to remember this.
	@BatchSize(size = 25)
	@OneToMany(targetEntity = ChrFeatureDamageAgentXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_ID", insertable = false, updatable = false)
	private Set chrFeatureDamageAgentXrefs = new HashSet(0);

	public ChrFeatureDetail() {
	}

	public ChrFeatureDetail(ChrFeatureIdentity chrFeatureIdentity, String regdArchaeologicalSiteInd,
			String fnMgmtRecommendationsInd, String permitIssuedInd, String sitePlanStratsRecommndInd,
			String featureLocatedInd, String uniformStrategyAppliedInd, String managementAppliedInd,
			String evidenceOfDamageInd, String damageIrreversibleInd, String windthrowMgmtApplicableInd,
			String areaWindfirmInd, String trailFeaturesApplicableInd, String trailLocatableInd,
			String trailAreaDamagedInd, String trailLessPassableInd, String limitingOperatnlFactorsInd,
			String effectiveStratsUsedInd, String alternateStratsAvailInd, String evaluationRatingRationale,
			String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp, long revisionCount) {
		this.chrFeatureIdentity = chrFeatureIdentity;
		this.regdArchaeologicalSiteInd = regdArchaeologicalSiteInd;
		this.fnMgmtRecommendationsInd = fnMgmtRecommendationsInd;
		this.permitIssuedInd = permitIssuedInd;
		this.sitePlanStratsRecommndInd = sitePlanStratsRecommndInd;
		this.featureLocatedInd = featureLocatedInd;
		this.uniformStrategyAppliedInd = uniformStrategyAppliedInd;
		this.managementAppliedInd = managementAppliedInd;
		this.evidenceOfDamageInd = evidenceOfDamageInd;
		//this.damageIrreversibleInd = damageIrreversibleInd;
		this.windthrowMgmtApplicableInd = windthrowMgmtApplicableInd;
		this.areaWindfirmInd = areaWindfirmInd;
		this.trailFeaturesApplicableInd = trailFeaturesApplicableInd;
		this.trailLocatableInd = trailLocatableInd;
		this.trailAreaDamagedInd = trailAreaDamagedInd;
		this.trailLessPassableInd = trailLessPassableInd;
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

	public ChrFeatureDetail(ChrFeatureIdentity chrFeatureIdentity, ChrSiteEvaluationCode chrSiteEvaluationCode,
			String description, BigDecimal areaWidthMeters, BigDecimal areaLengthMeters, BigDecimal areaHectares,
			String regdArchaeologicalSiteInd, String bordenNo, String fnMgmtRecommendationsInd, String permitIssuedInd,
			String permitNumber, String sitePlanStratsRecommndInd, String featureLocatedInd,
			String uniformStrategyAppliedInd, String managementAppliedInd, String evidenceOfDamageInd,
			String damageDescription, String damageIrreversibleInd,
			FrepChecklistAnswerCode damageIrreversibleAnswerCd,
			String windthrowMgmtApplicableInd,
			String areaWindfirmInd, String otherWindthrowMgmtDesc, String trailFeaturesApplicableInd,
			String trailLocatableInd, String trailAreaDamagedInd, String trailLessPassableInd,
			Short estTrailDamagePercent,Short estWindthrowPercent,
			String limitingOperatnlFactorsInd, String limitingOperatnlFactorsDesc,
			String effectiveStratsUsedInd, String effectiveStratsUsedDesc, String alternateStratsAvailInd,
			String alternateStratsAvailDesc, String evaluationRatingRationale, String entryUserid, Date entryTimestamp,
			String updateUserid, Date updateTimestamp, long revisionCount, Set chrMgmtStrategyUseds,
			Set chrMgmtStrategyPlanneds, Set chrFeatureAgeXrefs, Set chrFeatureLocationDetails, Set chrFeatureTypeXrefs,
			Set chrFeatWindthrTreatXrefs) {
		this.chrFeatureIdentity = chrFeatureIdentity;
		this.chrSiteEvaluationCode = chrSiteEvaluationCode;
		this.description = description;
		this.areaWidthMeters = areaWidthMeters;
		this.areaLengthMeters = areaLengthMeters;
		this.areaHectares = areaHectares;
		this.regdArchaeologicalSiteInd = regdArchaeologicalSiteInd;
		this.bordenNo = bordenNo;
		this.fnMgmtRecommendationsInd = fnMgmtRecommendationsInd;
		this.permitIssuedInd = permitIssuedInd;
		this.permitNumber = permitNumber;
		this.sitePlanStratsRecommndInd = sitePlanStratsRecommndInd;
		this.featureLocatedInd = featureLocatedInd;
		this.uniformStrategyAppliedInd = uniformStrategyAppliedInd;
		this.managementAppliedInd = managementAppliedInd;
		this.evidenceOfDamageInd = evidenceOfDamageInd;
		this.damageDescription = damageDescription;
		//this.damageIrreversibleInd = damageIrreversibleInd;
		this.damageIrreversibleAnswerCd = damageIrreversibleAnswerCd;
		this.windthrowMgmtApplicableInd = windthrowMgmtApplicableInd;
		this.areaWindfirmInd = areaWindfirmInd;
		this.trailFeaturesApplicableInd = trailFeaturesApplicableInd;
		this.trailLocatableInd = trailLocatableInd;
		this.trailAreaDamagedInd = trailAreaDamagedInd;
		this.trailLessPassableInd = trailLessPassableInd;
		this.estTrailDamagePercent = estTrailDamagePercent;
		this.estWindthrowPercent = estWindthrowPercent;
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
		this.chrMgmtStrategyUseds = chrMgmtStrategyUseds;
		this.chrMgmtStrategyPlanneds = chrMgmtStrategyPlanneds;
		this.chrFeatureAgeXrefs = chrFeatureAgeXrefs;
		this.chrFeatureLocationDetails = chrFeatureLocationDetails;
		this.chrFeatureTypeXrefs = chrFeatureTypeXrefs;
		this.chrFeatWindthrTreatXrefs = chrFeatWindthrTreatXrefs;
	}

	public long getChrFeatureId() {
		return this.chrFeatureId;
	}

	public void setChrFeatureId(long chrFeatureId) {
		this.chrFeatureId = chrFeatureId;
	}

	public ChrFeatureIdentity getChrFeatureIdentity() {
		return this.chrFeatureIdentity;
	}

	public void setChrFeatureIdentity(ChrFeatureIdentity chrFeatureIdentity) {
		this.chrFeatureIdentity = chrFeatureIdentity;
	}

	public ChrSiteEvaluationCode getChrSiteEvaluationCode() {
		return chrSiteEvaluationCode;
	}

	public void setChrSiteEvaluationCode(ChrSiteEvaluationCode chrSiteEvaluationCode) {
		this.chrSiteEvaluationCode = chrSiteEvaluationCode;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getAreaWidthMeters() {
		return this.areaWidthMeters;
	}

	public void setAreaWidthMeters(BigDecimal areaWidthMeters) {
		this.areaWidthMeters = areaWidthMeters;
	}

	public BigDecimal getAreaLengthMeters() {
		return this.areaLengthMeters;
	}

	public void setAreaLengthMeters(BigDecimal areaLengthMeters) {
		this.areaLengthMeters = areaLengthMeters;
	}

	public BigDecimal getAreaHectares() {
		return this.areaHectares;
	}

	public void setAreaHectares(BigDecimal areaHectares) {
		this.areaHectares = areaHectares;
	}

	public String getRegdArchaeologicalSiteInd() {
		return this.regdArchaeologicalSiteInd;
	}

	public void setRegdArchaeologicalSiteInd(String regdArchaeologicalSiteInd) {
		this.regdArchaeologicalSiteInd = regdArchaeologicalSiteInd;
	}

	public String getBordenNo() {
		return this.bordenNo;
	}

	public void setBordenNo(String bordenNo) {
		this.bordenNo = bordenNo;
	}

	public String getFnMgmtRecommendationsInd() {
		return this.fnMgmtRecommendationsInd;
	}

	public void setFnMgmtRecommendationsInd(String fnMgmtRecommendationsInd) {
		this.fnMgmtRecommendationsInd = fnMgmtRecommendationsInd;
	}

	public String getPermitIssuedInd() {
		return this.permitIssuedInd;
	}

	public void setPermitIssuedInd(String permitIssuedInd) {
		this.permitIssuedInd = permitIssuedInd;
	}

	public String getPermitNumber() {
		return this.permitNumber;
	}

	public void setPermitNumber(String permitNumber) {
		this.permitNumber = permitNumber;
	}

	public String getSitePlanStratsRecommndInd() {
		return this.sitePlanStratsRecommndInd;
	}

	public void setSitePlanStratsRecommndInd(String sitePlanStratsRecommndInd) {
		this.sitePlanStratsRecommndInd = sitePlanStratsRecommndInd;
	}

	public String getFeatureLocatedInd() {
		return this.featureLocatedInd;
	}

	public void setFeatureLocatedInd(String featureLocatedInd) {
		this.featureLocatedInd = featureLocatedInd;
	}

	public String getUniformStrategyAppliedInd() {
		return this.uniformStrategyAppliedInd;
	}

	public void setUniformStrategyAppliedInd(String uniformStrategyAppliedInd) {
		this.uniformStrategyAppliedInd = uniformStrategyAppliedInd;
	}

	public String getManagementAppliedInd() {
		return this.managementAppliedInd;
	}

	public void setManagementAppliedInd(String managementAppliedInd) {
		this.managementAppliedInd = managementAppliedInd;
	}

	public String getEvidenceOfDamageInd() {
		return this.evidenceOfDamageInd;
	}

	public void setEvidenceOfDamageInd(String evidenceOfDamageInd) {
		this.evidenceOfDamageInd = evidenceOfDamageInd;
	}

	public String getDamageDescription() {
		return this.damageDescription;
	}

	public void setDamageDescription(String damageDescription) {
		this.damageDescription = damageDescription;
	}
/*
	public String getDamageIrreversibleInd() {
		return this.damageIrreversibleInd;
	}

	public void setDamageIrreversibleInd(String damageIrreversibleInd) {
		this.damageIrreversibleInd = damageIrreversibleInd;
	}
*/
	public FrepChecklistAnswerCode getDamageIrreversibleAnswerCd() {
		return damageIrreversibleAnswerCd;
	}

	public void setDamageIrreversibleAnswerCd(FrepChecklistAnswerCode damageIrreversibleAnswerCd) {
		this.damageIrreversibleAnswerCd = damageIrreversibleAnswerCd;
	}

	public String getWindthrowMgmtApplicableInd() {
		return this.windthrowMgmtApplicableInd;
	}

	public void setWindthrowMgmtApplicableInd(String windthrowMgmtApplicableInd) {
		this.windthrowMgmtApplicableInd = windthrowMgmtApplicableInd;
	}

	public String getAreaWindfirmInd() {
		return this.areaWindfirmInd;
	}

	public void setAreaWindfirmInd(String areaWindfirmInd) {
		this.areaWindfirmInd = areaWindfirmInd;
	}

	public String getTrailFeaturesApplicableInd() {
		return this.trailFeaturesApplicableInd;
	}

	public void setTrailFeaturesApplicableInd(String trailFeaturesApplicableInd) {
		this.trailFeaturesApplicableInd = trailFeaturesApplicableInd;
	}

	public String getTrailLocatableInd() {
		return this.trailLocatableInd;
	}

	public void setTrailLocatableInd(String trailLocatableInd) {
		this.trailLocatableInd = trailLocatableInd;
	}

	public String getTrailAreaDamagedInd() {
		return this.trailAreaDamagedInd;
	}

	public void setTrailAreaDamagedInd(String trailAreaDamagedInd) {
		this.trailAreaDamagedInd = trailAreaDamagedInd;
	}

	public String getTrailLessPassableInd() {
		return this.trailLessPassableInd;
	}

	public void setTrailLessPassableInd(String trailLessPassableInd) {
		this.trailLessPassableInd = trailLessPassableInd;
	}

	public Short getEstTrailDamagePercent() {
		return this.estTrailDamagePercent;
	}

	public void setEstTrailDamagePercent(Short estTrailDamagePercent) {
		this.estTrailDamagePercent = estTrailDamagePercent;
	}

	public Short getEstWindthrowPercent() {
		return estWindthrowPercent;
	}

	public void setEstWindthrowPercent(Short estWindthrowPercent) {
		this.estWindthrowPercent = estWindthrowPercent;
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

	public Set getChrMgmtStrategyUseds() {
		return this.chrMgmtStrategyUseds;
	}

	public void setChrMgmtStrategyUseds(Set chrMgmtStrategyUseds) {
		this.chrMgmtStrategyUseds = chrMgmtStrategyUseds;
	}

	public Set getChrMgmtStrategyPlanneds() {
		return this.chrMgmtStrategyPlanneds;
	}

	public void setChrMgmtStrategyPlanneds(Set chrMgmtStrategyPlanneds) {
		this.chrMgmtStrategyPlanneds = chrMgmtStrategyPlanneds;
	}

	public Set getChrFeatureAgeXrefs() {
		return this.chrFeatureAgeXrefs;
	}

	public void setChrFeatureAgeXrefs(Set chrFeatureAgeXrefs) {
		this.chrFeatureAgeXrefs = chrFeatureAgeXrefs;
	}

	public Set getChrFeatureLocationDetails() {
		return this.chrFeatureLocationDetails;
	}

	public void setChrFeatureLocationDetails(Set chrFeatureLocationDetails) {
		this.chrFeatureLocationDetails = chrFeatureLocationDetails;
	}

	public Set getChrFeatureTypeXrefs() {
		return this.chrFeatureTypeXrefs;
	}

	public void setChrFeatureTypeXrefs(Set chrFeatureTypeXrefs) {
		this.chrFeatureTypeXrefs = chrFeatureTypeXrefs;
	}

	public Set getChrFeatWindthrTreatXrefs() {
		return this.chrFeatWindthrTreatXrefs;
	}

	public void setChrFeatWindthrTreatXrefs(Set chrFeatWindthrTreatXrefs) {
		this.chrFeatWindthrTreatXrefs = chrFeatWindthrTreatXrefs;
	}

	public Set getChrFeatureDamageAgentXrefs() {
		return chrFeatureDamageAgentXrefs;
	}

	public void setChrFeatureDamageAgentXrefs(Set chrFeatureDamageAgentXrefs) {
		this.chrFeatureDamageAgentXrefs = chrFeatureDamageAgentXrefs;
	}

}
