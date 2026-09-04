package ca.bc.gov.nrs.frep.repository.v1.impl;

import ca.bc.gov.nrs.frep.repository.v1.AcceptedSitesRepository;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;
import ca.bc.gov.nrs.frep.repository.v1.bean.AcceptedSiteRow;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accepted/targeted sites for the FREP200 dashboard. Replaces legacy package
 * {@code FREP_200_ACCEPTED_SITES.get} with a single native query.
 *
 * <p>The legacy proc returned BIO/RIP/WTR via a bounded VARRAY ({@code BULK COLLECT}) and hard-excluded
 * CHR, which the new app then fetched with a separate query and merged in Java. This query does both in
 * one pass — a {@code UNION ALL} of the BIO and CHR branches — dropping RIP/WTR (out of migration scope),
 * the unused header counts, and the unused map-extent columns, and removing the VARRAY cap.
 *
 * <p>Schema-qualified to {@code THE} (the app connects as a different user; see SearchRepositoryImpl).
 * The BIO branch keeps the proc's {@code cut_block_open_admin} inner join for exact result parity, so the
 * app user needs SELECT on {@code THE.cut_block_open_admin}; CHR omits it (it never had it). Tenure
 * (licence/CP/cut block) comes from {@code frep_selected_site} in both, as in the proc.
 */
@Repository
public class AcceptedSitesRepositoryImpl extends AbstractFrepRepository
    implements AcceptedSitesRepository {

  private final NamedParameterJdbcTemplate namedJdbc;

  public AcceptedSitesRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
    this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  @Override
  public List<AcceptedSiteRow> findAcceptedSites(String orgUnitNo, String effectiveYear) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("effectiveYear", effectiveYear)
        .addValue("orgUnit", orgUnitNo);
    return namedJdbc.query(
        ACCEPTED_SITES_SQL,
        params,
        (rs, rowNum) -> new AcceptedSiteRow(
            cleanString(numberString(rs, "checklist_id")),
            cleanString(rs.getString("checklist_type")),
            cleanString(numberString(rs, "sample_number")),
            cleanString(rs.getString("resource_value_stat_code")),
            cleanString(rs.getString("checklist_status_code")),
            cleanString(rs.getString("opening_number")),
            cleanString(rs.getString("opening_id")),
            cleanString(rs.getString("licence_id")),
            cleanString(rs.getString("cutting_permit_id")),
            cleanString(rs.getString("cut_block_id")),
            cleanString(rs.getString("harvest_complete_date"))));
  }

  /**
   * BIO + CHR accepted/targeted sites. The BIO branch ports {@code FREP_200_ACCEPTED_SITES.get} (filtered
   * to SLB and its go-forward code SLR, with the {@code cut_block_open_admin} inner join and the optional checklist outer join, so
   * targeted sites without a checklist still appear). The CHR branch is the new app's own join (inner —
   * CHR rows only exist once a checklist does). Both source the same 11 columns the dashboard reads;
   * {@code sample_number} is always NULL for BIO and CHR (the proc only populates it for RIP).
   */
  private static final String ACCEPTED_SITES_SQL = """
      SELECT bc.biodiversity_checklist_id              AS checklist_id
           , frvtc.description                         AS checklist_type
           , NULL                                      AS sample_number
           , frv.frep_resource_value_stat_code         AS resource_value_stat_code
           , bc.frep_checklist_status_code             AS checklist_status_code
           , THE.frep_formatted_mapsheet(fss.mapsheet_grid, fss.mapsheet_letter, fss.mapsheet_square,
                                         fss.mapsheet_quad, fss.mapsheet_sub_quad, fss.opening_number)
                                                       AS opening_number
           , fss.opening_id                            AS opening_id
           , fss.forest_file_id                        AS licence_id
           , fss.cutting_permit_id                     AS cutting_permit_id
           , fss.cut_block_id                          AS cut_block_id
           , TO_CHAR(fss.disturbance_end_date, 'YYYY-MM-DD')
                                                       AS harvest_complete_date
        FROM THE.frep_selected_site fss
           , THE.frep_resource_value frv
           , THE.cut_block_open_admin cboa
           , THE.frep_resource_value_type_code frvtc
           , THE.biodiversity_checklist bc
       WHERE fss.frep_selected_site_id = frv.frep_selected_site_id
         AND frv.frep_resource_value_type_code = frvtc.frep_resource_value_type_code
         AND fss.cut_block_open_admin_id = cboa.cut_block_open_admin_id
         AND frv.frep_resource_value_id = bc.frep_resource_value_id (+)
         AND fss.effective_year = to_number(:effectiveYear)
         AND fss.org_unit_no = to_number(:orgUnit)
         AND frv.frep_resource_value_stat_code <> 'REJ'
         AND frv.frep_resource_value_type_code IN ('SLB', 'SLR')
      UNION ALL
      SELECT cc.chr_checklist_id                       AS checklist_id
           , frvtc.description                         AS checklist_type
           , NULL                                      AS sample_number
           , frv.frep_resource_value_stat_code         AS resource_value_stat_code
           , cc.frep_checklist_status_code             AS checklist_status_code
           , THE.frep_formatted_mapsheet(fss.mapsheet_grid, fss.mapsheet_letter, fss.mapsheet_square,
                                         fss.mapsheet_quad, fss.mapsheet_sub_quad, fss.opening_number)
                                                       AS opening_number
           , fss.opening_id                            AS opening_id
           , fss.forest_file_id                        AS licence_id
           , fss.cutting_permit_id                     AS cutting_permit_id
           , fss.cut_block_id                          AS cut_block_id
           , TO_CHAR(fss.disturbance_end_date, 'YYYY-MM-DD')
                                                       AS harvest_complete_date
        FROM THE.frep_selected_site fss
           , THE.frep_resource_value frv
           , THE.chr_checklist cc
           , THE.frep_resource_value_type_code frvtc
       WHERE fss.frep_selected_site_id = frv.frep_selected_site_id
         AND frv.frep_resource_value_id = cc.frep_resource_value_id
         AND frv.frep_resource_value_type_code = frvtc.frep_resource_value_type_code
         AND fss.effective_year = to_number(:effectiveYear)
         AND fss.org_unit_no = to_number(:orgUnit)
         AND frv.frep_resource_value_stat_code <> 'REJ'
         AND frv.frep_resource_value_type_code = 'CHR'
       ORDER BY checklist_type, opening_id, checklist_id
      """;

  private static String cleanString(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.endsWith(".0")) {
      try {
        Double.parseDouble(trimmed);
        return trimmed.substring(0, trimmed.length() - 2);
      } catch (NumberFormatException ignored) {
        // keep original
      }
    }
    return trimmed;
  }
}
