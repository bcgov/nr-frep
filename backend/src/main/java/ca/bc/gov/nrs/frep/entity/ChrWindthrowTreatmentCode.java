package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CHR_WINDTHROW_TREATMENT_CODE", schema = "THE")
public class ChrWindthrowTreatmentCode implements java.io.Serializable {

	@Id
	@Column(name = "CHR_WINDTHROW_TREATMENT_CODE")
	private String chrWindthrowTreatmentCode;
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
	@OneToMany(targetEntity = ChrFeatWindthrTreatXref.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "CHR_WINDTHROW_TREATMENT_CODE", insertable = false, updatable = false)
	private Set chrFeatWindthrTreatXrefs = new HashSet(0);

	public ChrWindthrowTreatmentCode() {
	}

	public ChrWindthrowTreatmentCode(String chrWindthrowTreatmentCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp) {
		this.chrWindthrowTreatmentCode = chrWindthrowTreatmentCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public ChrWindthrowTreatmentCode(String chrWindthrowTreatmentCode, String description, Date effectiveDate,
			Date expiryDate, Date updateTimestamp, Set chrFeatWindthrTreatXrefs) {
		this.chrWindthrowTreatmentCode = chrWindthrowTreatmentCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrFeatWindthrTreatXrefs = chrFeatWindthrTreatXrefs;
	}

	public String getChrWindthrowTreatmentCode() {
		return this.chrWindthrowTreatmentCode;
	}

	public void setChrWindthrowTreatmentCode(String chrWindthrowTreatmentCode) {
		this.chrWindthrowTreatmentCode = chrWindthrowTreatmentCode;
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

	public Set getChrFeatWindthrTreatXrefs() {
		return this.chrFeatWindthrTreatXrefs;
	}

	public void setChrFeatWindthrTreatXrefs(Set chrFeatWindthrTreatXrefs) {
		this.chrFeatWindthrTreatXrefs = chrFeatWindthrTreatXrefs;
	}

}
