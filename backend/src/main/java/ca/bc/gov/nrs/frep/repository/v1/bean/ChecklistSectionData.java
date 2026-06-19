package ca.bc.gov.nrs.frep.repository.v1.bean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scalar and flattened collection fields for one legacy checklist screen.
 */
public record ChecklistSectionData(
    ChecklistHeaderData header,
    List<Map.Entry<String, String>> fields
) {

  public static ChecklistSectionData of(ChecklistHeaderData header, Map<String, String> fields) {
    return new ChecklistSectionData(header, List.copyOf(fields.entrySet()));
  }

  public static ChecklistSectionData fieldsOnly(Map<String, String> fields) {
    return of(ChecklistHeaderData.empty(), fields);
  }

  public static Map<String, String> linkedFields() {
    return new LinkedHashMap<>();
  }

  /** An empty section — used for tabs whose content is loaded by a dedicated inline editor. */
  public static ChecklistSectionData emptySection() {
    return fieldsOnly(Map.of());
  }
}
