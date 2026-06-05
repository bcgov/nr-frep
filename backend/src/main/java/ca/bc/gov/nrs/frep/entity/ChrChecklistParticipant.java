package ca.bc.gov.nrs.frep.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "CHR_CHECKLIST_PARTICIPANT", schema = "THE")
public class ChrChecklistParticipant implements java.io.Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chrChecklistParticipantIdSeq")
	@SequenceGenerator(name = "chrChecklistParticipantIdSeq", sequenceName = "THE.CHR_CHECKLIST_PARTICIPANT_SEQ", allocationSize = 1)
	@Column(name = "CHR_CHECKLIST_PARTICIPANT_ID")
	private Long chrChecklistParticipantId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CLIENT_NUMBER")
	private ForestClient forestClient;

	@Column(name = "FIRST_NAME")
	private String firstName;

	@Column(name = "LAST_NAME")
	private String lastName;

	@Column(name = "ORGANIZATION_NAME")
	private String organizationName;

	@Column(name = "ENTRY_USERID")
	private String entryUserid;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "ENTRY_TIMESTAMP")
	private Date entryTimestamp;

	@Column(name = "UPDATE_USERID")
	private String updateUserid;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "UPDATE_TIMESTAMP")
	private Date updateTimestamp;

	@Column(name = "REVISION_COUNT")
	private long revisionCount;

	public ChrChecklistParticipant() {
	}

	public ChrChecklistParticipant(String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp,
			long revisionCount) {
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public ChrChecklistParticipant(ForestClient forestClient, String firstName, String lastName,
			String organizationName, String entryUserid, Date entryTimestamp, String updateUserid, Date updateTimestamp,
			long revisionCount) {
		this.forestClient = forestClient;
		this.firstName = firstName;
		this.lastName = lastName;
		this.organizationName = organizationName;
		this.entryUserid = entryUserid;
		this.entryTimestamp = entryTimestamp;
		this.updateUserid = updateUserid;
		this.updateTimestamp = updateTimestamp;
		this.revisionCount = revisionCount;
	}

	public Long getChrChecklistParticipantId() {
		return this.chrChecklistParticipantId;
	}

	public void setChrChecklistParticipantId(Long chrChecklistParticipantId) {
		this.chrChecklistParticipantId = chrChecklistParticipantId;
	}

	public ForestClient getForestClient() {
		return this.forestClient;
	}

	public void setForestClient(ForestClient forestClient) {
		this.forestClient = forestClient;
	}

	public String getFirstName() {
		return this.firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return this.lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getOrganizationName() {
		return this.organizationName;
	}

	public void setOrganizationName(String organizationName) {
		this.organizationName = organizationName;
	}

	public String getEntryUserid() {
		return this.entryUserid;
	}

	public void setEntryUserid(String entryUserid) {
		this.entryUserid = entryUserid;
	}

	public Date getEntryTimestamp() {
		return this.entryTimestamp;
	}

	public void setEntryTimestamp(Date entryTimestamp) {
		this.entryTimestamp = entryTimestamp;
	}

	public String getUpdateUserid() {
		return this.updateUserid;
	}

	public void setUpdateUserid(String updateUserid) {
		this.updateUserid = updateUserid;
	}

	public Date getUpdateTimestamp() {
		return this.updateTimestamp;
	}

	public void setUpdateTimestamp(Date updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}

	public long getRevisionCount() {
		return this.revisionCount;
	}

	public void setRevisionCount(long revisionCount) {
		this.revisionCount = revisionCount;
	}

}
