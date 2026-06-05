package ca.bc.gov.nrs.frep.exception;

public class ChrRestException extends RuntimeException {

  private final String type;
  private final String code;

  public ChrRestException(String type, String message) {
    super(message);
    this.type = type;
    this.code = "";
  }

  public ChrRestException(String type, String code, String message) {
    super(message);
    this.type = type;
    this.code = code;
  }

  public String getType() {
    return type;
  }

  public String getCode() {
    return code;
  }
}
