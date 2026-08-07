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
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentContent;
import ca.bc.gov.nrs.frep.struct.v1.frep.AttachmentRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes;
import ca.bc.gov.nrs.frep.struct.v1.frep.StratumComputed;
import ca.bc.gov.nrs.frep.service.v1.ObjectStorageService;
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
import org.springframework.transaction.annotation.Transactional;

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

  // Left-joins the evaluation-team lead (biodiversity_evaluator_name, team_lead_ind='Y') so the
  // Opening carries the evaluator + its own revision token. Columns are bc.-qualified because both
  // tables have a revision_count.
  private static final String BIO_OPENING_SELECT =
      "SELECT bc.frep_resource_value_id, bc.frep_checklist_status_code, bc.frep_wtp_override, "
          + "bc.location_description, bc.patch_reserves_on_block, bc.patch_reserves_sampled, "
          + "bc.innovtv_practice_answer_code, bc.innovative_practices_comment, "
          + "bc.invasive_plant_answer_code, bc.invasive_plant_comment, bc.frep_site_evaluation_code, "
          + "bc.evaluator_opinion_comment, TO_CHAR(bc.evaluation_date, 'YYYY-MM-DD') AS evaluation_date, "
          + "bc.revision_count, ben.evaluator_userid AS team_lead_userid, "
          + "ben.revision_count AS team_lead_revision_count "
          + "FROM the.biodiversity_checklist bc "
          + "LEFT JOIN the.biodiversity_evaluator_name ben "
          + "ON ben.biodiversity_checklist_id = bc.biodiversity_checklist_id "
          + "AND ben.evaluator_team_lead_ind = 'Y' "
          + "WHERE bc.biodiversity_checklist_id = ?";

  private final ObjectStorageService objectStorage;

  public ProtocolChecklistWriteRepositoryImpl(
      @Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate,
      ObjectStorageService objectStorage) {
    super(jdbcTemplate);
    this.objectStorage = objectStorage;
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
              rs.getString("evaluation_date"),
              rs.getString("revision_count"),
              null, null, null,
              rs.getString("team_lead_userid"),
              null, // teamLeadName — resolved via FAM in the service
              rs.getString("team_lead_revision_count")
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
   * Persist the Opening via {@code FREP_210_BIO_OPENING.SAVE} (17 positional params; checklist id,
   * resource id and revision_count are IN OUT; error_message is OUT; evaluation_date is the optional
   * trailing param — a null leaves the stored value untouched). Returns the opening with the
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
        callSql(BIO_OPENING_PACKAGE, "SAVE", 17),
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
          cs.setString(17, o.evaluationDate());
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
          cs.setString(3, "SLR"); // resource_value_type — new records are SLR (SLB saves are guard-blocked)
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

  @Override
  public String checklistIdForStratum(String stratumId) {
    return firstOrNull(jdbcTemplate.query(
        "SELECT biodiversity_checklist_id FROM the.biodiversity_stratum WHERE stratum_id = ?",
        (rs, n) -> rs.getString(1), stratumId));
  }

  @Override
  public String checklistIdForPlot(String plotId) {
    return firstOrNull(jdbcTemplate.query(
        "SELECT s.biodiversity_checklist_id FROM the.biodiversity_stratum s "
            + "JOIN the.biodiversity_plot p ON p.stratum_id = s.stratum_id "
            + "WHERE p.biodiversity_plot_id = ?",
        (rs, n) -> rs.getString(1), plotId));
  }

  private static String firstOrNull(List<String> rows) {
    return rows.isEmpty() ? null : rows.get(0);
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
            null, // display name resolved via FAM in the service
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
          cwd,
          null // display name resolved via FAM in the service
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
          cs.setString(3, "SLR"); // resource_value_type — new records are SLR (SLB saves are guard-blocked)
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

  // --- Administration / Notes / Attachments (shared procs, biodiversity only) ---
  //
  // The biodiversity checklist row carries the frep_resource_value_id the shared procs join on.
  // Biodiversity is SLB (legacy) / SLR (going forward) — both live in biodiversity_checklist.

  private static final String COST_RESOURCE_PKG = "FREP_CHECKLIST_COST_RESOURCES";

  /** SLB (legacy) and its go-forward code SLR are the two biodiversity resource types. */
  static boolean isBiodiversity(String resourceType) {
    return "SLB".equals(resourceType) || "SLR".equals(resourceType);
  }

  private String resolveResourceValueId(String checklistId, String resourceType) {
    if (!isBiodiversity(resourceType)) {
      throw new IllegalArgumentException("Unsupported protocol resource type: " + resourceType);
    }
    List<String> ids = jdbcTemplate.query(
        "SELECT frep_resource_value_id FROM the.biodiversity_checklist "
            + "WHERE biodiversity_checklist_id = ?",
        (rs, n) -> rs.getString(1),
        checklistId);
    return ids.isEmpty() ? "" : ids.get(0);
  }

  private static String nullIfBlank(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  /**
   * Make {@code newLead} the sole team lead ("Assign it to me" takeover). Removes the current lead
   * first (single-lead invariant, like the legacy UI) when it's someone else, then re-flags/adds the
   * caller as lead. No {@code getAdministration} re-read — the Opening flow re-reads itself. The
   * evaluator table has its own revision token, independent of the checklist's.
   *
   * <p><b>Transactional for a reason.</b> {@code delete_bio_team_member} reports "this evaluator
   * still has plots" by appending message keys to its OUT parameter, but it does <em>not</em> stop:
   * it falls through and deletes the evaluator row anyway (there is no guard between the check loop
   * and {@code frep_biodiversity_eval_name.remove()}). Under autocommit that left the checklist with
   * no evaluator at all whenever the takeover was refused — the delete committed, the exception
   * skipped {@code save_team_member}, and the Opening header went blank. Neither proc commits
   * internally (both do plain DML), so one transaction across both calls makes the takeover atomic:
   * either the new lead is installed or nothing changes.
   */
  @Transactional
  public void assignBiodiversityLead(String checklistId, String resourceType, String newLead,
      String oldLead, String oldRevision, String userId) {
    if (StringUtils.isNotBlank(oldLead) && !oldLead.equalsIgnoreCase(newLead)) {
      executeCall(
          callSql(COST_RESOURCE_PKG, "delete_team_member", 5),
          cs -> {
            cs.setString(1, oldLead);
            cs.setString(2, checklistId);
            cs.setString(3, resourceType);
            cs.setString(4, nullIfBlank(oldRevision));
            cs.registerOutParameter(5, Types.VARCHAR);
          },
          cs -> {
            throwIfError(COST_RESOURCE_PKG, "delete_team_member", cs.getString(5));
            return null;
          });
    }
    executeCall(
        callSql(COST_RESOURCE_PKG, "save_team_member", 6),
        cs -> {
          cs.setString(1, checklistId);
          cs.setString(2, resourceType);
          cs.setString(3, newLead);
          cs.setString(4, "Y");
          cs.setString(5, userId);
          cs.registerOutParameter(6, Types.VARCHAR);
        },
        cs -> {
          throwIfError(COST_RESOURCE_PKG, "save_team_member", cs.getString(6));
          return null;
        });
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
   * Biodiversity attachment bytes live in shared object storage, not the Oracle BLOB. Metadata
   * still goes through {@code FREP_CHECKLIST_ATTACHMENTS} (list/insert/remove); only the file content
   * moves. Keyed by the unique attachment id under an {@code slr/} prefix — collision-free vs the CHR
   * photo keys and independent of the DB resource type (SLB legacy / SLR go-forward). Other protocols
   * (RIP/WTR) keep using the Oracle BLOB path unchanged.
   */
  private static final String BIO_OBJECT_PREFIX = "slr/";

  private static boolean isBioAttachment(String resourceType) {
    return isBiodiversity(resourceType);
  }

  private static String bioObjectKey(String attachmentId) {
    return BIO_OBJECT_PREFIX + attachmentId.trim();
  }

  /**
   * One page of attachment metadata, newest first.
   *
   * <p>Descending by creation time is a UX decision: ascending puts a newly uploaded attachment on
   * the <em>last</em> page, so a user on page 1 sees nothing change after uploading. The id is a
   * tiebreaker because {@code entry_timestamp} is an Oracle DATE (second precision) — attachments
   * added in the same second would otherwise page non-deterministically.
   */
  private static final String BIO_ATTACHMENTS_PAGE = """
      SELECT bca.biodiversity_chklst_attach_id AS chklst_attach_id
           , bca.file_name
           , bca.description
           , bca.mime_type_code
        FROM THE.biodiversity_chklst_attach bca
       WHERE bca.biodiversity_checklist_id = ?
       ORDER BY bca.entry_timestamp DESC, bca.biodiversity_chklst_attach_id DESC
      OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
      """;

  private static final String BIO_ATTACHMENTS_COUNT =
      "SELECT COUNT(*) FROM THE.biodiversity_chklst_attach WHERE biodiversity_checklist_id = ?";

  /**
   * One page of a Biodiversity checklist's attachment metadata, read directly rather than through
   * {@code FREP_CHECKLIST_ATTACHMENTS.GET}.
   *
   * <p>The package cursor has no paging and returns the whole set, and it joins
   * {@code biodiversity_attach_content} solely to compute {@code file_size} from
   * {@code DBMS_LOB.getlength}. That column is useless to us — Biodiversity bytes live in object
   * storage and the BLOB is deliberately left empty, so it always reads 0.00 — and the join is an
   * inner one, which would silently hide any attachment row lacking a content row. Dropping both
   * leaves a single-table read; size is filled from object storage by the service.
   *
   * <p>Requires SELECT on {@code THE.BIODIVERSITY_CHKLST_ATTACH} (the proc ran with definer rights
   * and needed no such grant); confirmed present 2026-08-06.
   */
  @Override
  public List<AttachmentRow> getAttachments(String checklistId, String resourceType, int page, int size) {
    return jdbcTemplate.query(
        BIO_ATTACHMENTS_PAGE,
        (rs, n) -> new AttachmentRow(
            trimNumericId(rs.getString("chklst_attach_id")), rs.getString("file_name"),
            rs.getString("description"), rs.getString("mime_type_code"), null),
        Long.valueOf(checklistId), (long) page * size, size);
  }

  /** Oracle JDBC renders a NUMBER id as e.g. "77.0"; the object key and the API use "77". */
  private static String trimNumericId(String value) {
    if (value == null) {
      return null;
    }
    return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
  }

  @Override
  public int countAttachments(String checklistId, String resourceType) {
    Integer count = jdbcTemplate.queryForObject(
        BIO_ATTACHMENTS_COUNT, Integer.class, Long.valueOf(checklistId));
    return count == null ? 0 : count;
  }


  /**
   * Download an attachment's bytes. Metadata (file name, mime) always comes from {@code GET_BLOB}; for
   * Biodiversity (SLB) the bytes come from object storage ({@code slr/<id>}) instead of the Oracle BLOB,
   * with a fallback to the BLOB for rows not yet migrated (dual-read during the cutover).
   */
  public AttachmentContent getAttachmentContent(
      String checklistId, String resourceType, String attachmentId) {
    AttachmentContent viaBlob = getAttachmentContentFromBlob(checklistId, resourceType, attachmentId);
    if (!isBioAttachment(resourceType)) {
      return viaBlob;
    }
    String key = bioObjectKey(attachmentId);
    if (objectStorage.objectExists(key)) {
      return new AttachmentContent(viaBlob.fileName(), viaBlob.mimeType(), objectStorage.getObjectBytes(key));
    }
    // Not in object storage yet — pre-migration row whose bytes are still in the Oracle BLOB.
    log.warn("BIO attachment {} not found in object storage (key {}); serving from Oracle BLOB",
        attachmentId, key);
    return viaBlob;
  }

  /**
   * The raw {@code GET_BLOB} read (10 params: id/checklist/type/name/desc/mime-code/mime-type IN OUT
   * 1-7, file_contents BLOB @8, userid @9, error @10) — returns metadata + whatever bytes are in the
   * Oracle BLOB (empty for a migrated BIO row).
   */
  private AttachmentContent getAttachmentContentFromBlob(
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
    if (!isBioAttachment(resourceType)) {
      writeAttachmentContent(attachmentId, checklistId, resourceType, fileName, description, mimeType,
          bytes, userId);
      return;
    }
    // BIO: finalize the metadata exactly as legacy does but leave the Oracle BLOB empty; the real
    // bytes go to object storage. GET_BLOB_FOR_UPDATE already committed the metadata row, so if the
    // object-storage put fails we must compensate by removing that row — otherwise it orphans (a
    // listed attachment whose bytes 404 on download, with no BLOB fallback after cutover).
    writeAttachmentContent(attachmentId, checklistId, resourceType, fileName, description, mimeType,
        new byte[0], userId);
    try {
      objectStorage.putObject(bioObjectKey(attachmentId), mimeType, bytes);
    } catch (RuntimeException ex) {
      try {
        removeAttachmentRecord(checklistId, resourceType, attachmentId);
      } catch (RuntimeException cleanup) {
        log.error("Orphaned BIO attachment metadata {} could not be removed after object-storage "
            + "failure; manual cleanup required", attachmentId, cleanup);
      }
      throw new IllegalStateException(
          "Failed to store BIO attachment " + attachmentId + " in object storage", ex);
    }
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

  /**
   * Delete an attachment: remove the metadata row, then (BIO only) delete its object-storage object.
   * The object delete is best-effort — a stray object with no metadata row is invisible to the app and
   * harmless (swept up by reconciliation), so it must not fail the delete.
   */
  public void deleteAttachment(String checklistId, String resourceType, String attachmentId) {
    removeAttachmentRecord(checklistId, resourceType, attachmentId);
    if (isBioAttachment(resourceType)) {
      try {
        objectStorage.deleteObject(bioObjectKey(attachmentId));
      } catch (RuntimeException ex) {
        log.warn("Could not delete BIO attachment object {} after removing its metadata row; "
            + "leaving a stray object", bioObjectKey(attachmentId), ex);
      }
    }
  }

  /** Remove the attachment metadata row via {@code REMOVE} (4 params: id, checklist, type, error). */
  private void removeAttachmentRecord(String checklistId, String resourceType, String attachmentId) {
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
