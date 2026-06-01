package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_FEATURE_DAMAGE_AGENT_CODE", schema = "THE")
public class ChrFeatureDamageAgentCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_FEATURE_DAMAGE_AGENT_CODE")
	private String chrFeatureDamageAgentCode;
	@Column(name = "DESCRIPTION")
	private String description;
	@Column(name = "EFFECTIVE_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date effectiveDate;
	@Column(name = "EXPIRY_DATE")
	@Temporal(TemporalType.TIMESTAMP)
	private Date expiryDate;
	@Column(name = "UPDATE_TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date updateTimestamp;
	@OneToMany(targetEntity = ChrFeatureDamageAgentXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_FEATURE_DAMAGE_AGENT_CODE", insertable = false, updatable = false)
	private Set chrFeatureDamageAgentXrefs = new HashSet(0);

	public ChrFeatureDamageAgentCode() {
	}

	public ChrFeatureDamageAgentCode(String chrFeatureDamageAgentCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.chrFeatureDamageAgentCode = chrFeatureDamageAgentCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrFeatureDamageAgentCode(String chrFeatureDamageAgentCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set chrFeatureDamageAgentXrefs) {
		this.chrFeatureDamageAgentCode = chrFeatureDamageAgentCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrFeatureDamageAgentXrefs = chrFeatureDamageAgentXrefs;
	}

	public String getChrFeatureDamageAgentCode() {
		return this.chrFeatureDamageAgentCode;
	}

	public void setChrFeatureDamageAgentCode(String chrFeatureDamageAgentCode) {
		this.chrFeatureDamageAgentCode = chrFeatureDamageAgentCode;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Date getEffectiveDate() {
		return this.effectiveDate;
	}

	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public Date getExpiryDate() {
		return this.expiryDate;
	}

	public void setExpiryDate(Date expiryDate) {
		this.expiryDate = expiryDate;
	}

	public Date getUpdateTimestamp() {
		return this.updateTimestamp;
	}

	public void setUpdateTimestamp(Date updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	public Set getChrFeatureDamageAgentXrefs() {
		return this.chrFeatureDamageAgentXrefs;
	}

	public void setChrFeatureDamageAgentXrefs(Set chrFeatureDamageAgentXrefs) {
		this.chrFeatureDamageAgentXrefs = chrFeatureDamageAgentXrefs;
	}

}
