package ca.bc.gov.nrs.frep.repository.frep;

import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import java.sql.Types;
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
          + "innovative_practice_ind, innovative_practices_comment, invasive_plant_indicator, "
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
              rs.getString("innovative_practice_ind"),
              rs.getString("innovative_practices_comment"),
              rs.getString("invasive_plant_indicator"),
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
}
