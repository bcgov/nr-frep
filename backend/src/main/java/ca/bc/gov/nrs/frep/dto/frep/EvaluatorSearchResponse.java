package ca.bc.gov.nrs.frep.dto.frep;

import java.util.List;

/**
 * A page of FREP-editor candidates from the FAM evaluator search ({@code /external/v1/users?role=
 * FREP_EDITOR}), backing the Administration "Add evaluator" search modal.
 *
 * @param users one option per matching IDIR user ({@code code} = IDIR username, {@code description}
 *              = display name)
 * @param total total matching users across all pages (from FAM)
 * @param page  current page number (1-indexed)
 * @param size  page size used for this query
 */
public record EvaluatorSearchResponse(
    List<CodeOptionResponse> users,
    long total,
    int page,
    int size) {}
