package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.List;

/**
 * Generic page envelope for server-side paginated endpoints (mirrors the shape of Spring Data's
 * {@code Page} without coupling the JSON contract to it).
 *
 * @param content       the rows for this page
 * @param totalElements total matching rows across all pages
 * @param totalPages    total page count
 * @param pageNumber    zero-based page index of this page
 * @param pageSize      requested page size
 */
public record PagedResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int pageNumber,
    int pageSize
) {}
