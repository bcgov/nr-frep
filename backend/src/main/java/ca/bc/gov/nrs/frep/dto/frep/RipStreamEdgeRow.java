package ca.bc.gov.nrs.frep.dto.frep;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One riparian stream-edge measurement (mirrors {@code THE.FREP_STRM_EDGE_MEASMNT_OBJECT}).
 * {@code description} is the joined code description (display-only).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RipStreamEdgeRow(
    String measureType,
    String measurement,
    String description,
    String revisionCount
) {}
