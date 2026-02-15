package app.fuggs.invitation.repository;

import app.fuggs.invitation.domain.Invitation;
import app.fuggs.invitation.domain.InvitationStatus;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.security.OrganizationContext;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Repository for managing invitations. Provides methods to query invitations
 * with organization-scoped access.
 */
@ApplicationScoped
public class InvitationRepository implements PanacheRepository<Invitation>
{
	@Inject
	OrganizationContext organizationContext;

	/**
	 * Finds an invitation by its token. NOT scoped to organization as tokens
	 * are globally unique.
	 *
	 * @param token
	 *            The invitation token
	 * @return The invitation, or null if not found
	 */
	public Invitation findByToken(String token)
	{
		return find("token", token).firstResult();
	}

	/**
	 * Finds all pending invitations for the current organization, ordered by
	 * creation date.
	 *
	 * @return List of pending invitations
	 */
	public List<Invitation> findPendingByOrganization()
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return List.of();
		}
		return list("organization.id = ?1 AND status = ?2 ORDER BY createdAt DESC", orgId, InvitationStatus.PENDING);
	}

	/**
	 * Finds all pending invitations for a specific organization ID. Used when
	 * organization context is not available.
	 *
	 * @param organizationId
	 *            The organization ID
	 * @return List of pending invitations
	 */
	public List<Invitation> findPendingByOrganizationId(Long organizationId)
	{
		if (organizationId == null)
		{
			return List.of();
		}
		return list("organization.id = ?1 AND status = ?2 ORDER BY createdAt DESC", organizationId,
			InvitationStatus.PENDING);
	}

	/**
	 * Finds all invitations (any status) for a specific email address, scoped
	 * to the current organization.
	 *
	 * @param email
	 *            The email address
	 * @return List of invitations for the email
	 */
	public List<Invitation> findByEmail(String email)
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return List.of();
		}
		return list("organization.id = ?1 AND LOWER(email) = LOWER(?2) ORDER BY createdAt DESC", orgId, email);
	}

	/**
	 * Finds all invitations (any status) for a specific email address across
	 * all organizations. NOT scoped to organization - used for checking if user
	 * already has invitations.
	 *
	 * @param email
	 *            The email address
	 * @return List of all invitations for the email
	 */
	public List<Invitation> findByEmailGlobal(String email)
	{
		return list("LOWER(email) = LOWER(?1) ORDER BY createdAt DESC", email);
	}

	/**
	 * Finds an invitation by ID, scoped to the current organization. This
	 * prevents cross-organization access.
	 *
	 * @param id
	 *            The invitation ID
	 * @return The invitation, or null if not found or not in current
	 *         organization
	 */
	public Invitation findByIdScoped(Long id)
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return null;
		}
		return find("id = ?1 AND organization.id = ?2", id, orgId).firstResult();
	}

	/**
	 * Finds all invitations for a specific organization and status.
	 *
	 * @param organization
	 *            The organization
	 * @param status
	 *            The invitation status
	 * @return List of invitations matching the criteria
	 */
	public List<Invitation> findByOrganizationAndStatus(Organization organization, InvitationStatus status)
	{
		if (organization == null)
		{
			return List.of();
		}
		return list("organization.id = ?1 AND status = ?2 ORDER BY createdAt DESC", organization.id, status);
	}

	/**
	 * Finds a pending invitation by email address, scoped to the current
	 * organization.
	 *
	 * @param email
	 *            The email address
	 * @return The pending invitation, or null if not found
	 */
	public Invitation findPendingByEmail(String email)
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return null;
		}
		return find("organization.id = ?1 AND LOWER(email) = LOWER(?2) AND status = ?3", orgId, email,
			InvitationStatus.PENDING).firstResult();
	}
}
