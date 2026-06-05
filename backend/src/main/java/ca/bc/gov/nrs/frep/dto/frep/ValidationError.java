package ca.bc.gov.nrs.frep.dto.frep;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "type","entityLabel", "checklistID", "featureID", "field", "fieldValue", "message" })
public class ValidationError {

	@JsonProperty("type")
	private String type;

	/**
	 * entityLable is the representing which entity is beeing validated
	 * For a checklist it will be checklistID
	 * for a feature in a checklist it will be checklistID - FeatureID
	 */
	@JsonProperty("entityLabel")
	private String entityLabel;

	@JsonProperty("field")
	private String field;
	@JsonProperty("fieldValue")
	private String fieldValue;
	@JsonProperty("message")
	private String message;
	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();


	public ValidationError (String type, String message) {
		this.type = type;
		this.message = message;
	}

	public ValidationError (String type, String message, String entityLabel) {
		this.type = type;
		this.message = message;
		this.entityLabel = entityLabel;
	}

	public ValidationError (String type, String message, String entityLabel, String field) {
		this.type = type;
		this.message = message;
		this.entityLabel = entityLabel;
		this.field = field;
	}

	public ValidationError (String type, String message, String entityLabel, String field, String fieldValue) {
		this.type = type;
		this.message = message;
		this.entityLabel = entityLabel;
		this.field = field;
		this.fieldValue = fieldValue;
	}

	@JsonProperty("type")
	public String getType() {
		return type;
	}

	@JsonProperty("type")
	public void setType(String type) {
		this.type = type;
	}

	@JsonProperty("field")
	public String getField() {
		return field;
	}

	@JsonProperty("field")
	public void setField(String field) {
		this.field = field;
	}

	@JsonProperty("fieldValue")
	public String getFieldValue() {
		return fieldValue;
	}

	@JsonProperty("fieldValue")
	public void setFieldValue(String fieldValue) {
		this.fieldValue = fieldValue;
	}

	@JsonProperty("message")
	public String getMessage() {
		return message;
	}

	@JsonProperty("message")
	public void setMessage(String mesage) {
		this.message = mesage;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

}
