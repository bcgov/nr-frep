package ca.bc.gov.nrs.frep.exception;

/**
 * Raised when a legacy PL/SQL package returns an application error message.
 */
public class StoredProcedureException extends RuntimeException {

  private final String packageName;
  private final String procedureName;
  private final String oracleErrorMessage;

  public StoredProcedureException(String packageName, String procedureName, String oracleErrorMessage) {
    super(packageName + "." + procedureName + " failed: " + oracleErrorMessage);
    this.packageName = packageName;
    this.procedureName = procedureName;
    this.oracleErrorMessage = oracleErrorMessage;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getProcedureName() {
    return procedureName;
  }

  public String getOracleErrorMessage() {
    return oracleErrorMessage;
  }
}
