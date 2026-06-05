package ca.bc.gov.nrs.frep.dto.frep;

/**
 * The single free-text note for a checklist (legacy Notes tab / {@code FREP_CHECKLIST_NOTES}).
 *
 * @param checklistId     the riparian checklist id
 * @param noteDescription the note text
 * @param revisionCount   optimistic-lock token
 */
public record RiparianNotes(String checklistId, String noteDescription, String revisionCount) {}
