package ca.bc.gov.nrs.frep.repository.frep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.dto.frep.BioCwdRow;
import ca.bc.gov.nrs.frep.dto.frep.BioPlot;
import ca.bc.gov.nrs.frep.dto.frep.BioStandRow;
import ca.bc.gov.nrs.frep.dto.frep.BioStratum;
import ca.bc.gov.nrs.frep.dto.frep.BioWindthrowTreatment;
import ca.bc.gov.nrs.frep.dto.frep.BiodiversityOpening;
import ca.bc.gov.nrs.frep.dto.frep.RipContinuousIndRow;
import ca.bc.gov.nrs.frep.dto.frep.RipNoAnswerRow;
import ca.bc.gov.nrs.frep.dto.frep.RipOpenSpecImpactRow;
import ca.bc.gov.nrs.frep.dto.frep.RipOtherIndRow;
import ca.bc.gov.nrs.frep.dto.frep.RipOtherSpecImpactRow;
import ca.bc.gov.nrs.frep.dto.frep.RipPointIndRow;
import ca.bc.gov.nrs.frep.dto.frep.RipQuestionRow;
import ca.bc.gov.nrs.frep.dto.frep.RipStreamEdgeRow;
import ca.bc.gov.nrs.frep.dto.frep.RiparianFieldData;
import ca.bc.gov.nrs.frep.dto.frep.RiparianFinalComments;
import ca.bc.gov.nrs.frep.dto.frep.RiparianOtherIndicators;
import ca.bc.gov.nrs.frep.dto.frep.RiparianQuestions;
import ca.bc.gov.nrs.frep.dto.frep.RiparianSpecificImpacts;
import ca.bc.gov.nrs.frep.dto.frep.RiparianStreamOpening;
import ca.bc.gov.nrs.frep.dto.frep.WaterAssessment;
import ca.bc.gov.nrs.frep.dto.frep.WaterRange;
import ca.bc.gov.nrs.frep.dto.frep.WaterSampleArea;
import ca.bc.gov.nrs.frep.dto.frep.WaterSampleSite;
import ca.bc.gov.nrs.frep.dto.frep.WtrAccessRoadRow;
import ca.bc.gov.nrs.frep.dto.frep.WtrAssessmentRow;
import ca.bc.gov.nrs.frep.dto.frep.WtrDisturbanceRow;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.util.List;
import oracle.jdbc.OracleConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the proc name + positional parameter wiring for the protocol write procedures. */
@ExtendWith(MockitoExtension.class)
class ProtocolChecklistWriteRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private CallableStatement cs;
  @Mock private OracleConnection oracleConnection;
  @Mock private Array windthrowArray;

  private ProtocolChecklistWriteRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new ProtocolChecklistWriteRepository(jdbcTemplate);
    lenient().when(connection.prepareCall(anyString())).thenReturn(cs);
    // Run the ConnectionCallback against the mocked connection/statement.
    lenient().when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
      ConnectionCallback<?> callback = inv.getArgument(0);
      return callback.doInConnection(connection);
    });
    // For the windthrow VARRAY build inside SAVE_STRATUM.
    lenient().when(cs.getConnection()).thenReturn(connection);
    lenient().when(connection.unwrap(OracleConnection.class)).thenReturn(oracleConnection);
    lenient().when(oracleConnection.createOracleArray(anyString(), any())).thenReturn(windthrowArray);
  }

  @Test
  void submitCallsSubmissionValidationWithResourceType() throws Exception {
    when(cs.getString(4)).thenReturn("");

    String error = repository.submit("SLB", "1001", "idir");

    verify(connection).prepareCall("{call FREP_TOMBSTONE.FREP_SUBMISSION_VALIDATION(?,?,?,?)}");
    verify(cs).setString(1, "SLB");
    verify(cs).setString(2, "1001");
    verify(cs).setString(3, "idir");
    verify(cs).registerOutParameter(4, Types.VARCHAR);
    assertEquals("", error);
  }

  @Test
  void unsubmitCallsUnsubmitProc() throws Exception {
    when(cs.getString(4)).thenReturn("");

    repository.unsubmit("RIP", "2002", "idir");

    verify(connection).prepareCall("{call FREP_TOMBSTONE.UNSUBMIT(?,?,?,?)}");
    verify(cs).setString(1, "RIP");
    verify(cs).setString(2, "2002");
  }

  @Test
  void saveNotesCallsPublicSaveDispatcherWithResourceType() throws Exception {
    // resolveResourceValueId() lookup (used by save + the round-trip read).
    lenient()
        .when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
        .thenReturn(List.of("500"));

    // The private save_<protocol>_notes procs aren't callable directly (PLS-00302) — only the public
    // SAVE dispatcher, which routes on the resource_value_type at param 3.
    repository.saveNotes(
        new ca.bc.gov.nrs.frep.dto.frep.RiparianNotes("1001", "a note", "2"), "SLB", "idir");

    verify(connection).prepareCall("{call FREP_CHECKLIST_NOTES.SAVE(?,?,?,?,?,?,?)}");
    verify(cs).setString(3, "SLB"); // resource_value_type drives the internal dispatch
    verify(cs).setString(4, "a note");
    verify(cs).setString(6, "idir"); // update_userid — unique to the SAVE call
  }

  @Test
  void saveBiodiversityOpeningWiresSixteenParamsAndEchoesIdentity() throws Exception {
    when(cs.getString(16)).thenReturn(null); // no error
    when(cs.getString(1)).thenReturn("1001"); // checklist id echoed
    when(cs.getString(14)).thenReturn("6"); // revision incremented

    BiodiversityOpening in = new BiodiversityOpening(
        "1001", "500", "ACT", "N", "loc", "Y", "N", "Y", "innov", "N", "inv", "W", "opinion", "5");

    BiodiversityOpening out = repository.saveBiodiversityOpening(in, "idir");

    verify(connection).prepareCall("{call frep_210_bio_opening.SAVE(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
    verify(cs).setString(3, "ACT"); // status
    verify(cs).setString(5, "loc"); // location description
    verify(cs).setString(12, "W"); // site evaluation code
    verify(cs).setString(15, "idir"); // update userid
    verify(cs).registerOutParameter(16, Types.VARCHAR); // error message
    assertEquals("1001", out.checklistId());
    assertEquals("6", out.revisionCount());
  }

  @Test
  void saveStratumWiresParamsBuildsWindthrowArrayAndEchoesIdentity() throws Exception {
    when(cs.getString(62)).thenReturn(null); // no error
    when(cs.getString(1)).thenReturn("900"); // stratum id echoed
    when(cs.getString(60)).thenReturn("3"); // revision incremented

    BioStratum in = new BioStratum(
        "900", "1001", "MAT", "1", "2024-05-01", // 1-5
        null, null, null, null, null, null, null, null, null, null, // 6-15
        null, null, null, null, null, null, null, null, null, null, // 16-25
        null, null, null, null, null, null, null, null, null, null, // 26-35
        null, null, null, null, null, null, null, null, null, null, // 36-45
        null, null, null, null, null, null, null, null, null, null, // 46-55
        null, null, null, // 56-58
        "2", // 59 revisionCount
        List.of(new BioWindthrowTreatment(null, "PRU", "Y"))); // 60

    BioStratum out = repository.saveBioStratum(in, "idir");

    verify(connection).prepareCall(
        "{call FREP_211_BIOSTRATUM.SAVE_STRATUM(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
    verify(cs).setString(3, "SLB"); // resource value type
    verify(cs).setString(4, "MAT"); // strata type code
    verify(cs).setString(5, "1"); // stratum number
    verify(cs).setString(6, "2024-05-01"); // summary date
    verify(cs).setString(61, "idir"); // update userid
    verify(cs).registerOutParameter(62, Types.VARCHAR); // error message
    verify(cs).setObject(63, windthrowArray); // windthrow VARRAY
    verify(cs).registerOutParameter(63, Types.ARRAY, "THE.FREP_WINDTHROW_TREAT_VARRAY");
    assertEquals("900", out.stratumId());
    assertEquals("3", out.revisionCount());
  }

  @Test
  void deleteStratumCallsDeleteProc() throws Exception {
    when(cs.getString(3)).thenReturn("");

    repository.deleteBioStratum("900", "2");

    verify(connection).prepareCall("{call FREP_211_BIOSTRATUM.DELETE_STRATUM(?,?,?)}");
    verify(cs).setString(1, "900");
    verify(cs).setString(2, "2");
    verify(cs).registerOutParameter(3, Types.VARCHAR);
  }

  @Test
  void saveBioPlotWiresSavePlotAndBuildsStandAndCwdArrays() throws Exception {
    when(cs.getString(20)).thenReturn(null); // save_plot: no error
    when(cs.getString(1)).thenReturn("500"); // plot id echoed
    when(cs.getString(2)).thenReturn("900"); // stratum id echoed
    when(cs.getString(18)).thenReturn("3"); // revision incremented
    when(cs.getString(3)).thenReturn(null); // stand/cwd detail: no error

    BioPlot in = new BioPlot(
        "500", "900", "1", "jdoe", "S", "9", "100", "200", "Y", "1", "2", "3", "N", "a", "b",
        "cmt", "2",
        List.of(new BioStandRow(null, null, "FD", null, "1", "30", "20", "ok", "1", null, "1",
            null, null)),
        List.of(new BioCwdRow(null, null, "FD", null, "1", "15", "5", "2", null, "log", "1", null,
            null)));

    repository.saveBioPlot(in, "idir");

    verify(connection).prepareCall(
        "{call FREP_212_BIOPLOT.save_plot(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
    verify(connection).prepareCall("{call FREP_212_BIOPLOT.save_bio_stand_detail(?,?,?)}");
    verify(connection).prepareCall("{call FREP_212_BIOPLOT.save_cwd_detail(?,?,?)}");
    verify(cs).setString(3, "SLB"); // resource value type
    verify(cs).setString(4, "1"); // plot number
    verify(cs).setString(17, "cmt"); // plot comment
    verify(cs).registerOutParameter(20, Types.VARCHAR); // save_plot error
    verify(oracleConnection).createStruct(eq("THE.FREP_STAND_TABLE_OBJECT"), any());
    verify(oracleConnection).createStruct(eq("THE.FREP_CWD_TABLE_OBJECT"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_STAND_TABLE_VARRAY"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_CWD_TABLE_VARRAY"), any());
  }

  @Test
  void deletePlotCallsDeleteProc() throws Exception {
    when(cs.getString(3)).thenReturn("");

    repository.deleteBioPlot("500", "2");

    verify(connection).prepareCall("{call FREP_212_BIOPLOT.delete_plot(?,?,?)}");
    verify(cs).setString(2, "2");
    verify(cs).registerOutParameter(3, Types.VARCHAR);
  }

  @Test
  void saveRipStreamOpeningWires48ParamsAndStreamEdgeArray() throws Exception {
    when(cs.getString(48)).thenReturn(null); // no error
    when(cs.getString(1)).thenReturn("2002"); // checklist id echoed
    when(cs.getString(46)).thenReturn("4"); // revision incremented

    RiparianStreamOpening in = new RiparianStreamOpening(
        "2002", "S1", null, null, null, null, null, null, // 1-8 (checklistId, sampleNumber, ...)
        null, null, null, null, null, null, null, null, null, null, null, null, null, // 9-21 scalar
        null, null, null, null, null, null, // 22-27 RMA
        null, null, null, null, null, null, null, null, // 28-35 RRZ
        null, null, null, null, null, null, // 36-41 RMZ
        null, // 42 plnRiparianStrNaInd
        null, // 43 invasivePlantIndicator
        "cmt", // 44 invasivePlantComment
        "3", // 45 revisionCount
        List.of(new RipStreamEdgeRow("LFT", "2.5", null, "1")));

    repository.saveRipStreamOpening(in, "idir");

    verify(connection).prepareCall(
        "{call FREP_230_STRM_OPEN.SAVE(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
    verify(cs).setString(2, "S1"); // sample number
    verify(cs).setString(45, "cmt"); // invasive plant comment
    verify(cs).setString(47, "idir"); // update userid
    verify(cs).registerOutParameter(48, Types.VARCHAR); // error message
    verify(cs).registerOutParameter(9, Types.ARRAY, "THE.FREP_STRM_EDGE_MEASMNT_VARRAY");
    verify(oracleConnection).createStruct(eq("THE.FREP_STRM_EDGE_MEASMNT_OBJECT"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_STRM_EDGE_MEASMNT_VARRAY"), any());
  }

  @Test
  void saveRipFinalCommentsWiresTenParamsAndEchoesIdentity() throws Exception {
    when(cs.getString(10)).thenReturn(null);
    when(cs.getString(1)).thenReturn("2002");
    when(cs.getString(8)).thenReturn("4");

    RiparianFinalComments in = new RiparianFinalComments(
        "2002", "conc", "spec", "prob", "map", "leave", "recomm", "3");

    RiparianFinalComments out = repository.saveRipFinalComments(in, "idir");

    verify(connection).prepareCall("{call FREP_235_FINAL_CMTS.save(?,?,?,?,?,?,?,?,?,?)}");
    verify(cs).setString(2, "conc");
    verify(cs).setString(7, "recomm");
    verify(cs).setString(9, "idir");
    verify(cs).registerOutParameter(10, Types.VARCHAR);
    assertEquals("2002", out.checklistId());
    assertEquals("4", out.revisionCount());
  }

  @Test
  void saveRipFieldDataWiresProcAndBothIndicatorArrays() throws Exception {
    when(cs.getString(6)).thenReturn(null);

    RiparianFieldData in = new RiparianFieldData("2002", "N",
        List.of(new RipPointIndRow("1", "Q1", "PT", "T1", "1", "2", "3", "4", "5", "6", "th", "3.5",
            "1")),
        List.of(new RipContinuousIndRow("9", "Q2", "CT", "q", "10", "c", "th", "1")));

    repository.saveRipFieldData(in, "idir");

    verify(connection).prepareCall("{call FREP_231_FIELD_DATA.SAVE(?,?,?,?,?,?)}");
    verify(cs).setString(2, "N");
    verify(cs).setString(5, "idir");
    verify(oracleConnection).createOracleArray(eq("THE.FREP_POINT_INDICATOR_VARRAY"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_CONTINUOUS_IND_VARRAY"), any());
  }

  @Test
  void saveRipOtherIndicatorsWiresProcAndArray() throws Exception {
    when(cs.getString(4)).thenReturn(null);

    RiparianOtherIndicators in = new RiparianOtherIndicators("2002",
        List.of(new RipOtherIndRow("T1", "S1", "N", "q", "5", "Y", "1", null, null)));

    repository.saveRipOtherIndicators(in, "idir");

    verify(connection).prepareCall("{call FREP_232_OTHER_INDS.save(?,?,?,?)}");
    verify(cs).setString(3, "idir");
    verify(oracleConnection).createOracleArray(eq("THE.FREP_OTHER_INDICATOR_VARRAY"), any());
  }

  @Test
  void saveRipQuestionsCallsBothProcsAndArrays() throws Exception {
    when(cs.getString(4)).thenReturn(null);

    RiparianQuestions in = new RiparianQuestions("2002",
        List.of(new RipQuestionRow("2002", "10", "Q1", null, null, null, null, null, null, null,
            "YES", "1", null, null)),
        List.of(new RipNoAnswerRow("3", "2002", "10", "Q1", "TYPE", null, "1", "Y", "1", null,
            null)));

    repository.saveRipQuestions(in, "idir");

    verify(connection).prepareCall("{call FREP_233_QUESTIONS.save_responses(?,?,?,?)}");
    verify(connection).prepareCall("{call FREP_233_QUESTIONS.save_no_answers(?,?,?,?)}");
    verify(oracleConnection).createOracleArray(eq("THE.FREP_QUESTIONS_VARRAY"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_NO_ANSWERS_VARRAY"), any());
  }

  @Test
  void saveRipSpecificImpactsWiresProcAndBothArrays() throws Exception {
    when(cs.getString(3)).thenReturn(null);

    RiparianSpecificImpacts in = new RiparianSpecificImpacts("2002",
        List.of(new RipOpenSpecImpactRow("1", "TYPE", "Y", "1")),
        List.of(new RipOtherSpecImpactRow("2", "desc", "N", "1")));

    repository.saveRipSpecificImpacts(in, "idir");

    verify(connection).prepareCall(
        "{call FREP_234_SPECIFIC_IMPACTS.SAVE(?,?,?,?,?)}");
    verify(cs).registerOutParameter(3, Types.VARCHAR);
    verify(oracleConnection).createOracleArray(eq("THE.FREP_OPEN_SPEC_IMPACT_VARRAY"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_OTHER_SPEC_IMPACT_VARRAY"), any());
  }

  @Test
  void saveWaterSampleAreaWiresObjectAndChildArrays() throws Exception {
    WaterSampleArea in = new WaterSampleArea(
        "900", null, null, null, null, null, null, null, null, null, // 0-9
        null, null, null, null, null, null, null, null, null, null, // 10-19
        null, null, null, null, null, null, null, null, null, null, // 20-29
        null, null, null, null, null, "2", null, null, // 30-37 (35 = revisionCount)
        List.of(new WtrDisturbanceRow(null, "900", "D1", "A1", "2", "1", null, null)),
        List.of(new WtrAccessRoadRow(null, "900", "R1", "desc", "S1", "5", "3", "1", null, null)));

    repository.saveWaterSampleArea(in, "idir");

    verify(connection).prepareCall("{call FREP_250_WATER_CHKLST_SAVE(?,?,?)}");
    verify(oracleConnection).createStruct(eq("THE.FREP_WTR_CHKLST_OBJECT"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_WTR_ACCESS_ROAD_VARRAY"), any());
    verify(oracleConnection).createOracleArray(eq("THE.FREP_WTR_DISTURBANCE_VARRAY"), any());
  }

  @Test
  void saveWaterSampleSiteWiresObject() throws Exception {
    WaterSampleSite in = new WaterSampleSite(
        "500", "900", null, "T", null, null, null, "1", null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, "2", null, null);

    repository.saveWaterSampleSite(in, "idir");

    verify(connection).prepareCall("{call FREP_251_SAMPLE_SITE_SAVE(?)}");
    verify(oracleConnection).createStruct(eq("THE.FREP_WTR_SAMPLE_SITE_OBJECT"), any());
  }

  @Test
  void saveWaterAssessmentWiresProcAndBothArrays() throws Exception {
    WaterAssessment in = new WaterAssessment("500",
        List.of(new WtrAssessmentRow("500", "G", "d", "1", "C1", "cd", "Y", "1", null, null)),
        List.of(new WtrAssessmentRow("500", "G", "d", "1", "S1", "sd", "N", "1", null, null)));

    repository.saveWaterAssessment(in, "idir");

    verify(connection).prepareCall("{call FREP_WATER_ASSESSMENT.save(?,?,?)}");
    verify(cs, org.mockito.Mockito.atLeastOnce()).setString(1, "500");
    verify(oracleConnection, org.mockito.Mockito.atLeast(2))
        .createOracleArray(eq("THE.FREP_WTR_ASSESSMENT_VW_VARRAY"), any());
  }

  @Test
  void saveWaterRangeWiresProcAndArray() throws Exception {
    WaterRange in = new WaterRange("500",
        List.of(new WtrAssessmentRow("500", "G", "d", "1", "R1", "rd", "Y", "1", null, null)));

    repository.saveWaterRange(in, "idir");

    verify(connection).prepareCall("{call FREP_WATER_RANGE.save(?,?)}");
    verify(cs, org.mockito.Mockito.atLeastOnce()).setString(1, "500");
    verify(oracleConnection, org.mockito.Mockito.atLeastOnce())
        .createOracleArray(eq("THE.FREP_WTR_ASSESSMENT_VW_VARRAY"), any());
  }
}
