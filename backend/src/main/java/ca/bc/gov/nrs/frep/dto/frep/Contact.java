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
@JsonPropertyOrder({ "id", "firstName", "lastName", "roleCode", "organization", "contactedId", "contactedDate", "attendingOnSiteId" })
public class Contact {

	@JsonProperty("id")
	private String id;
	@JsonProperty("firstName")
	private String firstName;
	@JsonProperty("lastName")
	private String lastName;
	@JsonProperty("roleCode")
	private String roleCode;
	@JsonProperty("organization")
	private String organization;
	@JsonProperty("contactedInd")
	private String contactedInd;
	@JsonProperty("contactedDate")
	private String contactedDate;
	@JsonProperty("attendingOnSiteInd")
	private String attendingOnSiteInd;

	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	public Contact() {}

	public Contact(String id, String firstName, String lastName, String roleCode, String organization, String contactedInd, String contactedDate, String attendingOnSiteInd) {
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.roleCode = roleCode;
		this.organization = organization;
		this.contactedInd = contactedInd;
		this.contactedDate = contactedDate;
		this.attendingOnSiteInd = attendingOnSiteInd;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	public String getRoleCode() {
		return roleCode;
	}

	public void setRoleCode(String roleCode) {
		this.roleCode = roleCode;
	}

	public String getOrganization() {
		return organization;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public String getContactedInd() {
		return contactedInd;
	}

	public void setContactedInd(String contactedInd) {
		this.contactedInd = contactedInd;
	}

	public String getContactedDate() {
		return contactedDate;
	}

	public void setContactedDate(String contactedDate) {
		this.contactedDate = contactedDate;
	}

	public String getAttendingOnSiteInd() {
		return attendingOnSiteInd;
	}

	public void setAttendingOnSiteInd(String attendingOnSiteInd) {
		this.attendingOnSiteInd = attendingOnSiteInd;
	}

}
