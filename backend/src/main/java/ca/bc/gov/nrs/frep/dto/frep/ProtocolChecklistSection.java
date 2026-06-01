package ca.bc.gov.nrs.frep.dto.frep;

import java.util.List;

/**
 * One sub-screen (tab) of a protocol checklist.
 *
 * <p>Maps to the legacy multi-page JSPs:
 * <ul>
 *   <li>{@code FREP_210_BIO_OPENING} / {@code 211_BIOSTRATUM} / {@code 212_BIOPLOT}</li>
 *   <li>{@code FREP_230_STRM_OPEN} … {@code FREP_235_FINAL_CMTS}</li>
 *   <li>{@code FREP_250_WTR_SAMPLE_AREA} … {@code FREP_254_WTR_SUMMARY}</li>
 * </ul>
 *
 * @param id      stable slug (e.g. {@code "opening"}, {@code "stratum"})
 * @param title   tab label (e.g. {@code "Opening info"})
 * @param fields  ordered fields to render inside the tab
 */
public record ProtocolChecklistSection(
    String id,
    String title,
    List<ProtocolChecklistField> fields
) {}
