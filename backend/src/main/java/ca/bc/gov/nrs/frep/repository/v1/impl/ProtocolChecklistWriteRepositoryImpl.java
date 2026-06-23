package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.ProtocolChecklistWriteRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import ca.bc.gov.nrs.frep.struct.v1.frep.BioCwdRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlotRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStandRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratumRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioWindthrowTreatment;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.struct.v1.frep.AdministrationData;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.EvaluatorRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.struct.v1.frep.StratumComputed;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import oracle.jdbc.OracleConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * Write path for the classic BIO/RIP/WTR protocol checklists, calling the legacy Oracle packages:
 * submit/unsubmit via {@code FREP_TOMBSTONE}, and the Biodiversity Opening save via
 * {@code FREP_210_BIO_OPENING.SAVE}. Mirrors legacy {@code SubmitPrintChecklistDataManager} and
 * {@code BiodiversityDataManager}.
 */
@Repository
public class ProtocolChecklistWriteRepositoryImpl extends AbstractFrepRepository implements ProtocolChecklistWriteRepository {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ProtocolChecklistWriteRepositoryImpl.class);

  private static final String TOMBSTONE = "FREP_TOMBSTONE";
  private static final String BIO_OPENING_PACKAGE = "frep_210_bio_opening";

  private static final String BIO_OPENING_SELECT =
      "SELECT frep_resource_value_id, frep_checklist_status_code, frep_wtp_override, "
          + "location_description, patch_reserves_on_block, patch_reserves_sampled, "
          + "innovtv_practice_answer_code, innovative_practices_comment, invasive_plant_answer_code, "
          + "invasive_plant_comment, frep_site_evaluation_code, evaluator_opinion_comment, "
          + "revision_count "
          + "FROM the.biodiversity_checklist WHERE biodiversity_checklist_id = ?";

  public ProtocolChecklistWriteRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
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
    BiodiversityOpening opening = jdbcTemplate.query(
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
              rs.getString("revision_count"),
              null, null, null
          );
        },
        checklistId
    );
    if (opening == null) {
      return null;
    }
    return fillOpeningResultsRefs(opening);
  }

  /**
   * Populates the read-only RESULTS reference fields (gross / net area, harvest-complete date) that
   * the legacy {@code FREP_210_BIO_OPENING.GET} derives from {@code frep_selected_site} (joined via
   * {@code frep_resource_value}). They are display-only — never persisted by the SAVE proc — so a
   * direct SELECT keeps them current without round-tripping. Leaves them null if the site row or its
   * resource-value link is missing.
   */
  private BiodiversityOpening fillOpeningResultsRefs(BiodiversityOpening opening) {
    String resourceValueId = opening.resourceValueId();
    if (resourceValueId == null || resourceValueId.isBlank()) {
      return opening;
    }
    List<BiodiversityOpening> refs = jdbcTemplate.query(
        "SELECT fss.opening_gross_area, fss.nar_area,"
            + " TO_CHAR(fss.disturbance_end_date, 'YYYY-MM-DD') AS harvest_date"
            + " FROM the.frep_selected_site fss"
            + " JOIN the.frep_resource_value frv"
            + " ON frv.frep_selected_site_id = fss.frep_selected_site_id"
            + " WHERE frv.frep_resource_value_id = ?",
        (rs, n) -> opening.withResultsRefs(
            rs.getString("opening_gross_area"),
            rs.getString("nar_area"),
            rs.getString("harvest_date")),
        resourceValueId);
    return refs.isEmpty() ? opening : refs.get(0);
  }

  /**
   * Reads the live {@code revision_count} for a biodiversity checklist (the optimistic-lock token
   * the SAVE proc compares against). Returns null when the row is absent. Used only for diagnostics.
   */
  private String currentBioRevisionCount(String checklistId) {
    try {
      return jdbcTemplate.queryForObject(
          "SELECT revision_count FROM the.biodiversity_checklist "
              + "WHERE biodiversity_checklist_id = ?",
          String.class, checklistId);
    } catch (EmptyResultDataAccessException ex) {
      return null;
    }
  }

  /**
   * Persist the Opening via {@code FREP_210_BIO_OPENING.SAVE} (16 positional params; checklist id,
   * resource id and revision_count are IN OUT; error_message is OUT). Returns the opening with the
   * id + revision the proc echoes back. Throws {@code StoredProcedureException} on a proc error
   * (includes the optimistic-lock conflict the proc raises on a stale revision_count).
   */
  public BiodiversityOpening saveBiodiversityOpening(BiodiversityOpening o, String userId) {
    // Diagnostic for the FREP_210_BIO_OPENING.SAVE optimistic-lock failure
    // (frep.web.usr.database.record.modified2): the proc's CHANGE updates
    // WHERE biodiversity_checklist_id = id AND revision_count = <token>, so a mismatch (or an
    // empty/NULL token, which Oracle TO_NUMBER('') turns into NULL → matches no rows) raises it.
    // Re-read the live token and compare it to what the client is sending.
    String dbRevision = currentBioRevisionCount(o.checklistId());
    if (dbRevision == null || !dbRevision.equals(nullIfBlank(o.revisionCount()))) {
      log.warn(
          "BIO 210 SAVE revision check: checklistId=[{}] sending revisionCount=[{}] "
              + "but current DB revision_count=[{}] (mismatch/blank → record.modified2)",
          o.checklistId(), o.revisionCount(), dbRevision);
    }
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
          + "stratum_size, revision_count FROM the.biodiversity_stratum "
          + "WHERE biodiversity_checklist_id = ? ORDER BY stratum_number";

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

  // Mirrors FREP_211_BIOSTRATUM.get: left-join the full windthrow-code catalogue
  // so EVERY code is returned (check_ind 'Y' when a row exists for this stratum,
  // else 'N'). SAVE_STRATUM loops the array bounds, so it must never be empty.
  private static final String WINDTHROW_SELECT =
      "SELECT wt.windthrow_treatment_id, wtc.windthrow_treatment_code, "
          + "DECODE(wt.windthrow_treatment_code, NULL, 'N', 'Y') AS check_ind "
          + "FROM the.windthrow_treatment_code wtc "
          + "LEFT JOIN the.windthrow_treatment wt "
          + "  ON wtc.windthrow_treatment_code = wt.windthrow_treatment_code "
          + " AND wt.stratum_id = ? "
          + "ORDER BY wtc.windthrow_treatment_code";

  public List<BioStratumRow> listBioStrata(String checklistId) {
    return jdbcTemplate.query(
        STRATUM_LIST_SELECT,
        (rs, rowNum) -> new BioStratumRow(
            rs.getString("stratum_id"),
            rs.getString("stratum_number"),
            rs.getString("biodiversity_strata_type_code"),
            rs.getString("summary_date"),
            rs.getString("stratum_plot_count"),
            rs.getString("stratum_size"),
            rs.getString("revision_count")),
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

  private static final String WINDTHROW_CODE_SELECT =
      "SELECT windthrow_treatment_code FROM the.windthrow_treatment_code "
          + "ORDER BY windthrow_treatment_code";

  private Array buildWindthrowArray(java.sql.CallableStatement cs, BioStratum s) throws java.sql.SQLException {
    OracleConnection connection = cs.getConnection().unwrap(OracleConnection.class);
    List<BioWindthrowTreatment> treatments =
        s.windthrowTreatments() == null ? List.of() : s.windthrowTreatments();
    // SAVE_STRATUM loops FIRST..LAST over this VARRAY; an empty array makes those
    // bounds NULL and raises ORA-06502. The legacy always sends the full code list,
    // so if we somehow received none, fall back to all-codes-unchecked (a no-op save).
    if (treatments.isEmpty()) {
      treatments = jdbcTemplate.queryForList(WINDTHROW_CODE_SELECT, String.class).stream()
          .map(code -> new BioWindthrowTreatment(null, code, "N"))
          .toList();
    }
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

  // Plots completed = count of plots in the stratum (FREP_211_BIOSTRATUM.get lines 456-460).
  private static final String PLOTS_COMPLETED_SELECT =
      "SELECT COUNT(*) FROM the.biodiversity_plot WHERE stratum_id = ?";

  // NAR (Net Area Reforested) = nar_area of the stratum's selected site, keyed off the
  // resource value (FREP_211_BIOSTRATUM.get lines 438-443).
  private static final String NAR_SELECT =
      "SELECT fss.nar_area FROM the.frep_selected_site fss, the.frep_resource_value frv "
          + "WHERE frv.frep_resource_value_id = ? "
          + "AND fss.frep_selected_site_id = frv.frep_selected_site_id";

  /**
   * Read-only computed values for the FREP211 Stratum Summary: NAR (Net Area Reforested) and
   * plots-completed. These are the two values the legacy {@code FREP_211_BIOSTRATUM.get} proc
   * derives (NAR out-param 78, plots-completed out-param 26); we run the proc's own two queries
   * directly rather than invoke the full 82-param proc — that proc also does tombstone /
   * evaluator-lead / status {@code SELECT ... INTO}s that raise {@code NO_DATA_FOUND} and sink the
   * whole call when the navigation context isn't perfectly reconstructed. Returns blanks (never
   * throws) when the stratum or its resource value can't be resolved.
   */
  public StratumComputed getStratumComputed(String stratumId) {
    String checklistId;
    try {
      checklistId = jdbcTemplate.queryForObject(
          "SELECT biodiversity_checklist_id FROM the.biodiversity_stratum WHERE stratum_id = ?",
          String.class, stratumId);
    } catch (EmptyResultDataAccessException ex) {
      return new StratumComputed(null, null);
    }
    Long plots = jdbcTemplate.queryForObject(PLOTS_COMPLETED_SELECT, Long.class, stratumId);
    return new StratumComputed(narForChecklist(checklistId), plots == null ? "0" : String.valueOf(plots));
  }

  /**
   * Computed values for a not-yet-saved stratum (the "Add stratum" form). NAR is site-level, so it
   * resolves from the checklist's resource value just like a saved stratum; plots-completed is 0
   * because the new stratum has no plots yet. Mirrors the legacy "Add New" flow, which calls the get
   * proc keyed on the resource value (NAR shown) with no stratum (COUNT(*) = 0).
   */
  public StratumComputed getNewStratumComputed(String checklistId) {
    return new StratumComputed(narForChecklist(checklistId), "0");
  }

  // NAR (Net Area Reforested) for a checklist's selected site — identical for every stratum.
  private String narForChecklist(String checklistId) {
    String resourceValueId = resolveResourceValueId(checklistId, "SLB");
    if (StringUtils.isBlank(resourceValueId)) {
      return null;
    }
    List<String> narRows = jdbcTemplate.query(NAR_SELECT, (rs, n) -> rs.getString(1), resourceValueId);
    return narRows.isEmpty() ? null : narRows.get(0);
  }

  // --- Biodiversity Plots (FREP screen 212) ---

  private static final String BIO_PLOT_PACKAGE = "FREP_212_BIOPLOT";
  private static final String STAND_VARRAY_TYPE = "THE.FREP_STAND_TABLE_VARRAY";
  private static final String STAND_OBJECT_TYPE = "THE.FREP_STAND_TABLE_OBJECT";
  private static final String CWD_VARRAY_TYPE = "THE.FREP_CWD_TABLE_VARRAY";
  private static final String CWD_OBJECT_TYPE = "THE.FREP_CWD_TABLE_OBJECT";

  private static final String PLOT_LIST_SELECT =
      "SELECT biodiversity_plot_id, plot_number, assessor_name, revision_count "
          + "FROM the.biodiversity_plot WHERE stratum_id = ? ORDER BY biodiversity_plot_id";

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
            rs.getString("assessor_name"),
            rs.getString("revision_count")),
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

  // --- Administration / Notes / Attachments (shared across bio / riparian / water) ---
  //
  // Each protocol's checklist row carries the frep_resource_value_id the shared procs join on, so
  // resolve it from the right table per resourceType ('SLB' | 'RIP' | 'WTR').

  private static final String COST_RESOURCE_PKG = "FREP_CHECKLIST_COST_RESOURCES";

  private String resolveResourceValueId(String checklistId, String resourceType) {
    String table = switch (resourceType) {
      case "SLB" -> "the.biodiversity_checklist";
      case "WTR" -> "the.water_checklist";
      default -> "the.riparian_checklist";
    };
    String idColumn = switch (resourceType) {
      case "SLB" -> "biodiversity_checklist_id";
      case "WTR" -> "water_checklist_id";
      default -> "riparian_checklist_id";
    };
    List<String> ids = jdbcTemplate.query(
        "SELECT frep_resource_value_id FROM " + table + " WHERE " + idColumn + " = ?",
        (rs, n) -> rs.getString(1),
        checklistId);
    return ids.isEmpty() ? "" : ids.get(0);
  }

  /**
   * Cost-resource scalars read directly from {@code biodiversity_checklist}, bypassing the drifted
   * {@code FREP_CHECKLIST_COST_RESOURCES.GET} out-params (@25-@28, @33). {@code revisionCountAccess}
   * is the table's {@code revision_count} — the optimistic-lock token the SAVE proc rewrites.
   */
  private record BiodiversityAdminScalars(String siteAccessCode, String blockAccessTime,
      String hoursOnBlock, String peopleOnBlock, String revisionCountAccess) {
  }

  private BiodiversityAdminScalars readBiodiversityAdminScalars(String checklistId) {
    List<BiodiversityAdminScalars> rows = jdbcTemplate.query(
        "SELECT frep_site_access_code, block_access_time, hours_on_block, people_on_block,"
            + " revision_count FROM the.biodiversity_checklist WHERE biodiversity_checklist_id = ?",
        (rs, n) -> new BiodiversityAdminScalars(
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
        checklistId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  /**
   * Read the Administration (cost/resource) data via {@code FREP_CHECKLIST_COST_RESOURCES.GET} (35
   * params per the legacy FrepCostResourceDataManager: tombstone 1-16, selectedSiteId @17,
   * resourceValueId IN @18, type @19, status @20, checklistId IN @21, ... siteAccessCode @25,
   * blockAccessTime @26, hoursOnBlock @27, peopleOnBlock @28, additionalComments @29, teamLeadNameId
   * @30, revisionCount @32, revisionCountAccess @33, error @34, team-member cursor @35).
   */
  public AdministrationData getAdministration(String checklistId, String resourceType) {
    String resourceValueId = resolveResourceValueId(checklistId, resourceType);
    // The deployed FREP_CHECKLIST_COST_RESOURCES.GET out-param layout has drifted from the legacy
    // source for the cost-resource scalars (the same GET-proc drift proven for FREP210/211): the
    // tombstone, status, team-lead and team-member-cursor params still line up, but @25-@28 (site
    // access / block-access time / hours / people on block) read blank even when the row holds
    // values, and SAVE persists fine. Read those four scalars plus the biodiversity_checklist
    // revision token straight from the table so the screen shows what SAVE actually wrote and the
    // save round-trips the true optimistic-lock token.
    BiodiversityAdminScalars direct =
        "SLB".equals(resourceType) ? readBiodiversityAdminScalars(checklistId) : null;
    return executeCall(
        callSql(COST_RESOURCE_PKG, "GET", 35),
        cs -> {
          for (int i = 1; i <= 17; i++) {
            cs.registerOutParameter(i, Types.VARCHAR);
          }
          cs.setString(18, resourceValueId);
          cs.registerOutParameter(19, Types.VARCHAR);
          cs.registerOutParameter(20, Types.VARCHAR);
          cs.setString(21, checklistId);
          for (int i = 22; i <= 34; i++) {
            cs.registerOutParameter(i, Types.VARCHAR);
          }
          registerOutCursor(cs, 35);
        },
        cs -> {
          throwIfError(COST_RESOURCE_PKG, "GET", cs.getString(34));
          // The cost-resource team-member cursor only returns EVALUATOR_USERID,
          // EVALUATOR_TEAM_LEAD_IND and REVISION_COUNT (FREP_RESOURCE_VALUE_ID and
          // EVALUATOR_DESCRIPTION are absent for this proc). Read each column only if present,
          // mirroring the legacy FrepCostResourceDataManager.populateFrepTeamMemeberBean, so an
          // absent column doesn't blow up with ORA-17006.
          List<EvaluatorRow> team = readCursor(cs, 35, rs -> new EvaluatorRow(
              getStringIfPresent(rs, "EVALUATOR_USERID"),
              getStringIfPresent(rs, "FREP_RESOURCE_VALUE_ID"),
              getStringIfPresent(rs, "EVALUATOR_TEAM_LEAD_IND"),
              getStringIfPresent(rs, "EVALUATOR_DESCRIPTION"),
              getStringIfPresent(rs, "REVISION_COUNT")));
          return new AdministrationData(
              checklistId,
              cs.getString(17),
              resourceValueId,
              cs.getString(19),
              cs.getString(20),
              cs.getString(11),
              direct != null ? direct.siteAccessCode() : cs.getString(25),
              direct != null ? direct.blockAccessTime() : cs.getString(26),
              direct != null ? direct.hoursOnBlock() : cs.getString(27),
              direct != null ? direct.peopleOnBlock() : cs.getString(28),
              cs.getString(29),
              cs.getString(30),
              cs.getString(31),
              cs.getString(32),
              direct != null ? direct.revisionCountAccess() : cs.getString(33),
              team);
        });
  }

  /**
   * Save the Administration scalar fields via {@code FREP_CHECKLIST_COST_RESOURCES.SAVE} (14 params:
   * selectedSiteId, resourceValueId, type, checklistId, statusCode, evaluationDate, siteAccessCode,
   * blockAccessTime, hoursOnBlock, peopleOnBlock, revisionCount, revisionCountAccess, userid, error).
   * Team membership is read-only here.
   */
  public AdministrationData saveAdministration(AdministrationData o, String userId) {
    executeCall(
        callSql(COST_RESOURCE_PKG, "SAVE", 14),
        cs -> {
          cs.setString(1, o.selectedSiteId());
          cs.setString(2, o.resourceValueId());
          cs.setString(3, o.resourceValueType());
          cs.setString(4, o.checklistId());
          cs.setString(5, o.statusCode());
          cs.setString(6, nullIfBlank(o.evaluationDate()));
          cs.setString(7, nullIfBlank(o.siteAccessCode()));
          cs.setString(8, nullIfBlank(o.blockAccessTime()));
          cs.setString(9, nullIfBlank(o.hoursOnBlock()));
          cs.setString(10, nullIfBlank(o.peopleOnBlock()));
          cs.setString(11, nullIfBlank(o.revisionCount()));
          cs.setString(12, nullIfBlank(o.revisionCountAccess()));
          cs.setString(13, userId);
          cs.registerOutParameter(14, Types.VARCHAR);
        },
        cs -> {
          throwIfError(COST_RESOURCE_PKG, "SAVE", cs.getString(14));
          return null;
        });
    return getAdministration(o.checklistId(), o.resourceValueType());
  }

  private static String nullIfBlank(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  /**
   * Returns the column's value, or {@code null} when the column is absent from the result set —
   * mirrors the legacy {@code FrepCostResourceDataManager} which guards each read with a
   * column-name presence check (some cursors omit columns the bean can carry).
   */
  private static String getStringIfPresent(ResultSet rs, String column) throws SQLException {
    ResultSetMetaData metaData = rs.getMetaData();
    for (int i = 1; i <= metaData.getColumnCount(); i++) {
      if (metaData.getColumnName(i).equalsIgnoreCase(column)) {
        return rs.getString(column);
      }
    }
    return null;
  }

  /** Add (or re-flag) an evaluator on the team via {@code save_team_member} (6 params). */
  public AdministrationData addTeamMember(
      String checklistId, String resourceType, String evaluator, boolean teamLead, String userId) {
    executeCall(
        callSql(COST_RESOURCE_PKG, "save_team_member", 6),
        cs -> {
          cs.setString(1, checklistId);
          cs.setString(2, resourceType);
          cs.setString(3, evaluator);
          cs.setString(4, teamLead ? "Y" : "N");
          cs.setString(5, userId);
          cs.registerOutParameter(6, Types.VARCHAR);
        },
        cs -> {
          throwIfError(COST_RESOURCE_PKG, "save_team_member", cs.getString(6));
          return null;
        });
    return getAdministration(checklistId, resourceType);
  }

  /** Remove an evaluator from the team via {@code delete_team_member} (5 params). */
  public AdministrationData deleteTeamMember(
      String checklistId, String resourceType, String evaluatorUserid, String revisionCount) {
    executeCall(
        callSql(COST_RESOURCE_PKG, "delete_team_member", 5),
        cs -> {
          cs.setString(1, evaluatorUserid);
          cs.setString(2, checklistId);
          cs.setString(3, resourceType);
          cs.setString(4, nullIfBlank(revisionCount));
          cs.registerOutParameter(5, Types.VARCHAR);
        },
        cs -> {
          throwIfError(COST_RESOURCE_PKG, "delete_team_member", cs.getString(5));
          return null;
        });
    return getAdministration(checklistId, resourceType);
  }

  // --- Notes (FREP checklistNote / FREP_CHECKLIST_NOTES) ---

  private static final String NOTES_PKG = "FREP_CHECKLIST_NOTES";

  /**
   * Read the single note for a riparian checklist via {@code FREP_CHECKLIST_NOTES.GET} (25 params:
   * tombstone 1-18, checklist_id IN OUT @19, status @20, resource_value_id IN OUT @21, type IN OUT
   * @22, note_description @23, revision_count @24, error @25).
   */
  public RiparianNotes getNotes(String checklistId, String resourceType) {
    String resourceValueId = resolveResourceValueId(checklistId, resourceType);
    return executeCall(
        callSql(NOTES_PKG, "GET", 25),
        cs -> {
          for (int i = 1; i <= 18; i++) {
            cs.registerOutParameter(i, Types.VARCHAR);
          }
          setInOutString(cs, 19, checklistId);
          cs.registerOutParameter(20, Types.VARCHAR);
          setInOutString(cs, 21, resourceValueId);
          setInOutString(cs, 22, resourceType);
          cs.registerOutParameter(23, Types.VARCHAR);
          cs.registerOutParameter(24, Types.VARCHAR);
          cs.registerOutParameter(25, Types.VARCHAR);
        },
        cs -> {
          throwIfError(NOTES_PKG, "GET", cs.getString(25));
          return new RiparianNotes(checklistId, cs.getString(23), cs.getString(24));
        });
  }

  /**
   * Save the checklist note via the public {@code FREP_CHECKLIST_NOTES.SAVE} (7 params). SAVE is the
   * only public save proc — it {@code CASE}-dispatches on {@code p_resource_value_type} to the
   * package-private {@code save_riparian_notes}/{@code save_biodiversity_notes}/
   * {@code save_water_notes}, which are NOT callable directly (PLS-00302).
   */
  public RiparianNotes saveNotes(RiparianNotes o, String resourceType, String userId) {
    String resourceValueId = resolveResourceValueId(o.checklistId(), resourceType);
    executeCall(
        callSql(NOTES_PKG, "SAVE", 7),
        cs -> {
          setInOutString(cs, 1, o.checklistId());
          setInOutString(cs, 2, resourceValueId);
          cs.setString(3, resourceType);
          cs.setString(4, nullIfBlank(o.noteDescription()));
          setInOutString(cs, 5, nullIfBlank(o.revisionCount()));
          cs.setString(6, userId);
          cs.registerOutParameter(7, Types.VARCHAR);
        },
        cs -> {
          throwIfError(NOTES_PKG, "SAVE", cs.getString(7));
          return null;
        });
    return getNotes(o.checklistId(), resourceType);
  }

  // --- Attachments (FREP checklistAttachment / FREP_CHECKLIST_ATTACHMENTS) ---

  private static final String ATTACH_PKG = "FREP_CHECKLIST_ATTACHMENTS";

  /**
   * List attachment metadata for a riparian checklist via {@code FREP_CHECKLIST_ATTACHMENTS.GET}
   * (24 params: tombstone 1-18, resource_value_id IN @19, checklist_id IN @20, type @21, status
   * @22, error @23, results cursor @24). Cursor columns per the legacy DataManager.
   */
  public List<AttachmentRow> getAttachments(String checklistId, String resourceType) {
    String resourceValueId = resolveResourceValueId(checklistId, resourceType);
    return executeCall(
        callSql(ATTACH_PKG, "GET", 24),
        cs -> {
          for (int i = 1; i <= 18; i++) {
            cs.registerOutParameter(i, Types.VARCHAR);
          }
          cs.setString(19, resourceValueId);
          cs.setString(20, checklistId);
          cs.registerOutParameter(21, Types.VARCHAR);
          cs.registerOutParameter(22, Types.VARCHAR);
          cs.registerOutParameter(23, Types.VARCHAR);
          registerOutCursor(cs, 24);
        },
        cs -> {
          throwIfError(ATTACH_PKG, "GET", cs.getString(23));
          return readCursor(cs, 24, rs -> new AttachmentRow(
              rs.getString("chklst_attach_id"), rs.getString("file_name"),
              rs.getString("description"), rs.getString("MIME_TYPE_CODE"),
              rs.getString("file_size")));
        });
  }

  /**
   * Download an attachment's bytes via {@code GET_BLOB} (10 params: id/checklist/type/name/desc/
   * mime-code/mime-type IN OUT 1-7, file_contents BLOB @8, userid @9, error @10).
   */
  public AttachmentContent getAttachmentContent(
      String checklistId, String resourceType, String attachmentId) {
    return executeCall(
        callSql(ATTACH_PKG, "GET_BLOB", 10),
        cs -> {
          setInOutString(cs, 1, attachmentId);
          setInOutString(cs, 2, checklistId);
          setInOutString(cs, 3, resourceType);
          setInOutString(cs, 4, null);
          setInOutString(cs, 5, null);
          setInOutString(cs, 6, null);
          setInOutString(cs, 7, null);
          cs.registerOutParameter(8, Types.BLOB);
          setInOutString(cs, 9, null);
          cs.registerOutParameter(10, Types.VARCHAR);
        },
        cs -> {
          throwIfError(ATTACH_PKG, "GET_BLOB", cs.getString(10));
          java.sql.Blob blob = cs.getBlob(8);
          byte[] bytes = blob == null ? new byte[0] : blob.getBytes(1, (int) blob.length());
          return new AttachmentContent(cs.getString(4), cs.getString(7), bytes);
        });
  }

  /**
   * Upload a new attachment. The legacy flow is two steps, because {@code SAVE} only <em>updates</em>
   * an existing row (it never inserts): first {@code GET_BLOB_FOR_UPDATE} with a null id <em>inserts</em>
   * the metadata row + an EMPTY_BLOB content row and returns the new id (committing), then {@code SAVE}
   * updates that row's file_name/description and writes the BLOB content.
   */
  public void saveAttachment(
      String checklistId, String resourceType, String fileName, String description, String mimeType,
      byte[] bytes, String userId) {
    String attachmentId = createAttachmentRecord(checklistId, resourceType, fileName, description,
        mimeType, userId);
    writeAttachmentContent(attachmentId, checklistId, resourceType, fileName, description, mimeType,
        bytes, userId);
  }

  /**
   * Insert the attachment metadata + empty content row via {@code GET_BLOB_FOR_UPDATE} (10 params,
   * all IN OUT; id @1 null = new). Returns the newly generated attachment id.
   */
  private String createAttachmentRecord(
      String checklistId, String resourceType, String fileName, String description, String mimeType,
      String userId) {
    return executeCall(
        callSql(ATTACH_PKG, "GET_BLOB_FOR_UPDATE", 10),
        cs -> {
          setInOutString(cs, 1, null); // null id => INSERT a new record
          setInOutString(cs, 2, checklistId);
          setInOutString(cs, 3, resourceType);
          setInOutString(cs, 4, fileName);
          setInOutString(cs, 5, nullIfBlank(description));
          setInOutString(cs, 6, mimeTypeCode(fileName));
          setInOutString(cs, 7, mimeType);
          cs.setNull(8, Types.BLOB);
          cs.registerOutParameter(8, Types.BLOB);
          setInOutString(cs, 9, userId);
          cs.registerOutParameter(10, Types.VARCHAR);
        },
        cs -> {
          throwIfError(ATTACH_PKG, "GET_BLOB_FOR_UPDATE", cs.getString(10));
          return cs.getString(1); // new attachment id
        });
  }

  /**
   * Write the file bytes + metadata onto an existing attachment via {@code SAVE} (10 params; id @1,
   * checklist @2, type @3, file_name @4, description @5, mime_type_code @6, mime_type IN OUT @7,
   * BLOB @8, userid @9, error @10).
   */
  private void writeAttachmentContent(
      String attachmentId, String checklistId, String resourceType, String fileName,
      String description, String mimeType, byte[] bytes, String userId) {
    executeCall(
        callSql(ATTACH_PKG, "SAVE", 10),
        cs -> {
          setInOutString(cs, 1, attachmentId);
          setInOutString(cs, 2, checklistId);
          setInOutString(cs, 3, resourceType);
          cs.setString(4, fileName);
          cs.setString(5, nullIfBlank(description));
          cs.setString(6, mimeTypeCode(fileName));
          setInOutString(cs, 7, mimeType);
          java.sql.Blob blob = cs.getConnection().createBlob();
          blob.setBytes(1, bytes);
          cs.setBlob(8, blob);
          cs.setString(9, userId);
          cs.registerOutParameter(10, Types.VARCHAR);
        },
        cs -> {
          throwIfError(ATTACH_PKG, "SAVE", cs.getString(10));
          return null;
        });
  }

  /** Delete an attachment via {@code REMOVE} (4 params: id, checklist, type, error). */
  public void deleteAttachment(String checklistId, String resourceType, String attachmentId) {
    executeCall(
        callSql(ATTACH_PKG, "REMOVE", 4),
        cs -> {
          cs.setString(1, attachmentId);
          cs.setString(2, checklistId);
          cs.setString(3, resourceType);
          cs.registerOutParameter(4, Types.VARCHAR);
        },
        cs -> {
          throwIfError(ATTACH_PKG, "REMOVE", cs.getString(4));
          return null;
        });
  }

  /** A short stored mime-type code derived from the file extension (no reference table). */
  private static String mimeTypeCode(String fileName) {
    if (fileName == null) {
      return null;
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return null;
    }
    String ext = fileName.substring(dot + 1).toUpperCase();
    return ext.length() > 20 ? ext.substring(0, 20) : ext;
  }

  /** Null for a blank string, so empty values are not passed to NUMBER struct attrs (ORA-17059). */
  private static Object blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

}
