package ca.bc.gov.nrs.frep.repository.v1.impl;
import ca.bc.gov.nrs.frep.repository.v1.bean.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Blob;
import org.mockito.ArgumentCaptor;

import ca.bc.gov.nrs.frep.struct.v1.frep.BioCwdRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioPlot;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStandRow;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioStratum;
import ca.bc.gov.nrs.frep.struct.v1.frep.BioWindthrowTreatment;
import ca.bc.gov.nrs.frep.struct.v1.frep.BiodiversityOpening;
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
class ProtocolChecklistWriteRepositoryImplTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private CallableStatement cs;
  @Mock private OracleConnection oracleConnection;
  @Mock private Array windthrowArray;
  @Mock private ca.bc.gov.nrs.frep.service.v1.ObjectStorageService objectStorage;

  private ProtocolChecklistWriteRepositoryImpl repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new ProtocolChecklistWriteRepositoryImpl(jdbcTemplate, objectStorage);
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
        new ca.bc.gov.nrs.frep.struct.v1.frep.RiparianNotes("1001", "a note", "2"), "SLB", "idir");

    verify(connection).prepareCall("{call FREP_CHECKLIST_NOTES.SAVE(?,?,?,?,?,?,?)}");
    verify(cs).setString(3, "SLB"); // resource_value_type drives the internal dispatch
    verify(cs).setString(4, "a note");
    verify(cs).setString(6, "idir"); // update_userid — unique to the SAVE call
  }

  @Test
  void saveBiodiversityOpeningWiresSeventeenParamsAndEchoesIdentity() throws Exception {
    when(cs.getString(16)).thenReturn(null); // no error
    when(cs.getString(1)).thenReturn("1001"); // checklist id echoed
    when(cs.getString(14)).thenReturn("6"); // revision incremented

    BiodiversityOpening in = new BiodiversityOpening(
        "1001", "500", "ACT", "N", "loc", "Y", "N", "Y", "innov", "N", "inv", "W", "opinion",
        "2024-08-12", "5", null, null, null, null, null, null);

    BiodiversityOpening out = repository.saveBiodiversityOpening(in, "idir");

    verify(connection).prepareCall("{call frep_210_bio_opening.SAVE(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
    verify(cs).setString(3, "ACT"); // status
    verify(cs).setString(5, "loc"); // location description
    verify(cs).setString(12, "W"); // site evaluation code
    verify(cs).setString(15, "idir"); // update userid
    verify(cs).registerOutParameter(16, Types.VARCHAR); // error message
    verify(cs).setString(17, "2024-08-12"); // evaluation date (optional trailing param)
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
  void saveStratumWithNoWindthrowRowsBackfillsFromCodeCatalogue() throws Exception {
    when(cs.getString(62)).thenReturn(null); // no error
    when(cs.getString(1)).thenReturn("900");
    when(cs.getString(60)).thenReturn("3");

    BioStratum in = new BioStratum(
        "900", "1001", "MAT", "1", "2024-05-01",
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null,
        "2",
        List.of()); // no windthrow rows

    repository.saveBioStratum(in, "idir");

    // An empty array would make SAVE_STRATUM's FIRST..LAST loop bounds NULL (ORA-06502),
    // so the repo backfills from the windthrow-code catalogue rather than send empty.
    verify(jdbcTemplate).queryForList(anyString(), eq(String.class));
  }

  @Test
  void getStratumComputedReadsNarAndPlotsViaDirectQueries() throws Exception {
    // checklist id (queryForObject String) and resource value id (query) resolution.
    when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any())).thenReturn("1001");
    lenient()
        .when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
        .thenReturn(List.of("500")) // resolveResourceValueId
        .thenReturn(List.of("12.3")); // NAR row
    // plots-completed COUNT(*).
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(2L);

    var out = repository.getStratumComputed("900");

    assertEquals("12.3", out.nar());
    assertEquals("2", out.plotsCompleted());
  }

  @Test
  void getStratumComputedReturnsZeroPlotsAndNoNarWhenNothingFound() throws Exception {
    when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any())).thenReturn("1001");
    lenient()
        .when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
        .thenReturn(List.of("500")) // resolveResourceValueId
        .thenReturn(List.of()); // no NAR row → null, not an exception
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);

    var out = repository.getStratumComputed("900");

    assertEquals("0", out.plotsCompleted()); // COUNT(*) is never null
    assertEquals(null, out.nar());
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
  void saveAttachmentNonBioInsertsViaBlobForUpdateThenWritesContentToOracleBlob() throws Exception {
    // Non-BIO (RIP/WTR) keeps the Oracle BLOB path: SAVE only UPDATEs by id, so a new attachment must
    // first be inserted via GET_BLOB_FOR_UPDATE (which returns the new id @1); SAVE then writes the
    // content onto that id — and object storage is never touched.
    when(cs.getString(1)).thenReturn("777"); // new attachment id from GET_BLOB_FOR_UPDATE
    when(cs.getString(10)).thenReturn(null); // no error on either call
    when(connection.createBlob()).thenReturn(mock(Blob.class));

    repository.saveAttachment("1001", "RIP", "f.pdf", "a note", "application/pdf",
        new byte[] {1, 2, 3}, "idir");

    verify(connection).prepareCall(
        "{call FREP_CHECKLIST_ATTACHMENTS.GET_BLOB_FOR_UPDATE(?,?,?,?,?,?,?,?,?,?)}");
    verify(connection).prepareCall("{call FREP_CHECKLIST_ATTACHMENTS.SAVE(?,?,?,?,?,?,?,?,?,?)}");
    verify(cs).setString(1, "777"); // SAVE targets the id created in step 1
    // file name is set on both the create + the content-write calls.
    verify(cs, org.mockito.Mockito.times(2)).setString(4, "f.pdf");
    verify(objectStorage, never()).putObject(anyString(), anyString(), any());
  }

  @Test
  void saveAttachmentBioWritesBytesToObjectStorageAndEmptyOracleBlob() throws Exception {
    // BIO (SLB): metadata still flows through GET_BLOB_FOR_UPDATE + SAVE, but the real bytes go to
    // object storage under slr/<id> and the Oracle BLOB is left empty.
    when(cs.getString(1)).thenReturn("777");
    when(cs.getString(10)).thenReturn(null);
    when(connection.createBlob()).thenReturn(mock(Blob.class));
    byte[] bytes = {1, 2, 3};

    repository.saveAttachment("1001", "SLB", "f.pdf", "a note", "application/pdf", bytes, "idir");

    verify(connection).prepareCall(
        "{call FREP_CHECKLIST_ATTACHMENTS.GET_BLOB_FOR_UPDATE(?,?,?,?,?,?,?,?,?,?)}");
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(objectStorage).putObject(eq("slr/777"), eq("application/pdf"), captor.capture());
    assertArrayEquals(bytes, captor.getValue()); // the real bytes go to object storage
  }

  @Test
  void saveAttachmentBioCompensatesByRemovingMetadataWhenObjectStorageFails() throws Exception {
    // GET_BLOB_FOR_UPDATE already committed the metadata row; a failed object-storage put must trigger
    // a compensating REMOVE so no orphaned "listed-but-404" attachment is left behind.
    when(cs.getString(1)).thenReturn("777");
    when(cs.getString(10)).thenReturn(null); // GET_BLOB_FOR_UPDATE / SAVE ok
    when(cs.getString(4)).thenReturn(""); // REMOVE error param — no error
    when(connection.createBlob()).thenReturn(mock(Blob.class));
    org.mockito.Mockito.doThrow(new RuntimeException("s3 down"))
        .when(objectStorage).putObject(anyString(), anyString(), any());

    assertThrows(IllegalStateException.class, () ->
        repository.saveAttachment("1001", "SLB", "f.pdf", "a note", "application/pdf",
            new byte[] {1, 2, 3}, "idir"));

    // Compensating REMOVE of the just-created metadata row (the REMOVE proc call uniquely proves it;
    // setString(1,"777") is ambiguous since SAVE also targets that id).
    verify(connection).prepareCall("{call FREP_CHECKLIST_ATTACHMENTS.REMOVE(?,?,?,?)}");
  }

  @Test
  void getAttachmentContentBioServesBytesFromObjectStorageWhenPresent() throws Exception {
    when(cs.getString(10)).thenReturn(null); // GET_BLOB ok
    when(cs.getString(4)).thenReturn("f.pdf"); // file name (metadata)
    when(cs.getString(7)).thenReturn("application/pdf"); // mime (metadata)
    when(objectStorage.objectExists("slr/777")).thenReturn(true);
    byte[] s3Bytes = {9, 8, 7};
    when(objectStorage.getObjectBytes("slr/777")).thenReturn(s3Bytes);

    var content = repository.getAttachmentContent("1001", "SLB", "777");

    assertEquals("f.pdf", content.fileName());
    assertEquals("application/pdf", content.mimeType());
    assertArrayEquals(s3Bytes, content.data());
  }

  @Test
  void getAttachmentContentBioFallsBackToOracleBlobWhenNotYetMigrated() throws Exception {
    when(cs.getString(10)).thenReturn(null);
    when(cs.getString(4)).thenReturn("f.pdf");
    when(cs.getString(7)).thenReturn("application/pdf");
    Blob blob = mock(Blob.class);
    byte[] blobBytes = {5, 5};
    when(blob.length()).thenReturn(2L);
    when(blob.getBytes(1, 2)).thenReturn(blobBytes);
    when(cs.getBlob(8)).thenReturn(blob);
    when(objectStorage.objectExists("slr/777")).thenReturn(false); // pre-migration row

    var content = repository.getAttachmentContent("1001", "SLB", "777");

    assertArrayEquals(blobBytes, content.data()); // served from the Oracle BLOB
    verify(objectStorage, never()).getObjectBytes(anyString());
  }

  @Test
  void deleteAttachmentBioRemovesMetadataRowAndObjectStorageObject() throws Exception {
    when(cs.getString(4)).thenReturn(""); // REMOVE error param — no error

    repository.deleteAttachment("1001", "SLB", "777");

    verify(connection).prepareCall("{call FREP_CHECKLIST_ATTACHMENTS.REMOVE(?,?,?,?)}");
    verify(objectStorage).deleteObject("slr/777");
  }
}
