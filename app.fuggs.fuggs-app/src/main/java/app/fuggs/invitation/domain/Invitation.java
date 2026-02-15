package app.fuggs.invitation.domain;

import app.fuggs.member.domain.Member;
import app.fuggs.organization.domain.Organization;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Represents an invitation to join an organization. Invitations are sent by
 * existing members to invite new users to their organization.
 */
@Entity
public class Invitation extends PanacheEntity
{
	/**
	 * Unique token used in the invitation link. Generated as UUID string.
	 */
	@Column(nullable = false, unique = true, length = 36)
	@NotBlank
	private String token;

	/**
	 * Email address of the person being invited.
	 */
	@Column(nullable = false)
	@Email
	@NotBlank
	private String email;

	/**
	 * The organization the invitee will join. Null for founder invitations
	 * where the organization will be created during registration.
	 */
	@ManyToOne(optional = true)
	@JoinColumn(name = "organization_id", nullable = true)
	private Organization organization;

	/**
	 * The role that will be assigned when the invitation is accepted.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@NotNull
	private InvitationRole role;

	/**
	 * Current status of the invitation.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@NotNull
	private InvitationStatus status;

	/**
	 * When the invitation expires and can no longer be accepted.
	 */
	@Column(nullable = false)
	@NotNull
	private Instant expiresAt;

	/**
	 * The member who sent the invitation.
	 */
	@ManyToOne(optional = false)
	@JoinColumn(name = "invited_by_id", nullable = false)
	@NotNull
	private Member invitedBy;

	/**
	 * When the invitation was created.
	 */
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * When the invitation was accepted.
	 */
	@Column
	private Instant acceptedAt;

	public Invitation()
	{
		// Default constructor for JPA
	}

	/**
	 * Creates a new invitation with a generated token and default expiration (7
	 * days).
	 *
	 * @param email
	 *            Email address of the invitee
	 * @param organization
	 *            Organization to join
	 * @param role
	 *            Role to assign
	 * @param invitedBy
	 *            Member sending the invitation
	 */
	public Invitation(String email, Organization organization, InvitationRole role, Member invitedBy)
	{
		this.email = email;
		this.organization = organization;
		this.role = role;
		this.invitedBy = invitedBy;
		this.token = UUID.randomUUID().toString();
		this.status = InvitationStatus.PENDING;
		this.createdAt = Instant.now();
		this.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
	}

	@PrePersist
	void onCreate()
	{
		if (this.createdAt == null)
		{
			this.createdAt = Instant.now();
		}
		if (this.token == null || this.token.isBlank())
		{
			this.token = UUID.randomUUID().toString();
		}
		if (this.status == null)
		{
			this.status = InvitationStatus.PENDING;
		}
		if (this.expiresAt == null)
		{
			this.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
		}
	}

	/**
	 * Checks if the invitation is valid and can be accepted.
	 *
	 * @return true if the invitation is pending and not expired
	 */
	public boolean isValid()
	{
		return status == InvitationStatus.PENDING && !isExpired();
	}

	/**
	 * Checks if the invitation has expired.
	 *
	 * @return true if the current time is after the expiration time
	 */
	public boolean isExpired()
	{
		return Instant.now().isAfter(expiresAt);
	}

	/**
	 * Checks if the invitation can be accepted by the given email address.
	 *
	 * @param emailAddress
	 *            The email address attempting to accept
	 * @return true if the invitation is valid and the email matches
	 */
	public boolean canBeAcceptedBy(String emailAddress)
	{
		if (emailAddress == null || emailAddress.isBlank())
		{
			return false;
		}
		return isValid() && email.equalsIgnoreCase(emailAddress.trim());
	}

	// Getters and setters

	public Long getId()
	{
		return id;
	}

	public String getToken()
	{
		return token;
	}

	public void setToken(String token)
	{
		this.token = token;
	}

	public String getEmail()
	{
		return email;
	}

	public void setEmail(String email)
	{
		this.email = email;
	}

	public Organization getOrganization()
	{
		return organization;
	}

	public void setOrganization(Organization organization)
	{
		this.organization = organization;
	}

	public InvitationRole getRole()
	{
		return role;
	}

	public void setRole(InvitationRole role)
	{
		this.role = role;
	}

	public InvitationStatus getStatus()
	{
		return status;
	}

	public void setStatus(InvitationStatus status)
	{
		this.status = status;
	}

	public Instant getExpiresAt()
	{
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt)
	{
		this.expiresAt = expiresAt;
	}

	public Member getInvitedBy()
	{
		return invitedBy;
	}

	public void setInvitedBy(Member invitedBy)
	{
		this.invitedBy = invitedBy;
	}

	public Instant getCreatedAt()
	{
		return createdAt;
	}

	public Instant getAcceptedAt()
	{
		return acceptedAt;
	}

	public void setAcceptedAt(Instant acceptedAt)
	{
		this.acceptedAt = acceptedAt;
	}

	/**
	 * Returns the inviter's display name for template use.
	 *
	 * @return Display name of the inviting member
	 */
	public String getInviterName()
	{
		return invitedBy != null ? invitedBy.getDisplayName() : "";
	}

	/**
	 * Returns the organization name for template use.
	 *
	 * @return Display name of the organization
	 */
	public String getOrganizationName()
	{
		return organization != null ? organization.getDisplayName() : "";
	}

	/**
	 * Formats the creation date for display in templates.
	 *
	 * @return Formatted date string (dd.MM.yyyy HH:mm)
	 */
	public String getDisplayCreatedAt()
	{
		if (createdAt == null)
		{
			return "";
		}
		return createdAt.atZone(ZoneId.systemDefault())
			.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
	}

	/**
	 * Formats the expiration date for display in templates.
	 *
	 * @return Formatted date string (dd.MM.yyyy HH:mm)
	 */
	public String getDisplayExpiresAt()
	{
		if (expiresAt == null)
		{
			return "";
		}
		return expiresAt.atZone(ZoneId.systemDefault())
			.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
	}

	/**
	 * Formats the acceptance date for display in templates.
	 *
	 * @return Formatted date string (dd.MM.yyyy HH:mm)
	 */
	public String getDisplayAcceptedAt()
	{
		if (acceptedAt == null)
		{
			return "";
		}
		return acceptedAt.atZone(ZoneId.systemDefault())
			.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
	}
}
