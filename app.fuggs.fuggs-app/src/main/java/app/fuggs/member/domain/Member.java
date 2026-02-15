package app.fuggs.member.domain;

import app.fuggs.bommel.domain.Bommel;
import app.fuggs.organization.domain.Organization;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Member extends PanacheEntity
{
	@NotBlank
	private String firstName;

	@NotBlank
	private String lastName;

	@Email
	private String email;

	private String phone;

	@Column(unique = true)
	private String userName;

	@ManyToOne(optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@OneToMany(mappedBy = "responsibleMember", fetch = FetchType.LAZY)
	private List<Bommel> responsibleBommels = new ArrayList<>();

	/**
	 * ID of the member who invited this member (if invited).
	 */
	@Column(name = "invited_by_member_id")
	private Long invitedByMemberId;

	/**
	 * Type of invitation used to join (BETA or INVITED).
	 */
	@Column(length = 20)
	private String inviteType;

	/**
	 * When the member joined the organization.
	 */
	@Column(name = "joined_at")
	private Instant joinedAt;

	/**
	 * Keycloak user ID for authentication mapping.
	 */
	@Column(name = "keycloak_user_id", unique = true)
	private String keycloakUserId;

	public Long getId()
	{
		return id;
	}

	public String getFirstName()
	{
		return firstName;
	}

	public void setFirstName(String firstName)
	{
		this.firstName = firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public void setLastName(String lastName)
	{
		this.lastName = lastName;
	}

	public String getEmail()
	{
		return email;
	}

	public void setEmail(String email)
	{
		this.email = email;
	}

	public String getPhone()
	{
		return phone;
	}

	public void setPhone(String phone)
	{
		this.phone = phone;
	}

	public List<Bommel> getResponsibleBommels()
	{
		return responsibleBommels;
	}

	public String getUserName()
	{
		return userName;
	}

	public void setUserName(String userName)
	{
		this.userName = userName;
	}

	public Organization getOrganization()
	{
		return organization;
	}

	public void setOrganization(Organization organization)
	{
		this.organization = organization;
	}

	public String getDisplayName()
	{
		return firstName + " " + lastName;
	}

	public Long getInvitedByMemberId()
	{
		return invitedByMemberId;
	}

	public void setInvitedByMemberId(Long invitedByMemberId)
	{
		this.invitedByMemberId = invitedByMemberId;
	}

	public String getInviteType()
	{
		return inviteType;
	}

	public void setInviteType(String inviteType)
	{
		this.inviteType = inviteType;
	}

	public Instant getJoinedAt()
	{
		return joinedAt;
	}

	public void setJoinedAt(Instant joinedAt)
	{
		this.joinedAt = joinedAt;
	}

	public String getKeycloakUserId()
	{
		return keycloakUserId;
	}

	public void setKeycloakUserId(String keycloakUserId)
	{
		this.keycloakUserId = keycloakUserId;
	}
}
