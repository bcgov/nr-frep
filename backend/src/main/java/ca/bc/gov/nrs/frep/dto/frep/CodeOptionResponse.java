package ca.bc.gov.nrs.frep.dto.frep;

/**
 * A generic code-list option for dropdowns: a stored {@code code} and its
 * human-readable {@code description}.
 *
 * <p>Backed by REF-CURSOR procedures in {@code FREP_CODE_LISTS} that select a
 * {@code code} column and a {@code description} column (e.g.
 * {@code get_stream_class_code}, {@code get_checklist_answer_code}).
 *
 * @param code        the stored code value
 * @param description the human-readable label
 */
public record CodeOptionResponse(String code, String description) {}
