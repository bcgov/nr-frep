package ca.bc.gov.nrs.frep.service;

import ca.bc.gov.nrs.frep.dto.ProtocolChecklistField;
import ca.bc.gov.nrs.frep.dto.ProtocolChecklistResponse;
import ca.bc.gov.nrs.frep.dto.ProtocolChecklistSection;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * In-memory protocol-checklist data for local dev.
 *
 * <p>The legacy app spreads each protocol across several screens; this stub
 * keeps that structure by returning a list of {@link ProtocolChecklistSection}s,
 * one per legacy sub-screen.
 *
 * <p>Replaced in Phase 0.3 by JDBC calls to {@code FREP_BIODIVERSITY_*},
 * {@code FREP_RIPARIAN_*}, {@code FREP_WATER_*}.
 */
@Service
public class StubProtocolChecklistService implements ProtocolChecklistService {

  private static ProtocolChecklistField text(String label, String value) {
    return new ProtocolChecklistField(label, value, "TEXT");
  }

  private static ProtocolChecklistField num(String label, String value) {
    return new ProtocolChecklistField(label, value, "NUMBER");
  }

  private static ProtocolChecklistField yesNo(String label, String value) {
    return new ProtocolChecklistField(label, value, "YES_NO");
  }

  private static ProtocolChecklistField date(String label, String value) {
    return new ProtocolChecklistField(label, value, "DATE");
  }

  private static ProtocolChecklistField multiline(String label, String value) {
    return new ProtocolChecklistField(label, value, "MULTILINE");
  }

  private static final ProtocolChecklistResponse BIO_9001 = new ProtocolChecklistResponse(
      "9001", "BIO", "Biodiversity", "1001", "A12345", "2024",
      "RDY", "Ready", "IDIR\\JDOE", "2024-08-12",
      List.of(
          new ProtocolChecklistSection("opening", "Opening info (FREP210)", List.of(
              text("Stand Description", "Mixed conifer, mature"),
              num("Stand age (yrs)", "82"),
              num("Site index", "21"),
              yesNo("Cut block harvested?", "Y"),
              date("Harvest complete date", "2024-06-15")
          )),
          new ProtocolChecklistSection("stratum", "Stratum summary (FREP211)", List.of(
              num("# strata identified", "3"),
              text("Dominant stratum", "Cedar–Hemlock"),
              num("Total stratum area (ha)", "22.1"),
              multiline("Comments",
                  "Stratum boundaries verified against air photo prior to plot layout.")
          )),
          new ProtocolChecklistSection("plots", "Plots (FREP212)", List.of(
              num("# plots established", "12"),
              num("Avg trees / plot", "8.4"),
              num("Avg basal area (m²/ha)", "34.2"),
              yesNo("All plots photographed?", "Y")
          ))
      )
  );

  private static final ProtocolChecklistResponse RIP_9003 = new ProtocolChecklistResponse(
      "9003", "RIP", "Riparian", "1002", "B67890", "2024",
      "SUB", "Submitted", "IDIR\\ASMITH", "2024-07-22",
      List.of(
          new ProtocolChecklistSection("stream", "Stream / opening (FREP230)", List.of(
              text("Stream class", "S4"),
              num("Stream length sampled (m)", "215"),
              yesNo("Adjacent to opening?", "Y")
          )),
          new ProtocolChecklistSection("field-data", "Field data (FREP231)", List.of(
              num("# bankfull cross-sections", "10"),
              num("Avg bankfull width (m)", "3.4"),
              num("Avg wetted width (m)", "1.8")
          )),
          new ProtocolChecklistSection("other-inds", "Other indicators (FREP232)", List.of(
              yesNo("Logging debris in channel?", "N"),
              yesNo("Bank disturbance present?", "N")
          )),
          new ProtocolChecklistSection("questions", "Questions (FREP233)", List.of(
              yesNo("Sediment delivered to stream?", "N"),
              yesNo("Riparian function maintained?", "Y")
          )),
          new ProtocolChecklistSection("specific-impacts", "Specific impacts (FREP234)", List.of(
              text("Most significant impact", "None observed"),
              text("Cause", "—")
          )),
          new ProtocolChecklistSection("final-cmts", "Final comments (FREP235)", List.of(
              multiline("Field crew comments",
                  "Site is in good post-harvest condition; recommend keep as control."),
              multiline("QA/QC notes", "Photos taken at 0, 50, 100, 200m.")
          ))
      )
  );

  private static final ProtocolChecklistResponse WAT_9002 = new ProtocolChecklistResponse(
      "9002", "WAT", "Water Quality", "1001", "A12345", "2024",
      "RDY", "Ready", "IDIR\\JDOE", "2024-09-04",
      List.of(
          new ProtocolChecklistSection("sample-area", "Sample area (FREP250)", List.of(
              text("Watershed", "Chilliwack River"),
              num("Sample area (ha)", "120.5")
          )),
          new ProtocolChecklistSection("site-control", "Site control / details (FREP251)", List.of(
              text("Control type", "Upstream paired"),
              num("# sample sites", "4")
          )),
          new ProtocolChecklistSection("assessment", "Assessment (FREP252)", List.of(
              num("Turbidity (NTU)", "2.1"),
              num("Suspended sediment (mg/L)", "5.0"),
              yesNo("Within guideline?", "Y")
          )),
          new ProtocolChecklistSection("range", "Range (FREP253)", List.of(
              text("Cattle access", "None observed"),
              yesNo("Access road crossing assessed?", "Y")
          )),
          new ProtocolChecklistSection("summary", "Summary (FREP254)", List.of(
              multiline("Overall summary",
                  "Water quality at this site is within Provincial guidelines; "
                      + "no actionable impacts noted.")
          ))
      )
  );

  @Override
  public Optional<ProtocolChecklistResponse> findChecklist(String protocolType, String checklistId) {
    if (StringUtils.isBlank(protocolType) || StringUtils.isBlank(checklistId)) {
      return Optional.empty();
    }
    String type = protocolType.toUpperCase();
    return switch (type) {
      case "BIO" -> "9001".equals(checklistId) ? Optional.of(BIO_9001) : Optional.empty();
      case "RIP" -> "9003".equals(checklistId) ? Optional.of(RIP_9003) : Optional.empty();
      case "WAT" -> "9002".equals(checklistId) ? Optional.of(WAT_9002) : Optional.empty();
      default -> Optional.empty();
    };
  }
}
