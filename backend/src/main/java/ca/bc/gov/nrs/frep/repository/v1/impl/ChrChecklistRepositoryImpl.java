package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.ChrChecklistRepository;

import ca.bc.gov.nrs.frep.util.UuidUtils;
import ca.bc.gov.nrs.frep.repository.v1.AbstractFrepRepository;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChrChecklistRepositoryImpl extends AbstractFrepRepository implements ChrChecklistRepository {

  public ChrChecklistRepositoryImpl(@Qualifier("oracleJdbcTemplate") JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  public String getChecklistStatus(long checklistId) {
    return jdbcTemplate.queryForObject(
        "SELECT frep_checklist_status_code FROM the.chr_checklist WHERE chr_checklist_id = ?",
        String.class,
        checklistId
    );
  }

  public long getRevisionCount(long checklistId) {
    Long revision = jdbcTemplate.queryForObject(
        "SELECT revision_count FROM the.chr_checklist WHERE chr_checklist_id = ?",
        Long.class,
        checklistId
    );
    return revision == null ? 0L : revision;
  }

  public String getLastUpdatedUser(long checklistId) {
    return jdbcTemplate.queryForObject(
        "SELECT update_userid FROM the.chr_checklist WHERE chr_checklist_id = ?",
        String.class,
        checklistId
    );
  }

  public UUID parseDeviceCheckoutGuid(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    return UuidUtils.asUuid(bytes);
  }

  public UUID getDeviceCheckoutGuid(long checklistId) {
    byte[] bytes = jdbcTemplate.queryForObject(
        "SELECT device_checkout_guid FROM the.chr_checklist WHERE chr_checklist_id = ?",
        byte[].class,
        checklistId
    );
    return parseDeviceCheckoutGuid(bytes);
  }

}
