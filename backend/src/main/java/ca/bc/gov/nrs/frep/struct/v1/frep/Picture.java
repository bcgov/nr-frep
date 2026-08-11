package ca.bc.gov.nrs.frep.struct.v1.frep;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "date", "description", "code", "status" })
public class Picture {

  @JsonProperty("id")
  private String id;
  @JsonProperty("date")
  private String date;
  @JsonProperty("description")
  private String description;
  @JsonProperty("code")
  private String code;
  @JsonProperty("mimeTypeCode")
  private String mimeTypeCode;
  /** The feature this photo documents (CHR_CHECKLIST_ATTACHMENT.CHR_FEATURE_ID); null if none. */
  @JsonProperty("featureId")
  private String featureId;
  /** That feature's label, resolved on read so a client can name it without a second lookup. */
  @JsonProperty("featureLabel")
  private String featureLabel;
  @JsonProperty("fileName")
  private String fileName;
  @JsonProperty("checklistId")
  private String checklistId;
  @JsonProperty("status")
  private String status;
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMimeTypeCode() {
    return mimeTypeCode;
  }

  public void setMimeTypeCode(String mimeTypeCode) {
    this.mimeTypeCode = mimeTypeCode;
  }

  public String getFeatureId() {
    return featureId;
  }

  public void setFeatureId(String featureId) {
    this.featureId = featureId;
  }

  public String getFeatureLabel() {
    return featureLabel;
  }

  public void setFeatureLabel(String featureLabel) {
    this.featureLabel = featureLabel;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getChecklistId() {
    return checklistId;
  }

  public void setChecklistId(String checklistId) {
    this.checklistId = checklistId;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    additionalProperties.put(name, value);
  }
}
