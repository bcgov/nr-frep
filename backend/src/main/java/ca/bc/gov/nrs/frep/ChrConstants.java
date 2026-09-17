package ca.bc.gov.nrs.frep;

import java.util.Map;

public final class ChrConstants {

  private ChrConstants() {}

  public static final String CHR_PROTOCOL_TYPE = "CHR";

  public static final class FrepChecklistStatusCode {
    public static final String ACT = "ACT";
    public static final String SUB = "SUB";
    public static final String RDO = "RDO";

    private FrepChecklistStatusCode() {}
  }

  public static final class FrepResourceValueStatusCode {
    public static final String TAR = "TAR";
    public static final String REJ = "REJ";
    public static final String ACC = "ACC";

    private FrepResourceValueStatusCode() {}
  }

  public static final class RestMessages {
    public static final String ERROR_AUTHORIZATION = "You are not authorized to perform this action.";
    public static final String ERROR_CHANGE_STATUS =
        "Checklist status does not allow this operation.";
    public static final String SYS_ERROR_REPORT_TO =
        "If this problem persists please contact the FREP help desk.";

    private RestMessages() {}
  }

  public static Map<String, String> frepChecklistStatusDescriptions() {
    return Map.of(
        FrepChecklistStatusCode.ACT, "Active",
        FrepChecklistStatusCode.RDO, "Read Only",
        FrepChecklistStatusCode.SUB, "Submitted"
    );
  }

  public static final class ChrFeatureTypeCode {
    public static final String CTDESIG = "CTDESIG";
    public static final String CTUNDESIG = "CTUNDESIG";
    public static final String BURIALSITE = "BURIALSITE";
    public static final String NEST = "NEST";
    public static final String CERMSITE = "CERMSITE";
    public static final String CREMATSITE = "CREMATSITE";
    public static final String CMT = "CMT";
    public static final String CAVE = "CAVE";
    public static final String DEN = "DEN";
    public static final String TUS = "TUS";
    public static final String CEDARSTRIP = "CEDARSTRIP";
    public static final String ROCKOUTCRP = "ROCKOUTCRP";
    public static final String SPIRSITE = "SPIRSITE";
    public static final String MONCEDAR = "MONCEDAR";
    public static final String CULTDEP = "CULTDEP";
    public static final String LITHICS = "LITHICS";
    public static final String OTH = "OTH";

    private ChrFeatureTypeCode() {}
  }

  public static final class ChrFeatureLocnContextCode {
    public static final String INHARV = "INHARV";
    public static final String ADJBLK = "ADJBLK";
    public static final String ADJWTR = "ADJWTR";
    public static final String OTH = "OTH";
    public static final String CUTBLK = "CUTBLK";
    public static final String RESERV = "RESERV";

    private ChrFeatureLocnContextCode() {}
  }

  public static final class ChrFeatureAgeCode {
    public static final String PRE1846 = "PRE1846";
    public static final String POST1846 = "POST1846";
    public static final String UNK = "UNK";
    public static final String HIST = "HIST";

    private ChrFeatureAgeCode() {}
  }

  public static final class ChrMgmtStrategySourceCode {
    public static final String FN = "FN";
    public static final String AIASAP = "AIASAP";
    public static final String SP = "SP";

    private ChrMgmtStrategySourceCode() {}
  }

  public static final class ChrMgmtStrategyTypeCode {
    public static final String MODBLOCK = "MODBLOCK";
    public static final String BUFFER = "BUFFER";
    public static final String NOBUFF = "NOBUFF";
    public static final String CROWNMOD = "CROWNMOD";
    public static final String ROTATRES = "ROTATRES";
    public static final String TEMPRES = "TEMPRES";
    public static final String DATEFEAT = "DATEFEAT";
    public static final String STUBCMT = "STUBCMT";
    public static final String STUBNONCMT = "STUBNONCMT";
    public static final String LEAVESTAND = "LEAVESTAND";
    public static final String AVOIDPLNT = "AVOIDPLNT";
    public static final String AVOIDSPREP = "AVOIDSPREP";
    public static final String MFZ = "MFZ";
    public static final String HARVSTSAP = "HARVSTSAP";
    public static final String WINTERHARV = "WINTERHARV";
    public static final String ALTERSILV = "ALTERSILV";
    public static final String OTH = "OTH";

    private ChrMgmtStrategyTypeCode() {}
  }

  public static final class ChrFeatureDamageAgentCode {
    public static final String HARV = "HARV";
    public static final String SAFETY = "SAFETY";
    public static final String SIL = "SIL";
    public static final String RECUSE = "RECUSE";
    public static final String FIRE = "FIRE";
    // INDUST, not INDUSTR — legacy Constants.ChrFeatureDamageAgentCode has the extra letter and
    // would fail the same way; the code table is the authority, not that file.
    public static final String INDUSTR = "INDUST";
    public static final String ROADBD = "ROADBD";
    public static final String LVS = "LVS";
    public static final String WINDTHR = "WINDTHR";
    public static final String OTH = "OTH";

    private ChrFeatureDamageAgentCode() {}
  }

  /**
   * Windthrow treatments, as single letters — the values in THE.CHR_WINDTHROW_TREATMENT_CODE.
   *
   * <p>These are the codes, not the names. Spelling them out ("NONE", "FEATHERING") fit the
   * VARCHAR2(10) column, so the insert reached the database and failed its foreign key instead:
   * ORA-02291 on CHFWTX_CHWTC_FK, every time a treatment was ticked. Verified against legacy
   * Constants.ChrWindthrowTreatmentCode.
   */
  public static final class ChrWindthrowTreatmentCode {
    public static final String NONE = "N";
    public static final String BUFFER = "B";
    public static final String PRUNING = "P";
    public static final String FEATHERING = "F";
    public static final String TOPPING = "T";
    public static final String OTHER = "O";

    private ChrWindthrowTreatmentCode() {}
  }
}
