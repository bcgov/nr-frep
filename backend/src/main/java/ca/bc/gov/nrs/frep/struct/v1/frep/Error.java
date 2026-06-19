package ca.bc.gov.nrs.frep.struct.v1.frep;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "type", "code", "field", "fieldValue", "message" })
public class Error {

	@JsonProperty("type")
	private String type;
	@JsonProperty("code")
	private String code;
	@JsonProperty("field")
	private String field;
	@JsonProperty("fieldValue")
	private String fieldValue;
	@JsonProperty("message")
	private String message;
	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	public Error (String type, String code, String field, String fieldValue, String message) {
		this.type = type;
		this.code = code;
		this.field = field;
		this.fieldValue = fieldValue;
		this.message = message;
	}

	@JsonProperty("type")
	public String getType() {
		return type;
	}

	@JsonProperty("type")
	public void setType(String type) {
		this.type = type;
	}

	@JsonProperty("code")
	public String getCode() {
		return code;
	}

	@JsonProperty("code")
	public void setCode(String code) {
		this.code = code;
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
