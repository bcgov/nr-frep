package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * The single free-text note for a checklist (legacy Notes tab / {@code FREP_CHECKLIST_NOTES}).
 *
 * @param checklistId     the riparian checklist id
 * @param noteDescription the note text
 * @param revisionCount   optimistic-lock token
 */
public record RiparianNotes(String checklistId, String noteDescription, String revisionCount) {}
