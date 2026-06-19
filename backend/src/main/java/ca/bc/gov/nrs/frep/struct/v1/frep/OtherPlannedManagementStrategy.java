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
@JsonPropertyOrder({ "otherStrategy", "fnInd", "aiaInd", "spInd" })
public class OtherPlannedManagementStrategy {

	@JsonProperty("otherStrategy")
	private String otherStrategy;
	@JsonProperty("fnInd")
	private String fnInd;
	@JsonProperty("aiaInd")
	private String aiaInd;
	@JsonProperty("spInd")
	private String spInd;

	public OtherPlannedManagementStrategy (String otherStrategy, String fnInd, String aiaInd, String spInd) {
		this.otherStrategy = otherStrategy;
		this.fnInd = fnInd;
		this.aiaInd = aiaInd;
		this.spInd = spInd;
	}

	public OtherPlannedManagementStrategy () {}

	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	@JsonProperty("otherStrategy")
	public String getOtherStrategy() {
		return otherStrategy;
	}

	@JsonProperty("otherStrategy")
	public void setOtherStrategy(String otherStrategy) {
		this.otherStrategy = otherStrategy;
	}

	@JsonProperty("fnInd")
	public String getFnInd() {
		return fnInd;
	}

	@JsonProperty("fnInd")
	public void setFnInd(String fnInd) {
		this.fnInd = fnInd;
	}

	@JsonProperty("aiaInd")
	public String getAiaInd() {
		return aiaInd;
	}

	@JsonProperty("aiaInd")
	public void setAiaInd(String aiaInd) {
		this.aiaInd = aiaInd;
	}

	@JsonProperty("spInd")
	public String getSpInd() {
		return spInd;
	}

	@JsonProperty("spInd")
	public void setSpInd(String spInd) {
		this.spInd = spInd;
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
