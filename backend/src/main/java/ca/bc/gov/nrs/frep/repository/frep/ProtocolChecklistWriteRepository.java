package ca.bc.gov.nrs.frep.repository.frep;

import ca.bc.gov.nrs.frep.dto.frep.BioCwdRow;
import ca.bc.gov.nrs.frep.dto.frep.BioPlot;
import ca.bc.gov.nrs.frep.dto.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.dto.frep.BioStandRow;
import ca.bc.gov.nrs.frep.dto.frep.BioStratum;
import ca.bc.gov.nrs.frep.dto.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.dto.frep.BioWindthrowTreatment;
import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.dto.frep.RipContinuousIndRow;
import ca.bc.gov.nrs.frep.dto.frep.RipNoAnswerRow;
import ca.bc.gov.nrs.frep.dto.frep.RipOpenSpecImpactRow;
import ca.bc.gov.nrs.frep.dto.frep.RipOtherIndRow;
import ca.bc.gov.nrs.frep.dto.frep.RipOtherSpecImpactRow;
import ca.bc.gov.nrs.frep.dto.frep.RipPointIndRow;
import ca.bc.gov.nrs.frep.dto.frep.RipQuestionRow;
import ca.bc.gov.nrs.frep.dto.frep.RipStreamEdgeRow;
import ca.bc.gov.nrs.frep.dto.frep.RiparianFieldData;
import ca.bc.gov.nrs.frep.dto.frep.RiparianFinalComments;
import ca.bc.gov.nrs.frep.dto.frep.RiparianOtherIndicators;
import ca.bc.gov.nrs.frep.dto.frep.RiparianQuestions;
import ca.bc.gov.nrs.frep.dto.frep.RiparianSpecificImpacts;
import ca.bc.gov.nrs.frep.dto.frep.RiparianStreamOpening;
import ca.bc.gov.nrs.frep.dto.frep.WaterAssessment;
import ca.bc.gov.nrs.frep.dto.frep.WaterRange;
import ca.bc.gov.nrs.frep.dto.frep.WaterSampleArea;
import ca.bc.gov.nrs.frep.dto.frep.WaterSampleSite;
import ca.bc.gov.nrs.frep.dto.frep.WtrAccessRoadRow;
import ca.bc.gov.nrs.frep.dto.frep.WtrAssessmentRow;
import ca.bc.gov.nrs.frep.dto.frep.WtrDisturbanceRow;
import java.sql.Array;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import oracle.jdbc.OracleConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Write path for the classic BIO/RIP/WTR protocol checklists, calling the legacy Oracle packages:
 * submit/unsubmit via {@code FREP_TOMBSTONE}, and the Biodiversity Opening save via
 * {@code FREP_210_BIO_OPENING.SAVE}. Mirrors legacy {@code SubmitPrintChecklistDataManager} and
 * {@code BiodiversityDataManager}.
 */
@Repository
@Profile("oracle")
public class ProtocolChecklistWriteRepository extends AbstractFrepRepository {

  private static final String TOMBSTONE = "FREP_TOMBSTONE";
  private static final String BIO_OPENING_PACKAGE = "frep_210_bio_opening";

  private static final String BIO_OPENING_SELECT =
      "SELECT frep_resource_value_id, frep_checklist_status_code, frep_wtp_override, "
          + "location_description, patch_reserves_on_block, patch_reserves_sampled, "
          + "innovtv_practice_answer_code, innovative_practices_comment, invasive_plant_answer_code, "
          + "invasive_plant_comment, frep_site_evaluation_code, evaluator_opinion_comment, "
          + "revision_count "
          + "FROM the.biodiversity_checklist WHERE biodiversity_checklist_id = ?";

  public ProtocolChecklistWriteRepository(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Server-side submit: {@code FREP_TOMBSTONE.FREP_SUBMISSION_VALIDATION} validates the whole
   * checklist and, on success, flips status to SUB. Returns the (possibly empty) error message.
   */
  public String submit(String resourceValueType, String checklistId, String userId) {
    return executeCall(
        callSql(TOMBSTONE, "FREP_SUBMISSION_VALIDATION", 4),
        cs -> {
          cs.setString(1, resourceValueType);
          cs.setString(2, checklistId);
          cs.setString(3, userId);
          cs.registerOutParameter(4, Types.VARCHAR);
        },
        cs -> cs.getString(4)
    );
  }

  /** Reverts a submitted checklist to ACT. Returns the (possibly empty) error message. */
  public String unsubmit(String resourceValueType, String checklistId, String userId) {
    return executeCall(
        callSql(TOMBSTONE, "UNSUBMIT", 4),
        cs -> {
          cs.setString(1, resourceValueType);
          cs.setString(2, checklistId);
          cs.setString(3, userId);
          cs.registerOutParameter(4, Types.VARCHAR);
        },
        cs -> cs.getString(4)
    );
  }

  /**
   * Read the editable Opening columns directly from {@code biodiversity_checklist} (including the
   * revision_count optimistic-lock token). A direct SELECT avoids depending on the GET proc's
   * out-parameter ordering. Returns null when the checklist does not exist.
   */
  public BiodiversityOpening getBiodiversityOpening(String checklistId) {
    return jdbcTemplate.query(
        BIO_OPENING_SELECT,
        rs -> {
          if (!rs.next()) {
            return null;
          }
          return new BiodiversityOpening(
              checklistId,
              rs.getString("frep_resource_value_id"),
              rs.getString("frep_checklist_status_code"),
              rs.getString("frep_wtp_override"),
              rs.getString("location_description"),
              rs.getString("patch_reserves_on_block"),
              rs.getString("patch_reserves_sampled"),
              rs.getString("innovtv_practice_answer_code"),
              rs.getString("innovative_practices_comment"),
              rs.getString("invasive_plant_answer_code"),
              rs.getString("invasive_plant_comment"),
              rs.getString("frep_site_evaluation_code"),
              rs.getString("evaluator_opinion_comment"),
              rs.getString("revision_count")
          );
        },
        checklistId
    );
  }

  /**
   * Persist the Opening via {@code FREP_210_BIO_OPENING.SAVE} (16 positional params; checklist id,
   * resource id and revision_count are IN OUT; error_message is OUT). Returns the opening with the
   * id + revision the proc echoes back. Throws {@code StoredProcedureException} on a proc error
   * (includes the optimistic-lock conflict the proc raises on a stale revision_count).
   */
  public BiodiversityOpening saveBiodiversityOpening(BiodiversityOpening o, String userId) {
    return executeCall(
        callSql(BIO_OPENING_PACKAGE, "SAVE", 16),
        cs -> {
          setInOutString(cs, 1, o.checklistId());
          setInOutString(cs, 2, o.resourceValueId());
          cs.setString(3, o.statusCode());
          cs.setString(4, o.frepWtpOverride());
          cs.setString(5, o.locationDescription());
          cs.setString(6, o.patchReservesOnBlock());
          cs.setString(7, o.patchReservesSampled());
          cs.setString(8, o.innovativePracticeInd());
          cs.setString(9, o.innovativePracticesComment());
          cs.setString(10, o.invasivePlantIndicator());
          cs.setString(11, o.invasivePlantComment());
          cs.setString(12, o.frepSiteEvaluationCode());
          cs.setString(13, o.evaluatorOpinionComment());
          setInOutString(cs, 14, o.revisionCount());
          cs.setString(15, userId);
          cs.registerOutParameter(16, Types.VARCHAR);
        },
        cs -> {
          throwIfError(BIO_OPENING_PACKAGE, "SAVE", cs.getString(16));
          return o.withIdentity(cs.getString(1), cs.getString(14));
        }
    );
  }

  // --- Biodiversity Stratum (FREP screen 211) ---

  private static final String BIO_STRATUM_PACKAGE = "FREP_211_BIOSTRATUM";
  private static final String WINDTHROW_VARRAY_TYPE = "THE.FREP_WINDTHROW_TREAT_VARRAY";
  private static final String WINDTHROW_OBJECT_TYPE = "THE.FREP_WINDTHROW_TREAT_OBJECT";

  private static final String STRATUM_LIST_SELECT =
      "SELECT stratum_id, stratum_number, biodiversity_strata_type_code, "
          + "TO_CHAR(stratum_summary_date,'YYYY-MM-DD') AS summary_date, stratum_plot_count, "
          + "stratum_size FROM the.biodiversity_stratum WHERE biodiversity_checklist_id = ? "
          + "ORDER BY stratum_number";

  // All editable + round-tripped scalar columns (UPDATE rewrites every column, so read them all).
  private static final String STRATUM_SELECT =
      "SELECT biodiversity_checklist_id, biodiversity_strata_type_code, stratum_number, "
          + "TO_CHAR(stratum_summary_date,'YYYY-MM-DD') AS summary_date, "
          + "stratum_summary_assessor_name, stratum_plot_count, stratum_size, "
          + "stratum_consistent_map_ind, stratum_estimated_size, patch_location_code, "
          + "patch_estimated_oldst_tree_age, patch_general_comment, patch_windthrow_pct, "
          + "constraint_indicator, wetland_pct, harvest_area_code, riparian_management_zone_pct, "
          + "riparian_reserve_zone_pct, rock_outcrop_pct, non_commercial_brush_pct, "
          + "non_merch_timber_pct, sensitive_soil_pct, ung_hoof_animal_wintering_pct, "
          + "wildlife_habitat_area_pct, old_growth_management_area_pct, visuals_pct, "
          + "cultural_heritage_feature_pct, recreation_feature_pct, other_constraint, "
          + "other_constraint_pct, eco_indicator, bear_den_cnt, hibernaculum_cnt, vet_tree_cnt, "
          + "mineral_lick_cnt, large_stick_nest_cnt, cavity_nest_cnt, large_hallow_tree_cnt, "
          + "large_witches_broom_cnt, karst_feature_ind, largest_tree_ind, "
          + "cwd_heavy_concentration_ind, active_wildlife_trails_ind, active_wlt_cwd_feeding_ind, "
          + "uncommon_tree_species_ind, other_eco_anchor_cnt, other_eco_anchor_desc, bgc_zone_code, "
          + "bgc_subzone_code, bgc_variant, bgc_phase, bec_site_series_cd, site_series_phase_cd, "
          + "seral, windthrow_distribution_code, other_windthrow_treatment, calc_constrained_total, "
          + "revision_count FROM the.biodiversity_stratum WHERE stratum_id = ?";

  private static final String WINDTHROW_SELECT =
      "SELECT windthrow_treatment_id, windthrow_treatment_code, check_ind "
          + "FROM the.windthrow_treatment WHERE stratum_id = ? ORDER BY windthrow_treatment_code";

  public List<BioStratumRow> listBioStrata(String checklistId) {
    return jdbcTemplate.query(
        STRATUM_LIST_SELECT,
        (rs, rowNum) -> new BioStratumRow(
            rs.getString("stratum_id"),
            rs.getString("stratum_number"),
            rs.getString("biodiversity_strata_type_code"),
            rs.getString("summary_date"),
            rs.getString("stratum_plot_count"),
            rs.getString("stratum_size")),
        checklistId
    );
  }

  public BioStratum getBioStratum(String stratumId) {
    List<BioWindthrowTreatment> treatments = jdbcTemplate.query(
        WINDTHROW_SELECT,
        (rs, rowNum) -> new BioWindthrowTreatment(
            rs.getString("windthrow_treatment_id"),
            rs.getString("windthrow_treatment_code"),
            rs.getString("check_ind")),
        stratumId
    );
    return jdbcTemplate.query(STRATUM_SELECT, rs -> {
      if (!rs.next()) {
        return null;
      }
      return new BioStratum(
          stratumId,
          rs.getString("biodiversity_checklist_id"),
          rs.getString("biodiversity_strata_type_code"),
          rs.getString("stratum_number"),
          rs.getString("summary_date"),
          rs.getString("stratum_summary_assessor_name"),
          rs.getString("stratum_plot_count"),
          rs.getString("stratum_size"),
          rs.getString("stratum_consistent_map_ind"),
          rs.getString("stratum_estimated_size"),
          rs.getString("patch_location_code"),
          rs.getString("patch_estimated_oldst_tree_age"),
          rs.getString("patch_general_comment"),
          rs.getString("patch_windthrow_pct"),
          rs.getString("constraint_indicator"),
          rs.getString("wetland_pct"),
          rs.getString("harvest_area_code"),
          rs.getString("riparian_management_zone_pct"),
          rs.getString("riparian_reserve_zone_pct"),
          rs.getString("rock_outcrop_pct"),
          rs.getString("non_commercial_brush_pct"),
          rs.getString("non_merch_timber_pct"),
          rs.getString("sensitive_soil_pct"),
          rs.getString("ung_hoof_animal_wintering_pct"),
          rs.getString("wildlife_habitat_area_pct"),
          rs.getString("old_growth_management_area_pct"),
          rs.getString("visuals_pct"),
          rs.getString("cultural_heritage_feature_pct"),
          rs.getString("recreation_feature_pct"),
          rs.getString("other_constraint"),
          rs.getString("other_constraint_pct"),
          rs.getString("eco_indicator"),
          rs.getString("bear_den_cnt"),
          rs.getString("hibernaculum_cnt"),
          rs.getString("vet_tree_cnt"),
          rs.getString("mineral_lick_cnt"),
          rs.getString("large_stick_nest_cnt"),
          rs.getString("cavity_nest_cnt"),
          rs.getString("large_hallow_tree_cnt"),
          rs.getString("large_witches_broom_cnt"),
          rs.getString("karst_feature_ind"),
          rs.getString("largest_tree_ind"),
          rs.getString("cwd_heavy_concentration_ind"),
          rs.getString("active_wildlife_trails_ind"),
          rs.getString("active_wlt_cwd_feeding_ind"),
          rs.getString("uncommon_tree_species_ind"),
          rs.getString("other_eco_anchor_cnt"),
          rs.getString("other_eco_anchor_desc"),
          rs.getString("bgc_zone_code"),
          rs.getString("bgc_subzone_code"),
          rs.getString("bgc_variant"),
          rs.getString("bgc_phase"),
          rs.getString("bec_site_series_cd"),
          rs.getString("site_series_phase_cd"),
          rs.getString("seral"),
          rs.getString("windthrow_distribution_code"),
          rs.getString("other_windthrow_treatment"),
          rs.getString("calc_constrained_total"),
          rs.getString("revision_count"),
          treatments
      );
    }, stratumId);
  }

  /** Generate the next stratum number for a new stratum (FREP_211_BIOSTRATUM.ADD_NEW). */
  public String nextStratumNumber() {
    return executeCall(
        callSql(BIO_STRATUM_PACKAGE, "ADD_NEW", 2),
        cs -> {
          cs.registerOutParameter(1, Types.VARCHAR);
          cs.registerOutParameter(2, Types.VARCHAR);
        },
        cs -> {
          throwIfError(BIO_STRATUM_PACKAGE, "ADD_NEW", cs.getString(2));
          return cs.getString(1);
        }
    );
  }

  /** Per-stratum upsert via FREP_211_BIOSTRATUM.SAVE_STRATUM (63 params incl. windthrow VARRAY). */
  public BioStratum saveBioStratum(BioStratum s, String userId) {
    return executeCall(
        callSql(BIO_STRATUM_PACKAGE, "SAVE_STRATUM", 63),
        cs -> {
          setInOutString(cs, 1, s.stratumId());
          setInOutString(cs, 2, s.checklistId());
          cs.setString(3, "SLB"); // resource_value_type
          cs.setString(4, s.strataTypeCode());
          cs.setString(5, s.stratumNumber());
          cs.setString(6, s.summaryDate());
          cs.setString(7, s.assessorName());
          cs.setString(8, s.plotCount());
          cs.setString(9, s.size());
          cs.setString(10, s.consistentMapInd());
          cs.setString(11, s.estimatedSize());
          cs.setString(12, s.patchLocationCode());
          cs.setString(13, s.patchEstimatedOldestTreeAge());
          cs.setString(14, s.patchGeneralComment());
          cs.setString(15, s.patchWindthrowPct());
          cs.setString(16, s.constraintIndicator());
          cs.setString(17, s.wetlandPct());
          cs.setString(18, s.harvestAreaCode());
          cs.setString(19, s.riparianManagementZonePct());
          cs.setString(20, s.riparianReserveZonePct());
          cs.setString(21, s.rockOutcropPct());
          cs.setString(22, s.nonCommercialBrushPct());
          cs.setString(23, s.nonMerchTimberPct());
          cs.setString(24, s.sensitiveSoilPct());
          cs.setString(25, s.ungHoofAnimalWinteringPct());
          cs.setString(26, s.wildlifeHabitatAreaPct());
          cs.setString(27, s.oldGrowthManagementAreaPct());
          cs.setString(28, s.visualsPct());
          cs.setString(29, s.culturalHeritageFeaturePct());
          cs.setString(30, s.recreationFeaturePct());
          cs.setString(31, s.otherConstraint());
          cs.setString(32, s.otherConstraintPct());
          cs.setString(33, s.ecoIndicator());
          cs.setString(34, s.bearDenCnt());
          cs.setString(35, s.hibernaculumCnt());
          cs.setString(36, s.vetTreeCnt());
          cs.setString(37, s.mineralLickCnt());
          cs.setString(38, s.largeStickNestCnt());
          cs.setString(39, s.cavityNestCnt());
          cs.setString(40, s.largeHallowTreeCnt());
          cs.setString(41, s.largeWitchesBroomCnt());
          cs.setString(42, s.karstFeatureInd());
          cs.setString(43, s.largestTreeInd());
          cs.setString(44, s.cwdHeavyConcentrationInd());
          cs.setString(45, s.activeWildlifeTrailsInd());
          cs.setString(46, s.activeWltCwdFeedingInd());
          cs.setString(47, s.uncommonTreeSpeciesInd());
          cs.setString(48, s.otherEcoAnchorCnt());
          cs.setString(49, s.otherEcoAnchorDesc());
          cs.setString(50, s.bgcZoneCode());
          cs.setString(51, s.bgcSubzoneCode());
          cs.setString(52, s.bgcVariant());
          cs.setString(53, s.bgcPhase());
          cs.setString(54, s.becSiteSeriesCd());
          cs.setString(55, s.siteSeriesPhaseCd());
          cs.setString(56, s.seral());
          cs.setString(57, s.windthrowDistributionCode());
          cs.setString(58, s.otherWindthrowTreatment());
          cs.setString(59, s.constrainedTotal());
          setInOutString(cs, 60, s.revisionCount());
          setInOutString(cs, 61, userId);
          cs.registerOutParameter(62, Types.VARCHAR);
          cs.setObject(63, buildWindthrowArray(cs, s));
          cs.registerOutParameter(63, Types.ARRAY, WINDTHROW_VARRAY_TYPE);
        },
        cs -> {
          throwIfError(BIO_STRATUM_PACKAGE, "SAVE_STRATUM", cs.getString(62));
          return s.withIdentity(cs.getString(1), cs.getString(60));
        }
    );
  }

  private Array buildWindthrowArray(java.sql.CallableStatement cs, BioStratum s) throws java.sql.SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    List<BioWindthrowTreatment> treatments =
        s.windthrowTreatments() == null ? List.of() : s.windthrowTreatments();
    List<Object> structs = new ArrayList<>(treatments.size());
    for (BioWindthrowTreatment t : treatments) {
      structs.add(connection.createStruct(WINDTHROW_OBJECT_TYPE, new Object[] {
          t.windthrowTreatmentId(), s.stratumId(), t.code(), t.checkInd()
      }));
    }
    return connection.createOracleArray(WINDTHROW_VARRAY_TYPE, structs.toArray());
  }

  public String deleteBioStratum(String stratumId, String revisionCount) {
    return executeCall(
        callSql(BIO_STRATUM_PACKAGE, "DELETE_STRATUM", 3),
        cs -> {
          setInOutString(cs, 1, stratumId);
          cs.setString(2, revisionCount);
          cs.registerOutParameter(3, Types.VARCHAR);
        },
        cs -> cs.getString(3)
    );
  }

  // --- Biodiversity Plots (FREP screen 212) ---

  private static final String BIO_PLOT_PACKAGE = "FREP_212_BIOPLOT";
  private static final String STAND_VARRAY_TYPE = "THE.FREP_STAND_TABLE_VARRAY";
  private static final String STAND_OBJECT_TYPE = "THE.FREP_STAND_TABLE_OBJECT";
  private static final String CWD_VARRAY_TYPE = "THE.FREP_CWD_TABLE_VARRAY";
  private static final String CWD_OBJECT_TYPE = "THE.FREP_CWD_TABLE_OBJECT";

  private static final String PLOT_LIST_SELECT =
      "SELECT biodiversity_plot_id, plot_number, assessor_name FROM the.biodiversity_plot "
          + "WHERE stratum_id = ? ORDER BY biodiversity_plot_id";

  private static final String PLOT_SELECT =
      "SELECT stratum_id, plot_number, assessor_name, utm_signal, utm_zone, utm_easting, "
          + "utm_northing, tree_indicator, basal_area_factor, fixed_area_radius, full_count_area, "
          + "cwd_transect_indicator, first_leg_transect, second_leg_transect, plot_comment, "
          + "revision_count FROM the.biodiversity_plot WHERE biodiversity_plot_id = ?";

  // Stand-table rows for a plot; descriptions LEFT JOINed for display (not written back on save).
  private static final String STAND_SELECT =
      "SELECT bsd.biodiversity_stand_id, bsd.biodiversity_plot_id, bsd.frep_tree_species_code, "
          + "ftsc.description AS species_desc, bsd.tree_number, bsd.dbh, bsd.height, bsd.comments, "
          + "bsd.wildlife_tree_decay_class_code, wtdcc.description AS decay_desc, "
          + "bsd.revision_count, bsd.entry_userid, bsd.update_userid "
          + "FROM the.biodiversity_stand_detail bsd "
          + "LEFT JOIN the.frep_tree_species_code ftsc "
          + "ON ftsc.frep_tree_species_code = bsd.frep_tree_species_code "
          + "LEFT JOIN the.wildlife_tree_decay_class_code wtdcc "
          + "ON wtdcc.wildlife_tree_decay_class_code = bsd.wildlife_tree_decay_class_code "
          + "WHERE bsd.biodiversity_plot_id = ? ORDER BY bsd.biodiversity_stand_id";

  private static final String CWD_SELECT =
      "SELECT cwdd.coarse_woody_debris_detail_id, cwdd.biodiversity_plot_id, "
          + "cwdd.frep_tree_species_code, stsc.description AS species_desc, cwdd.log_number, "
          + "cwdd.log_diameter, cwdd.log_length, cwdd.cwd_decay_class_code, "
          + "cdcc.description AS decay_desc, cwdd.comments, cwdd.revision_count, cwdd.entry_userid, "
          + "cwdd.update_userid FROM the.coarse_woody_debris_detail cwdd "
          + "LEFT JOIN the.frep_tree_species_code stsc "
          + "ON stsc.frep_tree_species_code = cwdd.frep_tree_species_code "
          + "LEFT JOIN the.cwd_decay_class_code cdcc "
          + "ON cdcc.cwd_decay_class_code = cwdd.cwd_decay_class_code "
          + "WHERE cwdd.biodiversity_plot_id = ? ORDER BY cwdd.coarse_woody_debris_detail_id";

  public List<BioPlotRow> listBioPlots(String stratumId) {
    return jdbcTemplate.query(
        PLOT_LIST_SELECT,
        (rs, rowNum) -> new BioPlotRow(
            rs.getString("biodiversity_plot_id"),
            rs.getString("plot_number"),
            rs.getString("assessor_name")),
        stratumId
    );
  }

  public BioPlot getBioPlot(String plotId) {
    List<BioStandRow> stand = jdbcTemplate.query(
        STAND_SELECT,
        (rs, rowNum) -> new BioStandRow(
            rs.getString("biodiversity_stand_id"),
            rs.getString("biodiversity_plot_id"),
            rs.getString("frep_tree_species_code"),
            rs.getString("species_desc"),
            rs.getString("tree_number"),
            rs.getString("dbh"),
            rs.getString("height"),
            rs.getString("comments"),
            rs.getString("wildlife_tree_decay_class_code"),
            rs.getString("decay_desc"),
            rs.getString("revision_count"),
            rs.getString("entry_userid"),
            rs.getString("update_userid")),
        plotId
    );
    List<BioCwdRow> cwd = jdbcTemplate.query(
        CWD_SELECT,
        (rs, rowNum) -> new BioCwdRow(
            rs.getString("coarse_woody_debris_detail_id"),
            rs.getString("biodiversity_plot_id"),
            rs.getString("frep_tree_species_code"),
            rs.getString("species_desc"),
            rs.getString("log_number"),
            rs.getString("log_diameter"),
            rs.getString("log_length"),
            rs.getString("cwd_decay_class_code"),
            rs.getString("decay_desc"),
            rs.getString("comments"),
            rs.getString("revision_count"),
            rs.getString("entry_userid"),
            rs.getString("update_userid")),
        plotId
    );
    return jdbcTemplate.query(PLOT_SELECT, rs -> {
      if (!rs.next()) {
        return null;
      }
      return new BioPlot(
          plotId,
          rs.getString("stratum_id"),
          rs.getString("plot_number"),
          rs.getString("assessor_name"),
          rs.getString("utm_signal"),
          rs.getString("utm_zone"),
          rs.getString("utm_easting"),
          rs.getString("utm_northing"),
          rs.getString("tree_indicator"),
          rs.getString("basal_area_factor"),
          rs.getString("fixed_area_radius"),
          rs.getString("full_count_area"),
          rs.getString("cwd_transect_indicator"),
          rs.getString("first_leg_transect"),
          rs.getString("second_leg_transect"),
          rs.getString("plot_comment"),
          rs.getString("revision_count"),
          stand,
          cwd
      );
    }, plotId);
  }

  /**
   * Upsert a plot + its stand/CWD child rows. {@code save_plot} (20 params) saves the scalar plot
   * fields and echoes back the (possibly new) plot id + revision; {@code save_bio_stand_detail} and
   * {@code save_cwd_detail} then full-replace the child collections (the procs DELETE all rows for
   * the plot and re-insert the array — so the child rows are sent with null ids to force insert,
   * matching the legacy full-replace semantics). Returns a fresh read of the saved plot.
   */
  public BioPlot saveBioPlot(BioPlot p, String userId) {
    BioPlot saved = executeCall(
        callSql(BIO_PLOT_PACKAGE, "save_plot", 20),
        cs -> {
          setInOutString(cs, 1, p.plotId());
          setInOutString(cs, 2, p.stratumId());
          cs.setString(3, "SLB"); // resource_value_type
          cs.setString(4, p.plotNumber());
          cs.setString(5, p.assessorName());
          cs.setString(6, p.utmSignal());
          cs.setString(7, p.utmZone());
          cs.setString(8, p.utmEasting());
          cs.setString(9, p.utmNorthing());
          cs.setString(10, p.treeIndicator());
          cs.setString(11, p.basalAreaFactor());
          cs.setString(12, p.fixedAreaRadius());
          cs.setString(13, p.fullCountArea());
          cs.setString(14, p.cwdTransectIndicator());
          cs.setString(15, p.firstLegTransect());
          cs.setString(16, p.secondLegTransect());
          cs.setString(17, p.plotComment());
          setInOutString(cs, 18, p.revisionCount());
          setInOutString(cs, 19, userId);
          cs.registerOutParameter(20, Types.VARCHAR);
        },
        cs -> {
          throwIfError(BIO_PLOT_PACKAGE, "save_plot", cs.getString(20));
          return p.withIdentity(cs.getString(1), cs.getString(2), cs.getString(18));
        }
    );

    String savedPlotId = saved.plotId();
    saveStandDetail(savedPlotId, p.standTable(), userId);
    saveCwdDetail(savedPlotId, p.cwdTable(), userId);
    return getBioPlot(savedPlotId);
  }

  private void saveStandDetail(String plotId, List<BioStandRow> rows, String userId) {
    String error = executeCall(
        callSql(BIO_PLOT_PACKAGE, "save_bio_stand_detail", 3),
        cs -> {
          setInOutString(cs, 1, plotId);
          cs.setObject(2, buildStructArray(cs, STAND_VARRAY_TYPE, STAND_OBJECT_TYPE, rows,
              row -> new Object[] {
                  null, plotId, row.speciesCode(), null, blankToNull(row.treeNumber()),
                  blankToNull(row.dbh()), blankToNull(row.height()), row.comments(),
                  row.decayClassCode(), null, blankToNull(row.revisionCount()), null, userId
              }));
          cs.registerOutParameter(3, Types.VARCHAR);
        },
        cs -> cs.getString(3)
    );
    throwIfError(BIO_PLOT_PACKAGE, "save_bio_stand_detail", error);
  }

  private void saveCwdDetail(String plotId, List<BioCwdRow> rows, String userId) {
    String error = executeCall(
        callSql(BIO_PLOT_PACKAGE, "save_cwd_detail", 3),
        cs -> {
          setInOutString(cs, 1, plotId);
          cs.setObject(2, buildStructArray(cs, CWD_VARRAY_TYPE, CWD_OBJECT_TYPE, rows,
              row -> new Object[] {
                  null, plotId, row.speciesCode(), null, blankToNull(row.logNumber()),
                  blankToNull(row.logDiameter()), blankToNull(row.logLength()), row.decayClassCode(),
                  null, row.comments(), blankToNull(row.revisionCount()), null, userId
              }));
          cs.registerOutParameter(3, Types.VARCHAR);
        },
        cs -> cs.getString(3)
    );
    throwIfError(BIO_PLOT_PACKAGE, "save_cwd_detail", error);
  }

  public String deleteBioPlot(String plotId, String revisionCount) {
    return executeCall(
        callSql(BIO_PLOT_PACKAGE, "delete_plot", 3),
        cs -> {
          setInOutString(cs, 1, plotId);
          cs.setString(2, revisionCount);
          cs.registerOutParameter(3, Types.VARCHAR);
        },
        cs -> cs.getString(3)
    );
  }

  // --- Riparian stream opening (FREP screen 230) ---

  private static final String RIP_STREAM_PACKAGE = "FREP_230_STRM_OPEN";
  private static final String STRM_EDGE_VARRAY_TYPE = "THE.FREP_STRM_EDGE_MEASMNT_VARRAY";
  private static final String STRM_EDGE_OBJECT_TYPE = "THE.FREP_STRM_EDGE_MEASMNT_OBJECT";

  // Column names taken from FREP_RIPARIAN_CHECKLIST entity package (param names differ from columns).
  private static final String RIP_STREAM_SELECT =
      "SELECT sample_number, range_use_plan, pasture_id, stream_name, stream_location_ind, "
          + "pln_riparian_strm_rma_cls_code, act_riparian_strm_rma_cls_code, channel_width, "
          + "channel_gradient_pct, channel_depth, reach_location_to, reach_location_from, "
          + "reach_location_ups_ds_ind, reach_location_from_desc, utm_signal, utm_at_reference, "
          + "utm_zone, utm_easting, utm_northing, riparian_chan_morphology_code, "
          + "rttn_rma_doms_on_plans_pct, rttn_rma_doms_on_plans_ind, rttn_rma_doms_in_field_pct, "
          + "rttn_rma_undrstry_on_plans_pct, rttn_rma_undrstry_on_plans_ind, "
          + "rttn_rma_undrstry_in_field_pct, rttn_rrz_doms_on_plans_pct, rttn_rrz_doms_on_plans_ind, "
          + "rttn_rrz_doms_in_field_pct, rttn_rrz_doms_in_field, rttn_rrz_undrstry_on_plans_pct, "
          + "rttn_rrz_undrstry_on_plans_ind, rttn_rrz_undrstry_in_field_pct, "
          + "rttn_rrz_undrstry_in_field, rttn_rmz_doms_on_plans_pct, rttn_rmz_doms_on_plans_ind, "
          + "rttn_rmz_doms_in_field_pct, rttn_rmz_undrstry_on_plans_pct, "
          + "rttn_rmz_undrstry_on_plans_ind, rttn_rmz_undrstry_in_field_pct, pln_riparian_str_na_ind, "
          + "invasive_plant_answer_code, invasive_plant_comment, revision_count "
          + "FROM the.riparian_checklist WHERE riparian_checklist_id = ?";

  private static final String RIP_STREAM_EDGE_SELECT =
      "SELECT rip_stream_edge_measure_type, stream_edge_measurement, revision_count "
          + "FROM the.rip_stream_edge_measure_xref WHERE riparian_checklist_id = ? "
          + "ORDER BY rip_stream_edge_measure_type";

  public RiparianStreamOpening getRipStreamOpening(String checklistId) {
    List<RipStreamEdgeRow> edges = jdbcTemplate.query(
        RIP_STREAM_EDGE_SELECT,
        (rs, rowNum) -> new RipStreamEdgeRow(
            rs.getString("rip_stream_edge_measure_type"),
            rs.getString("stream_edge_measurement"),
            null,
            rs.getString("revision_count")),
        checklistId
    );
    return jdbcTemplate.query(RIP_STREAM_SELECT, rs -> {
      if (!rs.next()) {
        return null;
      }
      return new RiparianStreamOpening(
          checklistId,
          rs.getString("sample_number"),
          rs.getString("range_use_plan"),
          rs.getString("pasture_id"),
          rs.getString("stream_name"),
          rs.getString("stream_location_ind"),
          rs.getString("pln_riparian_strm_rma_cls_code"),
          rs.getString("act_riparian_strm_rma_cls_code"),
          rs.getString("channel_width"),
          rs.getString("channel_gradient_pct"),
          rs.getString("channel_depth"),
          rs.getString("reach_location_to"),
          rs.getString("reach_location_from"),
          rs.getString("reach_location_ups_ds_ind"),
          rs.getString("reach_location_from_desc"),
          rs.getString("utm_signal"),
          rs.getString("utm_at_reference"),
          rs.getString("utm_zone"),
          rs.getString("utm_easting"),
          rs.getString("utm_northing"),
          rs.getString("riparian_chan_morphology_code"),
          rs.getString("rttn_rma_doms_on_plans_pct"),
          rs.getString("rttn_rma_doms_on_plans_ind"),
          rs.getString("rttn_rma_doms_in_field_pct"),
          rs.getString("rttn_rma_undrstry_on_plans_pct"),
          rs.getString("rttn_rma_undrstry_on_plans_ind"),
          rs.getString("rttn_rma_undrstry_in_field_pct"),
          rs.getString("rttn_rrz_doms_on_plans_pct"),
          rs.getString("rttn_rrz_doms_on_plans_ind"),
          rs.getString("rttn_rrz_doms_in_field_pct"),
          rs.getString("rttn_rrz_doms_in_field"),
          rs.getString("rttn_rrz_undrstry_on_plans_pct"),
          rs.getString("rttn_rrz_undrstry_on_plans_ind"),
          rs.getString("rttn_rrz_undrstry_in_field_pct"),
          rs.getString("rttn_rrz_undrstry_in_field"),
          rs.getString("rttn_rmz_doms_on_plans_pct"),
          rs.getString("rttn_rmz_doms_on_plans_ind"),
          rs.getString("rttn_rmz_doms_in_field_pct"),
          rs.getString("rttn_rmz_undrstry_on_plans_pct"),
          rs.getString("rttn_rmz_undrstry_on_plans_ind"),
          rs.getString("rttn_rmz_undrstry_in_field_pct"),
          rs.getString("pln_riparian_str_na_ind"),
          rs.getString("invasive_plant_answer_code"),
          rs.getString("invasive_plant_comment"),
          rs.getString("revision_count"),
          edges
      );
    }, checklistId);
  }

  /** Save the stream opening via FREP_230_STRM_OPEN.SAVE (48 params; stream-edge VARRAY at 9). */
  public RiparianStreamOpening saveRipStreamOpening(RiparianStreamOpening o, String userId) {
    return executeCall(
        callSql(RIP_STREAM_PACKAGE, "SAVE", 48),
        cs -> {
          setInOutString(cs, 1, o.checklistId());
          cs.setString(2, o.sampleNumber());
          cs.setString(3, o.rangeUsePlan());
          cs.setString(4, o.pastureId());
          cs.setString(5, o.streamName());
          cs.setString(6, o.streamLocationInd());
          cs.setString(7, o.plnRiparianStrmRmaCls());
          cs.setString(8, o.actRiparianStrmRmaCls());
          cs.setObject(9, buildStructArray(cs, STRM_EDGE_VARRAY_TYPE, STRM_EDGE_OBJECT_TYPE,
              o.streamEdge(), row -> new Object[] {
                  o.checklistId(), row.measureType(), blankToNull(row.measurement()), null,
                  blankToNull(row.revisionCount()), null, userId
              }));
          cs.registerOutParameter(9, Types.ARRAY, STRM_EDGE_VARRAY_TYPE);
          cs.setString(10, o.channelWidth());
          cs.setString(11, o.channelGradientPct());
          cs.setString(12, o.channelDepth());
          cs.setString(13, o.reachLocationTo());
          cs.setString(14, o.reachLocationFrom());
          cs.setString(15, o.reachLocationUpsDsInd());
          cs.setString(16, o.reachLocationFromDesc());
          cs.setString(17, o.utmSignal());
          cs.setString(18, o.utmAtReference());
          cs.setString(19, o.utmZone());
          cs.setString(20, o.utmEasting());
          cs.setString(21, o.utmNorthing());
          cs.setString(22, o.riparianChanMorphology());
          cs.setString(23, o.rttnRmaDomsOnPlans());
          cs.setString(24, o.rttnRmaDomsOnPlansInd());
          cs.setString(25, o.rttnRmaDomsInField());
          cs.setString(26, o.rttnRmaUndrstryOnPlans());
          cs.setString(27, o.rttnRmaUndrstryOnPlnI());
          cs.setString(28, o.rttnRmaUndrstryInField());
          cs.setString(29, o.rttnRrzDomsOnPlans());
          cs.setString(30, o.rttnRrzDomsOnPlansInd());
          cs.setString(31, o.rttnRrzDomsInFieldPct());
          cs.setString(32, o.rttnRrzDomsInField());
          cs.setString(33, o.rttnRrzUndrstryOnPlans());
          cs.setString(34, o.rttnRrzUndrstryOnPlnI());
          cs.setString(35, o.rttnRrzUndrstryFldPct());
          cs.setString(36, o.rttnRrzUndrstryInField());
          cs.setString(37, o.rttnRmzDomsOnPlans());
          cs.setString(38, o.rttnRmzDomsOnPlansInd());
          cs.setString(39, o.rttnRmzDomsInField());
          cs.setString(40, o.rttnRmzUndrstryOnPlans());
          cs.setString(41, o.rttnRmzUndrstryOnPlnI());
          cs.setString(42, o.rttnRmzUndrstryInField());
          cs.setString(43, o.plnRiparianStrNaInd());
          cs.setString(44, o.invasivePlantIndicator());
          cs.setString(45, o.invasivePlantComment());
          setInOutString(cs, 46, o.revisionCount());
          cs.setString(47, userId);
          cs.registerOutParameter(48, Types.VARCHAR);
        },
        cs -> {
          throwIfError(RIP_STREAM_PACKAGE, "SAVE", cs.getString(48));
          return o.withIdentity(cs.getString(1), cs.getString(46));
        }
    );
  }

  // --- Riparian final comments (FREP screen 235) ---

  private static final String RIP_FINAL_CMTS_PACKAGE = "FREP_235_FINAL_CMTS";

  private static final String RIP_FINAL_CMTS_SELECT =
      "SELECT conclusion_comment, specific_impact_comment, assessment_problems_comment, "
          + "map_legibility_comment, leave_strip_assessment_comment, checklist_recomm_comment, "
          + "revision_count FROM the.riparian_checklist WHERE riparian_checklist_id = ?";

  public RiparianFinalComments getRipFinalComments(String checklistId) {
    return jdbcTemplate.query(RIP_FINAL_CMTS_SELECT, rs -> {
      if (!rs.next()) {
        return null;
      }
      return new RiparianFinalComments(
          checklistId,
          rs.getString("conclusion_comment"),
          rs.getString("specific_impact_comment"),
          rs.getString("assessment_problems_comment"),
          rs.getString("map_legibility_comment"),
          rs.getString("leave_strip_assessment_comment"),
          rs.getString("checklist_recomm_comment"),
          rs.getString("revision_count")
      );
    }, checklistId);
  }

  public RiparianFinalComments saveRipFinalComments(RiparianFinalComments o, String userId) {
    return executeCall(
        callSql(RIP_FINAL_CMTS_PACKAGE, "save", 10),
        cs -> {
          setInOutString(cs, 1, o.checklistId());
          cs.setString(2, o.conclusionComment());
          cs.setString(3, o.specificImpactComment());
          cs.setString(4, o.assessmentProblemsComment());
          cs.setString(5, o.mapLegibilityComment());
          cs.setString(6, o.leaveStripAssessmentComment());
          cs.setString(7, o.checklistRecommComment());
          setInOutString(cs, 8, o.revisionCount());
          cs.setString(9, userId);
          cs.registerOutParameter(10, Types.VARCHAR);
        },
        cs -> {
          throwIfError(RIP_FINAL_CMTS_PACKAGE, "save", cs.getString(10));
          return o.withIdentity(cs.getString(1), cs.getString(8));
        }
    );
  }

  // --- Riparian indicator/question/impact grids (FREP screens 231-234) ---
  //
  // Reads use a direct SELECT of the xref tables (authoritative columns; display-only labels that
  // the legacy GET derives from type-table joins — question_no, transect_no, threshold, question
  // text, descriptions — are left null and reconciled by the SAVE procs by type/id). Saves use the
  // exact .pks SAVE signatures with the VARRAY element order from the .tps object types.

  private static final String RIP_FIELD_PACKAGE = "FREP_231_FIELD_DATA";
  private static final String RIP_OTHER_PACKAGE = "FREP_232_OTHER_INDS";
  private static final String RIP_QUESTIONS_PACKAGE = "FREP_233_QUESTIONS";
  private static final String RIP_IMPACTS_PACKAGE = "FREP_234_SPECIFIC_IMPACTS";

  private static final String POINT_IND_VARRAY = "THE.FREP_POINT_INDICATOR_VARRAY";
  private static final String POINT_IND_OBJECT = "THE.FREP_POINT_INDICATOR_OBJECT";
  private static final String CONTINUOUS_IND_VARRAY = "THE.FREP_CONTINUOUS_IND_VARRAY";
  private static final String CONTINUOUS_IND_OBJECT = "THE.FREP_CONTINUOUS_IND_OBJECT";
  private static final String OTHER_IND_VARRAY = "THE.FREP_OTHER_INDICATOR_VARRAY";
  private static final String OTHER_IND_OBJECT = "THE.FREP_OTHER_INDICATOR_OBJECT";
  private static final String QUESTIONS_VARRAY = "THE.FREP_QUESTIONS_VARRAY";
  private static final String QUESTIONS_OBJECT = "THE.FREP_QUESTIONS_OBJECT";
  private static final String NO_ANSWERS_VARRAY = "THE.FREP_NO_ANSWERS_VARRAY";
  private static final String NO_ANSWERS_OBJECT = "THE.FREP_NO_ANSWERS_OBJECT";
  private static final String OPEN_SPEC_VARRAY = "THE.FREP_OPEN_SPEC_IMPACT_VARRAY";
  private static final String OPEN_SPEC_OBJECT = "THE.FREP_OPEN_SPEC_IMPACT_OBJECT";
  private static final String OTHER_SPEC_VARRAY = "THE.FREP_OTHER_SPEC_IMPACT_VARRAY";
  private static final String OTHER_SPEC_OBJECT = "THE.FREP_OTHER_SPEC_IMPACT_OBJECT";

  // 231 Field data
  public RiparianFieldData getRipFieldData(String checklistId) {
    List<RipPointIndRow> points = jdbcTemplate.query(
        "SELECT riparian_point_indicator_id, riparian_point_ind_type, measure_1, measure_2, "
            + "measure_3, measure_4, measure_5, measure_6, mean, revision_count "
            + "FROM the.riparian_point_indicator WHERE riparian_checklist_id = ? "
            + "ORDER BY riparian_point_ind_type",
        (rs, n) -> new RipPointIndRow(
            rs.getString("riparian_point_indicator_id"), null,
            rs.getString("riparian_point_ind_type"), null,
            rs.getString("measure_1"), rs.getString("measure_2"), rs.getString("measure_3"),
            rs.getString("measure_4"), rs.getString("measure_5"), rs.getString("measure_6"),
            null, rs.getString("mean"), rs.getString("revision_count")),
        checklistId);
    List<RipContinuousIndRow> continuous = jdbcTemplate.query(
        "SELECT riparian_continuous_ind_id, riparian_continuous_ind_type, total, comments, "
            + "revision_count FROM the.riparian_continuous_ind WHERE riparian_checklist_id = ? "
            + "ORDER BY riparian_continuous_ind_type",
        (rs, n) -> new RipContinuousIndRow(
            rs.getString("riparian_continuous_ind_id"), null,
            rs.getString("riparian_continuous_ind_type"), null,
            rs.getString("total"), rs.getString("comments"), null, rs.getString("revision_count")),
        checklistId);
    return jdbcTemplate.query(
        "SELECT field_data_stream_reach_dry FROM the.riparian_checklist WHERE riparian_checklist_id = ?",
        rs -> {
          if (!rs.next()) {
            return null;
          }
          return new RiparianFieldData(
              checklistId, rs.getString("field_data_stream_reach_dry"), points, continuous);
        }, checklistId);
  }

  public RiparianFieldData saveRipFieldData(RiparianFieldData o, String userId) {
    return executeCall(
        callSql(RIP_FIELD_PACKAGE, "SAVE", 6),
        cs -> {
          cs.setString(1, o.checklistId());
          cs.setString(2, o.fieldDataStreamReachDry());
          // measure_1..6, mean, ids and revision_count are NUMBER attrs — blank "" raises ORA-17059,
          // so empty values must be sent as null.
          cs.setObject(3, buildStructArray(cs, POINT_IND_VARRAY, POINT_IND_OBJECT, o.points(),
              r -> new Object[] {
                  blankToNull(r.pointIndicatorId()), r.questionNo(), r.pointIndType(), r.transectNo(),
                  blankToNull(r.measure1()), blankToNull(r.measure2()), blankToNull(r.measure3()),
                  blankToNull(r.measure4()), blankToNull(r.measure5()), blankToNull(r.measure6()),
                  r.threshold(), blankToNull(r.mean()), blankToNull(r.revisionCount())
              }));
          cs.registerOutParameter(3, Types.ARRAY, POINT_IND_VARRAY);
          cs.setObject(4, buildStructArray(cs, CONTINUOUS_IND_VARRAY, CONTINUOUS_IND_OBJECT,
              o.continuous(), r -> new Object[] {
                  blankToNull(r.continuousIndId()), r.questionNo(), r.continuousIndType(), r.question(),
                  blankToNull(r.total()), r.comments(), r.threshold(), blankToNull(r.revisionCount())
              }));
          cs.registerOutParameter(4, Types.ARRAY, CONTINUOUS_IND_VARRAY);
          cs.setString(5, userId);
          setInOutString(cs, 6, null);
        },
        cs -> {
          throwIfError(RIP_FIELD_PACKAGE, "SAVE", cs.getString(6));
          return getRipFieldData(o.checklistId());
        }
    );
  }

  // 232 Other indicators
  public RiparianOtherIndicators getRipOtherIndicators(String checklistId) {
    List<RipOtherIndRow> indicators = jdbcTemplate.query(
        "SELECT riparian_other_indicator_id, riparian_other_ind_type_id, riparian_other_answer_ind, "
            + "revision_count FROM the.riparian_other_indicator WHERE riparian_checklist_id = ? "
            + "ORDER BY riparian_other_ind_type_id",
        (rs, n) -> new RipOtherIndRow(
            rs.getString("riparian_other_ind_type_id"), null, null, null,
            rs.getString("riparian_other_indicator_id"), rs.getString("riparian_other_answer_ind"),
            rs.getString("revision_count"), null, null),
        checklistId);
    return new RiparianOtherIndicators(checklistId, indicators);
  }

  public RiparianOtherIndicators saveRipOtherIndicators(RiparianOtherIndicators o, String userId) {
    return executeCall(
        callSql(RIP_OTHER_PACKAGE, "save", 4),
        cs -> {
          cs.setString(1, o.checklistId());
          cs.setObject(2, buildStructArray(cs, OTHER_IND_VARRAY, OTHER_IND_OBJECT, o.indicators(),
              r -> new Object[] {
                  r.otherIndTypeId(), r.quesSectCode(), r.headerQuestionInd(), r.question(),
                  blankToNull(r.otherIndicatorId()), r.otherAnswerInd(),
                  blankToNull(r.revisionCount()), null, userId
              }));
          cs.setString(3, userId);
          setInOutString(cs, 4, null);
        },
        cs -> {
          throwIfError(RIP_OTHER_PACKAGE, "save", cs.getString(4));
          return getRipOtherIndicators(o.checklistId());
        }
    );
  }

  // 233 Questions + no-answers
  public RiparianQuestions getRipQuestions(String checklistId) {
    List<RipQuestionRow> questions = jdbcTemplate.query(
        "SELECT riparian_checklist_question_id, frep_checklist_answer_code, revision_count "
            + "FROM the.riparian_checklist_answer WHERE riparian_checklist_id = ? "
            + "ORDER BY riparian_checklist_question_id",
        (rs, n) -> new RipQuestionRow(
            checklistId, rs.getString("riparian_checklist_question_id"), null, null, null, null,
            null, null, null, null, rs.getString("frep_checklist_answer_code"),
            rs.getString("revision_count"), null, null),
        checklistId);
    List<RipNoAnswerRow> noAnswers = jdbcTemplate.query(
        "SELECT riparian_answer_impact_id, riparian_checklist_question_id, "
            + "riparian_answer_impact_type, answer_ind, revision_count "
            + "FROM the.riparian_answer_impact WHERE riparian_checklist_id = ? "
            + "ORDER BY riparian_answer_impact_id",
        (rs, n) -> new RipNoAnswerRow(
            rs.getString("riparian_answer_impact_id"), checklistId,
            rs.getString("riparian_checklist_question_id"), null,
            rs.getString("riparian_answer_impact_type"), null, null, rs.getString("answer_ind"),
            rs.getString("revision_count"), null, null),
        checklistId);
    return new RiparianQuestions(checklistId, questions, noAnswers);
  }

  public RiparianQuestions saveRipQuestions(RiparianQuestions o, String userId) {
    String checklistId = o.checklistId();
    executeCall(
        callSql(RIP_QUESTIONS_PACKAGE, "save_responses", 4),
        cs -> {
          cs.setString(1, checklistId);
          cs.setObject(2, buildStructArray(cs, QUESTIONS_VARRAY, QUESTIONS_OBJECT, o.questions(),
              q -> new Object[] {
                  checklistId, blankToNull(q.checklistQuestionId()), q.questionNo(), q.question(),
                  q.chanMorphologyCode(), q.applicableInd(), q.morphologyDesc(), q.questionType(),
                  q.questionDesc(), q.subQuestion(), q.answerCode(), blankToNull(q.revisionCount()),
                  null, userId
              }));
          cs.setString(3, userId);
          setInOutString(cs, 4, null);
        },
        cs -> {
          throwIfError(RIP_QUESTIONS_PACKAGE, "save_responses", cs.getString(4));
          return null;
        }
    );
    executeCall(
        callSql(RIP_QUESTIONS_PACKAGE, "save_no_answers", 4),
        cs -> {
          cs.setString(1, checklistId);
          cs.setObject(2, buildStructArray(cs, NO_ANSWERS_VARRAY, NO_ANSWERS_OBJECT, o.noAnswers(),
              r -> new Object[] {
                  blankToNull(r.answerImpactId()), checklistId, blankToNull(r.checklistQuestionId()),
                  r.questionNo(), r.answerImpactType(), r.answerImpactDesc(),
                  blankToNull(r.sortOrder()), r.answerInd(), blankToNull(r.revisionCount()), null,
                  userId
              }));
          cs.registerOutParameter(2, Types.ARRAY, NO_ANSWERS_VARRAY);
          cs.setString(3, userId);
          setInOutString(cs, 4, null);
        },
        cs -> {
          throwIfError(RIP_QUESTIONS_PACKAGE, "save_no_answers", cs.getString(4));
          return null;
        }
    );
    return getRipQuestions(checklistId);
  }

  // 234 Specific impacts
  public RiparianSpecificImpacts getRipSpecificImpacts(String checklistId) {
    List<RipOpenSpecImpactRow> openImpacts = jdbcTemplate.query(
        "SELECT opening_specific_impact_id, opening_specific_impact_type, spec_impact_ind, "
            + "revision_count FROM the.opening_specific_impact WHERE riparian_checklist_id = ? "
            + "ORDER BY opening_specific_impact_id",
        (rs, n) -> new RipOpenSpecImpactRow(
            rs.getString("opening_specific_impact_id"), rs.getString("opening_specific_impact_type"),
            rs.getString("spec_impact_ind"), rs.getString("revision_count")),
        checklistId);
    List<RipOtherSpecImpactRow> otherImpacts = jdbcTemplate.query(
        "SELECT other_opening_spec_impact_id, description, spec_impact_ind, revision_count "
            + "FROM the.other_opening_spec_impact WHERE riparian_checklist_id = ? "
            + "ORDER BY other_opening_spec_impact_id",
        (rs, n) -> new RipOtherSpecImpactRow(
            rs.getString("other_opening_spec_impact_id"), rs.getString("description"),
            rs.getString("spec_impact_ind"), rs.getString("revision_count")),
        checklistId);
    return new RiparianSpecificImpacts(checklistId, openImpacts, otherImpacts);
  }

  public RiparianSpecificImpacts saveRipSpecificImpacts(RiparianSpecificImpacts o, String userId) {
    return executeCall(
        callSql(RIP_IMPACTS_PACKAGE, "SAVE", 5),
        cs -> {
          setInOutString(cs, 1, o.checklistId());
          setInOutString(cs, 2, userId);
          cs.registerOutParameter(3, Types.VARCHAR);
          cs.setObject(4, buildStructArray(cs, OPEN_SPEC_VARRAY, OPEN_SPEC_OBJECT, o.openImpacts(),
              r -> new Object[] {
                  blankToNull(r.openingSpecificImpactId()),
                  blankToNull(r.openingSpecificImpactType()), r.specImpactInd(),
                  blankToNull(r.revisionCount())
              }));
          cs.registerOutParameter(4, Types.ARRAY, OPEN_SPEC_VARRAY);
          cs.setObject(5, buildStructArray(cs, OTHER_SPEC_VARRAY, OTHER_SPEC_OBJECT, o.otherImpacts(),
              r -> new Object[] {
                  blankToNull(r.otherRiparianSpecImpactId()), r.description(), r.specImpactInd(),
                  blankToNull(r.revisionCount())
              }));
          cs.registerOutParameter(5, Types.ARRAY, OTHER_SPEC_VARRAY);
        },
        cs -> {
          throwIfError(RIP_IMPACTS_PACKAGE, "SAVE", cs.getString(3));
          return getRipSpecificImpacts(o.checklistId());
        }
    );
  }

  // --- Water (FREP screens 250-253) ---
  //
  // Reads use the struct-based GET procs (the OBJECT/VARRAY attribute order is the authoritative
  // .tps order). Saves marshal the full OBJECT back (round-tripping every attribute) plus the child
  // VARRAYs. The save procs carry revision_count inside the OBJECT and raise on a stale revision;
  // that surfaces as a DataAccessException (refined to a 409 in dev).

  private static final String WTR_CHECKLIST_GET = "FREP_250_WATER_CHKLST_GET";
  private static final String WTR_CHECKLIST_SAVE = "FREP_250_WATER_CHKLST_SAVE";
  private static final String WTR_SAMPLE_SITE_GET = "FREP_251_SAMPLE_SITE_GET";
  private static final String WTR_SAMPLE_SITE_SAVE = "FREP_251_SAMPLE_SITE_SAVE";
  private static final String WTR_ASSESSMENT_GET = "FREP_252_ASSESSMENT_GET";
  private static final String WTR_ASSESSMENT_PKG = "FREP_WATER_ASSESSMENT";
  private static final String WTR_RANGE_GET = "FREP_253_RANGE_GET";
  private static final String WTR_RANGE_PKG = "FREP_WATER_RANGE";

  private static final String WTR_CHECKLIST_TYPE = "THE.FREP_WTR_CHKLST_OBJECT";
  private static final String WTR_SAMPLE_SITE_TYPE = "THE.FREP_WTR_SAMPLE_SITE_OBJECT";
  private static final String WTR_DISTURBANCE_VARRAY = "THE.FREP_WTR_DISTURBANCE_VARRAY";
  private static final String WTR_DISTURBANCE_OBJECT = "THE.FREP_WTR_DISTURBANCE_OBJECT";
  private static final String WTR_ACCESS_ROAD_VARRAY = "THE.FREP_WTR_ACCESS_ROAD_VARRAY";
  private static final String WTR_ACCESS_ROAD_OBJECT = "THE.FREP_WTR_ACCESS_ROAD_OBJECT";
  private static final String WTR_ASSESSMENT_VARRAY = "THE.FREP_WTR_ASSESSMENT_VW_VARRAY";
  private static final String WTR_ASSESSMENT_OBJECT = "THE.FREP_WTR_ASSESSMENT_VW_OBJECT";

  // 250 Sample area (water checklist OBJECT + disturbance/access-road child VARRAYs)
  public WaterSampleArea getWaterSampleArea(String checklistId) {
    return executeCall(
        "{call " + WTR_CHECKLIST_GET + "(?,?,?)}",
        cs -> {
          cs.setObject(1, newStruct(cs, WTR_CHECKLIST_TYPE, 39, 0, checklistId));
          cs.registerOutParameter(1, Types.STRUCT, WTR_CHECKLIST_TYPE);
          cs.registerOutParameter(2, Types.ARRAY, WTR_DISTURBANCE_VARRAY);
          cs.registerOutParameter(3, Types.ARRAY, WTR_ACCESS_ROAD_VARRAY);
        },
        cs -> {
          Struct s = (Struct) cs.getObject(1);
          if (s == null) {
            return null;
          }
          Object[] a = s.getAttributes();
          List<WtrDisturbanceRow> dist = readStructList(cs.getArray(2), attrs -> new WtrDisturbanceRow(
              attrString(attrs, 0), attrString(attrs, 1), attrString(attrs, 2), attrString(attrs, 3),
              attrString(attrs, 4), attrString(attrs, 5), attrString(attrs, 6), attrString(attrs, 7)));
          List<WtrAccessRoadRow> roads = readStructList(cs.getArray(3), attrs -> new WtrAccessRoadRow(
              attrString(attrs, 0), attrString(attrs, 1), attrString(attrs, 2), attrString(attrs, 3),
              attrString(attrs, 4), attrString(attrs, 5), attrString(attrs, 6), attrString(attrs, 7),
              attrString(attrs, 8), attrString(attrs, 9)));
          return new WaterSampleArea(
              attrString(a, 0), attrString(a, 1), attrString(a, 2), attrString(a, 3),
              attrString(a, 4), attrString(a, 5), attrString(a, 6), attrString(a, 7),
              attrString(a, 8), attrString(a, 9), attrString(a, 10), attrString(a, 11),
              attrString(a, 12), attrString(a, 13), attrString(a, 14), attrString(a, 15),
              attrString(a, 16), attrString(a, 17), attrString(a, 18), attrString(a, 19),
              attrString(a, 20), attrString(a, 21), attrString(a, 22), attrString(a, 23),
              attrString(a, 24), attrString(a, 25), attrString(a, 26), attrString(a, 27),
              attrString(a, 28), attrString(a, 29), attrString(a, 30), attrString(a, 31),
              attrString(a, 32), attrString(a, 33), attrString(a, 34), attrString(a, 35),
              attrString(a, 36), attrString(a, 37), dist, roads);
        }
    );
  }

  public WaterSampleArea saveWaterSampleArea(WaterSampleArea o, String userId) {
    return executeCall(
        "{call " + WTR_CHECKLIST_SAVE + "(?,?,?)}",
        cs -> {
          cs.setObject(1, buildWaterChecklistStruct(cs, o, userId));
          cs.registerOutParameter(1, Types.STRUCT, WTR_CHECKLIST_TYPE);
          cs.setObject(2, buildStructArray(cs, WTR_ACCESS_ROAD_VARRAY, WTR_ACCESS_ROAD_OBJECT,
              o.accessRoads(), r -> new Object[] {
                  blankToNull(r.accessRoadId()), o.waterChecklistId(), r.accessRoadType(),
                  r.accessRoadDesc(), r.accessRoadStatusCode(),
                  blankToNull(r.approximateRoadLength()), blankToNull(r.approximateRoadAge()),
                  blankToNull(r.revisionCount()), r.entryUserid(), userId
              }));
          cs.registerOutParameter(2, Types.ARRAY, WTR_ACCESS_ROAD_VARRAY);
          cs.setObject(3, buildStructArray(cs, WTR_DISTURBANCE_VARRAY, WTR_DISTURBANCE_OBJECT,
              o.disturbances(), r -> new Object[] {
                  blankToNull(r.disturbanceId()), o.waterChecklistId(), r.disturbanceCode(),
                  r.disturbanceAgeCode(), blankToNull(r.disturbanceNumber()),
                  blankToNull(r.revisionCount()), r.entryUserid(), userId
              }));
          cs.registerOutParameter(3, Types.ARRAY, WTR_DISTURBANCE_VARRAY);
        },
        cs -> {
          Struct s = (Struct) cs.getObject(1);
          String revision = s == null ? o.revisionCount() : attrString(s.getAttributes(), 35);
          return o.withIdentity(o.waterChecklistId(), revision);
        }
    );
  }

  private Struct buildWaterChecklistStruct(java.sql.CallableStatement cs, WaterSampleArea o, String userId)
      throws java.sql.SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    Object[] a = new Object[38];
    a[0] = o.waterChecklistId();
    a[1] = o.frepResourceValueId();
    a[2] = o.statusCode();
    a[3] = o.siteAccessCode();
    a[4] = o.mainAccessRoadNumber();
    a[5] = o.mainWatershedDescription();
    a[6] = o.drinkingWaterAnswerCode();
    a[7] = o.waterIntakeComment();
    a[8] = o.intakeToCutblockDistance();
    a[9] = o.waterIntakeConnectivityCode();
    a[10] = o.intakeToCutblockComment();
    a[11] = o.specResourceAnswerCode();
    a[12] = o.specialResourceValueComment();
    a[13] = o.reportedDisturbanceInd();
    a[14] = o.fertilizerUseOnRoadInd();
    a[15] = o.fertilizerUseWithinBlckInd();
    a[16] = o.sensitiveSoilAnswerCode();
    a[17] = o.herbicideUseOnRoadInd();
    a[18] = o.herbicideUseWithinBlockInd();
    a[19] = o.pesticideUseOnRoadInd();
    a[20] = o.pesticideUseWithinBlockInd();
    a[21] = o.streamCrossingsInd();
    a[22] = o.roadsParallelToStreamInd();
    a[23] = o.unstableSlopesInd();
    a[24] = o.sensitiveSoilsInd();
    a[25] = o.adjacentHarvestingInd();
    a[26] = o.livestockConcernsInd();
    a[27] = o.otherActivityInd();
    a[28] = o.otherActivityDescription();
    a[29] = o.noteDescription();
    a[30] = o.blockAccessTime();
    a[31] = o.hoursOnBlock();
    a[32] = o.peopleOnBlock();
    a[33] = o.invasivePlantAnswerCode();
    a[34] = o.invasivePlantComment();
    a[35] = o.revisionCount();
    a[36] = o.entryUserid();
    a[37] = userId;
    return connection.createStruct(WTR_CHECKLIST_TYPE, a);
  }

  // 251 Sample site (single OBJECT)
  public WaterSampleSite getWaterSampleSite(String checklistId) {
    return executeCall(
        "{call " + WTR_SAMPLE_SITE_GET + "(?)}",
        cs -> {
          cs.setObject(1, newStruct(cs, WTR_SAMPLE_SITE_TYPE, 28, 1, checklistId));
          cs.registerOutParameter(1, Types.STRUCT, WTR_SAMPLE_SITE_TYPE);
        },
        cs -> {
          Struct s = (Struct) cs.getObject(1);
          if (s == null) {
            return null;
          }
          Object[] a = s.getAttributes();
          return new WaterSampleSite(
              attrString(a, 0), attrString(a, 1), attrString(a, 2), attrString(a, 3),
              attrString(a, 4), attrString(a, 5), attrString(a, 6), attrString(a, 7),
              attrString(a, 8), attrString(a, 9), attrString(a, 10), attrString(a, 11),
              attrString(a, 12), attrString(a, 13), attrString(a, 14), attrString(a, 15),
              attrString(a, 16), attrString(a, 17), attrString(a, 18), attrString(a, 19),
              attrString(a, 20), attrString(a, 21), attrString(a, 22), attrString(a, 23),
              attrString(a, 24), attrString(a, 25), attrString(a, 26), attrString(a, 27));
        }
    );
  }

  public WaterSampleSite saveWaterSampleSite(WaterSampleSite o, String userId) {
    return executeCall(
        "{call " + WTR_SAMPLE_SITE_SAVE + "(?)}",
        cs -> {
          cs.setObject(1, buildSampleSiteStruct(cs, o, userId));
          cs.registerOutParameter(1, Types.STRUCT, WTR_SAMPLE_SITE_TYPE);
        },
        cs -> {
          Struct s = (Struct) cs.getObject(1);
          if (s == null) {
            return o;
          }
          Object[] a = s.getAttributes();
          return o.withIdentity(attrString(a, 0), attrString(a, 25));
        }
    );
  }

  private Struct buildSampleSiteStruct(java.sql.CallableStatement cs, WaterSampleSite o, String userId)
      throws java.sql.SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    Object[] a = new Object[] {
        o.waterSampleSiteId(), o.waterChecklistId(), o.statusCode(), o.waterSiteType(),
        o.waterStreamWidthCode(), o.evaluatorNameId(), o.domesticIntakeInd(), o.sampleSiteNumber(),
        o.utmSignal(), o.utmZone(), o.utmEasting(), o.utmNorthing(), o.roadTypeCode(),
        o.roadUseCode(), o.roadReference(), o.watershedReference(), o.communityWatershedInd(),
        o.rangeImpactEvaluationInd(), o.waterCompromisedInd(), o.otherObservedConditionInd(),
        o.otherObservedConditionDesc(), o.otherSolutionInd(), o.otherSolutionDescription(),
        o.assessmentComment(), o.rangeComment(), o.revisionCount(), o.entryUserid(), userId
    };
    return connection.createStruct(WTR_SAMPLE_SITE_TYPE, a);
  }

  // 252 Assessment (condition + solution VARRAYs)
  public WaterAssessment getWaterAssessment(String sampleSiteId) {
    return executeCall(
        "{call " + WTR_ASSESSMENT_GET + "(?,?,?)}",
        cs -> {
          cs.setString(1, sampleSiteId);
          cs.registerOutParameter(2, Types.ARRAY, WTR_ASSESSMENT_VARRAY);
          cs.registerOutParameter(3, Types.ARRAY, WTR_ASSESSMENT_VARRAY);
        },
        cs -> new WaterAssessment(sampleSiteId,
            readStructList(cs.getArray(2), this::assessmentRow),
            readStructList(cs.getArray(3), this::assessmentRow))
    );
  }

  public WaterAssessment saveWaterAssessment(WaterAssessment o, String userId) {
    return executeCall(
        callSql(WTR_ASSESSMENT_PKG, "save", 3),
        cs -> {
          cs.setString(1, o.waterSampleSiteId());
          cs.setObject(2, buildAssessmentArray(cs, o.conditions(), o.waterSampleSiteId(), userId));
          cs.registerOutParameter(2, Types.ARRAY, WTR_ASSESSMENT_VARRAY);
          cs.setObject(3, buildAssessmentArray(cs, o.solutions(), o.waterSampleSiteId(), userId));
          cs.registerOutParameter(3, Types.ARRAY, WTR_ASSESSMENT_VARRAY);
        },
        cs -> getWaterAssessment(o.waterSampleSiteId())
    );
  }

  // 253 Range (single VARRAY)
  public WaterRange getWaterRange(String sampleSiteId) {
    return executeCall(
        "{call " + WTR_RANGE_GET + "(?,?)}",
        cs -> {
          cs.setString(1, sampleSiteId);
          cs.registerOutParameter(2, Types.ARRAY, WTR_ASSESSMENT_VARRAY);
        },
        cs -> new WaterRange(sampleSiteId, readStructList(cs.getArray(2), this::assessmentRow))
    );
  }

  public WaterRange saveWaterRange(WaterRange o, String userId) {
    return executeCall(
        callSql(WTR_RANGE_PKG, "save", 2),
        cs -> {
          cs.setString(1, o.waterSampleSiteId());
          cs.setObject(2, buildAssessmentArray(cs, o.ranges(), o.waterSampleSiteId(), userId));
          cs.registerOutParameter(2, Types.ARRAY, WTR_ASSESSMENT_VARRAY);
        },
        cs -> getWaterRange(o.waterSampleSiteId())
    );
  }

  private WtrAssessmentRow assessmentRow(Object[] attrs) {
    return new WtrAssessmentRow(
        attrString(attrs, 0), attrString(attrs, 1), attrString(attrs, 2), attrString(attrs, 3),
        attrString(attrs, 4), attrString(attrs, 5), attrString(attrs, 6), attrString(attrs, 7),
        attrString(attrs, 8), attrString(attrs, 9));
  }

  private Array buildAssessmentArray(
      java.sql.CallableStatement cs, List<WtrAssessmentRow> rows, String sampleSiteId, String userId)
      throws java.sql.SQLException {
    return buildStructArray(cs, WTR_ASSESSMENT_VARRAY, WTR_ASSESSMENT_OBJECT, rows,
        r -> new Object[] {
            sampleSiteId, r.activityGrpCode(), r.activityGrpDesc(),
            blankToNull(r.activityGrpCount()), r.assessmentType(), r.assessmentDesc(),
            r.assessmentInd(), blankToNull(r.revisionCount()), r.entryUserid(), userId
        });
  }

  /** Build an input OBJECT with a single id attribute set (the GET procs key off it). */
  private static Struct newStruct(
      java.sql.CallableStatement cs, String type, int size, int idIndex, String idValue)
      throws java.sql.SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    Object[] attrs = new Object[size];
    attrs[idIndex] = idValue;
    return connection.createStruct(type, attrs);
  }

  /** Null for a blank string, so empty values are not passed to NUMBER struct attrs (ORA-17059). */
  private static Object blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

}
