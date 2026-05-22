package ca.bc.gov.nrs.frep.dto;

/**
 * District-level org unit reference entry.
 *
 * <p>Legacy equivalent: rows from {@code ORG_UNIT} where {@code org_level_code = 'D'}.
 *
 * @param orgUnitNo    primary key, e.g. {@code "56"}
 * @param orgUnitCode  short code, e.g. {@code "DCK"}
 * @param orgUnitName  display name, e.g. {@code "Chilliwack Forest District"}
 */
public record OrgUnitResponse(
    String orgUnitNo,
    String orgUnitCode,
    String orgUnitName
) {}
