package ca.bc.gov.nrs.frep.dto.frep;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "attribute", "label" })
public class Label {

	@JsonProperty("attribute")
	private String attribute;
	@JsonProperty("label")
	private String label;

	public Label (String attribute, String label) {
		this.attribute = attribute;
		this.label = label;
	}

	public Label () {}

	@JsonProperty("attribute")
	public String getAttribute() {
		return attribute;
	}

	@JsonProperty("attribute")
	public void setAttribute(String attribute) {
		this.attribute = attribute;
	}

	@JsonProperty("label")
	public String getLabel() {
		return label;
	}

	@JsonProperty("label")
	public void setLabel(String label) {
		this.label = label;
	}

	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

}
