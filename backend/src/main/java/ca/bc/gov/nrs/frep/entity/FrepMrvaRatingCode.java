package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "FREP_MRVA_RATING_CODE", schema = "THE")
public class FrepMrvaRatingCode implements java.io.Serializable {

	@Id
	@Column(name = "FREP_MRVA_RATING_CODE")
	private String frepMrvaRatingCode;
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
	@OneToMany(targetEntity = ChrChecklist.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "FREP_MRVA_RATING_CODE", insertable = false, updatable = false)
	private Set chrChecklists = new HashSet(0);

	public FrepMrvaRatingCode() {
	}

	public FrepMrvaRatingCode(String frepMrvaRatingCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp) {
		this.frepMrvaRatingCode = frepMrvaRatingCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
	}

	public FrepMrvaRatingCode(String frepMrvaRatingCode, String description, Date effectiveDate, Date expiryDate,
			Date updateTimestamp, Set chrChecklists) {
		this.frepMrvaRatingCode = frepMrvaRatingCode;
		this.description = description;
		this.effectiveDate = effectiveDate;
		this.expiryDate = expiryDate;
		this.updateTimestamp = updateTimestamp;
		this.chrChecklists = chrChecklists;
	}

	public String getFrepMrvaRatingCode() {
		return this.frepMrvaRatingCode;
	}

	public void setFrepMrvaRatingCode(String frepMrvaRatingCode) {
		this.frepMrvaRatingCode = frepMrvaRatingCode;
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

	public Set getChrChecklists() {
		return this.chrChecklists;
	}

	public void setChrChecklists(Set chrChecklists) {
		this.chrChecklists = chrChecklists;
	}

}
