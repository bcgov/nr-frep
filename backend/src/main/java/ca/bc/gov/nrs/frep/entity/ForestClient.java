package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "V_CLIENT_PUBLIC", schema = "THE")
public class ForestClient implements java.io.Serializable {

	@Id
	@Column(name = "CLIENT_NUMBER")
	private String clientNumber;
	@Column(name = "CLIENT_NAME")
	private String clientName;
	@Column(name = "LEGAL_FIRST_NAME")
	private String legalFirstName;
	@Column(name = "LEGAL_MIDDLE_NAME")
	private String legalMiddleName;
	@Column(name = "CLIENT_STATUS_CODE")
	private String clientStatusCode;
	@Column(name = "CLIENT_TYPE_CODE")
	private String clientTypeCode;
	// Forest_client should come from the public View V_CLIENT_PUBLIC to protect personal data when we don't need it
	// Even if the view is not going to be used here, we don't need those field so we need to remove them.
//	private Date birthdate;
//	private String clientIdTypeCode;
//	private String clientIdentification;
//	private String registryCompanyTypeCode;
//	private String corpRegnNmbr;
//	private String clientAcronym;
//	private String wcbFirmNumber;
//	private String ocgSupplierNmbr;
//	private String clientComment;
//	private Date addTimestamp;
//	private String addUserid;
//	private long addOrgUnit;
//	private Date updateTimestamp;
//	private String updateUserid;
//	private long updateOrgUnit;
//	private Integer revisionCount;

	public ForestClient() {
	}

	public ForestClient(String clientNumber, String clientName, String clientStatusCode, String clientTypeCode) {
		this.clientNumber = clientNumber;
		this.clientName = clientName;
		this.clientStatusCode = clientStatusCode;
		this.clientTypeCode = clientTypeCode;
//		this.addTimestamp = addTimestamp;
//		this.addUserid = addUserid;
//		this.addOrgUnit = addOrgUnit;
//		this.updateTimestamp = updateTimestamp;
//		this.updateUserid = updateUserid;
//		this.updateOrgUnit = updateOrgUnit;
	}

	public ForestClient(String clientNumber, String clientName, String legalFirstName, String legalMiddleName,
			String clientStatusCode, String clientTypeCode) {
		this.clientNumber = clientNumber;
		this.clientName = clientName;
		this.legalFirstName = legalFirstName;
		this.legalMiddleName = legalMiddleName;
		this.clientStatusCode = clientStatusCode;
		this.clientTypeCode = clientTypeCode;
//		this.birthdate = birthdate;
//		this.clientIdTypeCode = clientIdTypeCode;
//		this.clientIdentification = clientIdentification;
//		this.registryCompanyTypeCode = registryCompanyTypeCode;
//		this.corpRegnNmbr = corpRegnNmbr;
//		this.clientAcronym = clientAcronym;
//		this.wcbFirmNumber = wcbFirmNumber;
//		this.ocgSupplierNmbr = ocgSupplierNmbr;
//		this.clientComment = clientComment;
//		this.addTimestamp = addTimestamp;
//		this.addUserid = addUserid;
//		this.addOrgUnit = addOrgUnit;
//		this.updateTimestamp = updateTimestamp;
//		this.updateUserid = updateUserid;
//		this.updateOrgUnit = updateOrgUnit;
//		this.revisionCount = revisionCount;
	}

	public String getClientNumber() {
		return this.clientNumber;
	}

	public void setClientNumber(String clientNumber) {
		this.clientNumber = clientNumber;
	}

	public String getClientName() {
		return this.clientName;
	}

	public void setClientName(String clientName) {
		this.clientName = clientName;
	}

	public String getLegalFirstName() {
		return this.legalFirstName;
	}

	public void setLegalFirstName(String legalFirstName) {
		this.legalFirstName = legalFirstName;
	}

	public String getLegalMiddleName() {
		return this.legalMiddleName;
	}

	public void setLegalMiddleName(String legalMiddleName) {
		this.legalMiddleName = legalMiddleName;
	}

	public String getClientStatusCode() {
		return this.clientStatusCode;
	}

	public void setClientStatusCode(String clientStatusCode) {
		this.clientStatusCode = clientStatusCode;
	}

	public String getClientTypeCode() {
		return this.clientTypeCode;
	}

	public void setClientTypeCode(String clientTypeCode) {
		this.clientTypeCode = clientTypeCode;
	}

}
