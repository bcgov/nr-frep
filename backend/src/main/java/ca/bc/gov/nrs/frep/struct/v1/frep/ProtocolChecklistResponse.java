package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Aggregate payload for a single protocol checklist (BIO / RIP / WAT).
 *
 * <p>Combines the legacy multi-page wizards into one payload so the new UI can
 * render the screens as tabs of a single page.
 *
 * @param checklistId         PK of {@code FREP_*_CHECKLIST} table for the protocol
 * @param protocolType        {@code BIO}, {@code RIP}, {@code WAT}, {@code CHR}
 * @param protocolName        display name, e.g. {@code "Stand Level Retention"}
 * @param frepSelectedSiteId  FK back to {@code FREP_SELECTED_SITE}
 * @param openingNumber       formatted opening for the site header strip
 * @param effectiveYear       master-list year
 * @param statusCode          {@code RDY} (ready) / {@code SUB} (submitted) etc.
 * @param statusLabel         human-readable status
 * @param evaluatorUserid     IDIR of the person who last touched the checklist
 * @param evaluatorName       evaluator's display name when they have FREP access (via FAM), else the userid
 * @param evaluationDate      date the field crew evaluated the site
 * @param sections            ordered tabs to render
 */
public record ProtocolChecklistResponse(
    String checklistId,
    String protocolType,
    String protocolName,
    String frepSelectedSiteId,
    String openingNumber,
    String effectiveYear,
    String statusCode,
    String statusLabel,
    String evaluatorUserid,
    String evaluatorName,
    String evaluationDate,
    List<ProtocolChecklistSection> sections
) {}
