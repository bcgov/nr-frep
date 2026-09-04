package ca.bc.gov.nrs.frep.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.frep.exception.errors.ApiError;
import ca.bc.gov.nrs.frep.service.v1.VirusScanner;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;

class RestExceptionHandlerTest {

  private final RestExceptionHandler handler = new RestExceptionHandler();

  @Test
  void famServiceExceptionMapsToBadGatewayWithMessage() {
    // Upstream FAM failure → 502 (not 500) and the clean message reaches the client.
    ResponseEntity<Object> response =
        handler.handleFamService(new FamServiceException("Evaluator search is unavailable."));

    assertEquals(HttpStatus.BAD_GATEWAY.value(), response.getStatusCode().value());
    ApiError body = (ApiError) response.getBody();
    assertEquals(HttpStatus.BAD_GATEWAY, body.getStatus());
    assertTrue(body.getMessage().contains("Evaluator search is unavailable."));
  }

  @Test
  void virusDetectedExceptionMapsToUnprocessableEntityWithMessage() {
    // A rejected upload → 422 and the user-facing rejection message reaches the client.
    VirusDetectedException ex = new VirusDetectedException(new VirusScanner.Rejection(
        VirusScanner.CODE_VIRUS_DETECTED, "Upload rejected: a virus was detected (Eicar)."));

    ResponseEntity<Object> response = handler.handleVirusDetected(ex);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), response.getStatusCode().value());
    ApiError body = (ApiError) response.getBody();
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, body.getStatus());
    assertTrue(body.getMessage().contains("a virus was detected"));
  }

  @Test
  void recognisedProcMessageKeysBecomeAConflictInPlainEnglish() {
    // What the evaluator-change save actually returns: one key per plot still holding the
    // evaluator. None of it should reach the UI as a raw key, and it is not a system fault.
    StoredProcedureException ex = new StoredProcedureException(
        "frep_checklist_cost_resources", "delete_team_member",
        "frep.evaluatorinfo.delete.evaluator:1,NAR1;"
            + "frep.evaluatorinfo.delete.evaluator:3,NAR1;"
            + "frep.evaluatorinfo.delete.evaluator:2,RES1;");

    ResponseEntity<Object> response = handler.handleStoredProcedure(ex);

    assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
    ApiError body = (ApiError) response.getBody();
    assertEquals(
        "Plots tab: plot 1 in stratum NAR1 is still assigned to this evaluator. "
            + "Plots tab: plot 3 in stratum NAR1 is still assigned to this evaluator. "
            + "Plots tab: plot 2 in stratum RES1 is still assigned to this evaluator.",
        body.getMessage());
    assertFalse(body.getMessage().contains("frep."));
    assertFalse(body.getMessage().contains("help desk"));
  }

  @Test
  void unrecognisedProcMessageIsAGenericFiveHundredWithTheDetailKeptOffScreen() {
    // Fail-safe: an unmapped key must not be swallowed or downgraded to a business rule — it stays a
    // 500. What changed is what the evaluator reads: proc names and message keys are internal
    // vocabulary they can do nothing with, so the raw string is kept for support (debugMessage and
    // the log) and never shown.
    StoredProcedureException ex = new StoredProcedureException(
        "frep_210_bio_opening", "SAVE", "frep.some.unmapped.key:9;");

    ResponseEntity<Object> response = handler.handleStoredProcedure(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
    ApiError body = (ApiError) response.getBody();
    assertEquals(
        "The request could not be completed. If this problem persists please contact the FREP help "
            + "desk.",
        body.getMessage());
    assertFalse(body.getMessage().contains("frep.some.unmapped.key"), body.getMessage());
    assertFalse(body.getMessage().contains("frep_210_bio_opening"), body.getMessage());
    // Support still has all of it.
    assertTrue(body.getDebugMessage().contains("frep.some.unmapped.key:9;"), body.getDebugMessage());
  }

  @Test
  void partiallyRecognisedProcMessageIsNotPartlySwallowed() {
    // All-or-nothing: one unknown segment must not be reported as though only the known half
    // happened. It falls to the 500, and the whole raw string is preserved for support.
    StoredProcedureException ex = new StoredProcedureException(
        "frep_checklist_cost_resources", "delete_team_member",
        "frep.evaluatorinfo.delete.evaluator:1,NAR1;frep.brand.new.key;");

    ResponseEntity<Object> response = handler.handleStoredProcedure(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
    ApiError body = (ApiError) response.getBody();
    assertFalse(body.getMessage().contains("still assigned to this evaluator"), body.getMessage());
    assertTrue(body.getDebugMessage().contains("frep.brand.new.key"), body.getDebugMessage());
  }

  @Test
  void columnOverflowInsideAProcIsA400NotA500() {
    // The legacy packages wrap their inserts in `EXCEPTION WHEN OTHERS`, which swallows the Oracle
    // error and hands it back as p_error_message text — so an ORA-12899 written through a proc never
    // becomes a SQLException and never reaches the generic handlers. SQLERRM is embedded in that
    // text, which is what makes it recognisable here. Before this, the user got a 500 quoting raw
    // Oracle at them.
    StoredProcedureException ex = new StoredProcedureException(
        "FREP_CHECKLIST_ATTACHMENTS", "GET_BLOB_FOR_UPDATE",
        "sil.web.usr.database.unexpected:FREP_Checklist_Attachments,GET_BLOB_FOR_UPDATE,-12899,"
            + "ORA-12899: value too large for column "
            + "\"THE\".\"BIODIVERSITY_CHKLST_ATTACH\".\"MIME_TYPE_CODE\" (actual: 11, maximum: 10);");

    ResponseEntity<Object> response = handler.handleStoredProcedure(ex);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    ApiError body = (ApiError) response.getBody();
    assertTrue(body.getMessage().contains("too long"), body.getMessage());
    assertTrue(body.getMessage().contains("10"), body.getMessage());
    assertTrue(body.getMessage().contains("11"), body.getMessage());
    // Raw Oracle text and the help-desk suffix are both wrong here: the user can fix this themselves.
    assertFalse(body.getMessage().contains("ORA-12899"), body.getMessage());
    assertFalse(body.getMessage().contains("help desk"), body.getMessage());
  }

  @Test
  void aProcRuleIsStillResolvedWhenThereIsNoOverflow() {
    // The overflow check runs first, so prove it does not shadow the business-rule path.
    StoredProcedureException ex = new StoredProcedureException(
        "frep_210_bio_opening", "SAVE", "frep.web.usr.database.record.modified2;");

    ResponseEntity<Object> response = handler.handleStoredProcedure(ex);

    assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
    assertTrue(((ApiError) response.getBody()).getMessage().contains("Someone else changed"));
  }

  @Test
  void commitTimeColumnOverflowNamesTheFieldAndTheLimit() {
    // The real chain for an over-long CHR feature comment: the CHR services flush at commit, so
    // JpaTransactionManager wraps the ORA-12899 in a TransactionSystemException — which is NOT a
    // DataAccessException, so this used to reach the catch-all as "Unexpected system error".
    TransactionSystemException ex = new TransactionSystemException(
        "Could not commit JPA transaction",
        new RuntimeException("could not execute statement",
            new SQLException("ORA-12899: value too large for column "
                + "\"THE\".\"CHR_FEATURE_IDENTITY\".\"COMMENTS\" (actual: 1234, maximum: 500)")));

    ResponseEntity<Object> response = handler.handleTransactionSystem(ex);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    String message = ((ApiError) response.getBody()).getMessage();
    assertTrue(message.startsWith("Comments is too long"), message);
    assertTrue(message.contains("500"));
    assertTrue(message.contains("1234"));
    // The raw Oracle text (and with it the schema/table) must not reach the UI.
    assertFalse(message.contains("ORA-12899"));
    assertFalse(message.contains("CHR_FEATURE_IDENTITY"));
    assertFalse(message.contains("THE"));
  }

  @Test
  void duplicateFeatureLabelNamesTheFieldRatherThanTheConstraint() {
    // The real chain for saving two features with the same label: CHFID_UK is
    // UNIQUE (CHR_CHECKLIST_ID, FEATURE_LABEL), and Hibernate's ConstraintViolationException
    // arrives as a DataIntegrityViolationException. It used to reach the client as
    // "Unexpected system error", which named neither the field nor the problem.
    DataIntegrityViolationException ex = new DataIntegrityViolationException(
        "could not execute statement",
        new SQLException("ORA-00001: unique constraint (THE.CHFID_UK) violated"));

    ResponseEntity<Object> response = handler.handleDataAccess(ex);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    String message = ((ApiError) response.getBody()).getMessage();
    assertTrue(message.startsWith("A feature with this label already exists"), message);
    // The raw Oracle text — and with it the schema and index name — must not reach the UI.
    assertFalse(message.contains("ORA-00001"));
    assertFalse(message.contains("CHFID_UK"));
    assertFalse(message.contains("THE"));
  }

  @Test
  void anUnrecognisedUniqueConstraintStillReadsAsADuplicate() {
    // A constraint this handler has not been taught to name still says what went wrong, rather
    // than falling through to the catch-all.
    TransactionSystemException ex = new TransactionSystemException(
        "Could not commit JPA transaction",
        new SQLException("ORA-00001: unique constraint (THE.SOME_OTHER_UK) violated"));

    ResponseEntity<Object> response = handler.handleTransactionSystem(ex);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    String message = ((ApiError) response.getBody()).getMessage();
    assertTrue(message.startsWith("This record duplicates one that already exists"), message);
    assertFalse(message.contains("SOME_OTHER_UK"));
  }

  @Test
  void abbreviatedColumnNamesGetAReadableLabel() {
    TransactionSystemException ex = new TransactionSystemException(
        "Could not commit JPA transaction",
        new SQLException("ORA-12899: value too large for column "
            + "\"THE\".\"CHR_FEATURE\".\"LIMITING_OPERATNL_FACTORS_DESC\" "
            + "(actual: 2500, maximum: 2000)"));

    String message = ((ApiError) handler.handleTransactionSystem(ex).getBody()).getMessage();

    assertTrue(message.startsWith("Limiting operational factors is too long"), message);
  }

  @Test
  void unmappedColumnNameIsSentenceCased() {
    DataAccessException ex = new DataIntegrityViolationException(
        "insert failed",
        new SQLException("ORA-12899: value too large for column "
            + "\"THE\".\"CHR_FEATURE\".\"DAMAGE_DESCRIPTION\" (actual: 1200, maximum: 1000)"));

    ResponseEntity<Object> response = handler.handleDataAccess(ex);

    assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    assertTrue(((ApiError) response.getBody()).getMessage()
        .startsWith("Damage description is too long"));
  }

  @Test
  void otherCommitFailuresStillReportGenerically() {
    // Neither an overflow nor a duplicate — the two the handler can explain — so it must not be
    // dressed up as either, and must not leak detail. (This used to use ORA-00001, which the
    // handler now recognises and reports as a duplicate.)
    TransactionSystemException ex = new TransactionSystemException(
        "Could not commit JPA transaction",
        new SQLException("ORA-00904: \"THE\".\"CHR_CHECKLIST\".\"NOPE\": invalid identifier"));

    ResponseEntity<Object> response = handler.handleTransactionSystem(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
    String message = ((ApiError) response.getBody()).getMessage();
    assertTrue(message.contains("could not be saved"));
    assertFalse(message.contains("ORA-00904"));
  }

  @Test
  void optimisticLockKeyResolvesWithoutArguments() {
    StoredProcedureException ex = new StoredProcedureException(
        "frep_210_bio_opening", "SAVE", "frep.web.usr.database.record.modified2");

    ResponseEntity<Object> response = handler.handleStoredProcedure(ex);

    assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
    assertEquals(
        "Someone else changed this data. Reload the checklist and try again.",
        ((ApiError) response.getBody()).getMessage());
  }
}
