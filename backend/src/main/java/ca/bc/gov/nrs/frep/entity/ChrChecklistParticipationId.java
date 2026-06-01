package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ChrChecklistParticipationId implements java.io.Serializable {

	@Column(name = "CHR_CHECKLIST_ID")
	private long chrChecklistId;

	@Column(name = "CHR_CHECKLIST_PARTICIPANT_ID")
	private long chrChecklistParticipantId;

	public ChrChecklistParticipationId() {
	}

	public ChrChecklistParticipationId(long chrChecklistId, long chrChecklistParticipantId) {
		this.chrChecklistId = chrChecklistId;
		this.chrChecklistParticipantId = chrChecklistParticipantId;
	}

	public long getChrChecklistId() {
		return this.chrChecklistId;
	}

	public void setChrChecklistId(long chrChecklistId) {
		this.chrChecklistId = chrChecklistId;
	}

	public long getChrChecklistParticipantId() {
		return this.chrChecklistParticipantId;
	}

	public void setChrChecklistParticipantId(long chrChecklistParticipantId) {
		this.chrChecklistParticipantId = chrChecklistParticipantId;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof ChrChecklistParticipationId))
			return false;
		ChrChecklistParticipationId castOther = (ChrChecklistParticipationId) other;

		return (this.getChrChecklistId() == castOther.getChrChecklistId())
				&& (this.getChrChecklistParticipantId() == castOther.getChrChecklistParticipantId());
	}

	public int hashCode() {
		int result = 17;

		result = 37 * result + (int) this.getChrChecklistId();
		result = 37 * result + (int) this.getChrChecklistParticipantId();
		return result;
	}

}
