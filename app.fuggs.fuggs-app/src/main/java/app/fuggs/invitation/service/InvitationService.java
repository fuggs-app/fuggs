package app.fuggs.invitation.service;

import app.fuggs.invitation.domain.Invitation;
import app.fuggs.invitation.domain.InvitationRole;
import app.fuggs.invitation.domain.InvitationStatus;
import app.fuggs.invitation.repository.InvitationRepository;
import app.fuggs.member.domain.Member;
import app.fuggs.organization.domain.Organization;
import app.fuggs.organization.repository.OrganizationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class InvitationService
{
	private static final Logger LOG = LoggerFactory.getLogger(InvitationService.class);
	private static final int EXPIRATION_DAYS = 7;

	@Inject
	InvitationRepository invitationRepository;

	@Inject
	OrganizationRepository organizationRepository;

	@Inject
	EmailService emailService;

	/**
	 * Creates a new invitation with a unique token.
	 *
	 * @param email
	 *            The invitee's email address
	 * @param role
	 *            The role to assign (ADMIN or MEMBER)
	 * @param organization
	 *            The organization
	 * @param invitedBy
	 *            The member creating the invitation
	 * @return The created invitation
	 */
	@Transactional
	public Invitation createInvitation(String email, InvitationRole role, Organization organization, Member invitedBy)
	{
		LOG.info("Creating invitation: email={}, role={}, organizationId={}, invitedBy={}", email, role,
			organization.id, invitedBy.getDisplayName());

		// Generate unique token
		String token = UUID.randomUUID().toString();

		// Create invitation
		Invitation invitation = new Invitation();
		invitation.setEmail(email);
		invitation.setToken(token);
		invitation.setRole(role);
		invitation.setStatus(InvitationStatus.PENDING);
		invitation.setOrganization(organization);
		invitation.setInvitedBy(invitedBy);
		invitation.setExpiresAt(Instant.now().plus(EXPIRATION_DAYS, ChronoUnit.DAYS));

		invitationRepository.persist(invitation);

		LOG.info("Invitation created: id={}, token={}, email={}", invitation.id, token, email);

		// Send invitation email
		try
		{
			emailService.sendInvitationEmail(email, token, organization.getDisplayName(), invitedBy.getDisplayName(),
				role.name());
			LOG.info("Invitation email sent: id={}, email={}", invitation.id, email);
		}
		catch (Exception e)
		{
			LOG.error("Failed to send invitation email: id={}, email={}, error={}", invitation.id, email,
				e.getMessage(), e);
			// Continue - invitation is still created even if email fails
		}

		return invitation;
	}

	/**
	 * Validates an invitation token.
	 *
	 * @param token
	 *            The invitation token
	 * @return The invitation if valid
	 * @throws IllegalArgumentException
	 *             if token is invalid, expired, or already used
	 */
	public Invitation validateToken(String token)
	{
		LOG.debug("Validating invitation token: {}", token);

		Invitation invitation = invitationRepository.findByToken(token);
		if (invitation == null)
		{
			LOG.warn("Invitation token not found: {}", token);
			throw new IllegalArgumentException("Ungültiger Einladungslink");
		}

		// Check status
		if (invitation.getStatus() != InvitationStatus.PENDING)
		{
			LOG.warn("Invitation already used or cancelled: token={}, status={}", token, invitation.getStatus());
			throw new IllegalArgumentException("Dieser Einladungslink wurde bereits verwendet oder ist ungültig");
		}

		// Check expiration
		if (Instant.now().isAfter(invitation.getExpiresAt()))
		{
			LOG.warn("Invitation expired: token={}, expiresAt={}", token, invitation.getExpiresAt());
			invitation.setStatus(InvitationStatus.EXPIRED);
			invitationRepository.persist(invitation);
			throw new IllegalArgumentException("Dieser Einladungslink ist abgelaufen");
		}

		LOG.debug("Invitation token valid: id={}, email={}", invitation.id, invitation.getEmail());
		return invitation;
	}

	/**
	 * Marks an invitation as accepted.
	 *
	 * @param invitation
	 *            The invitation to accept
	 */
	@Transactional
	public void acceptInvitation(Invitation invitation)
	{
		LOG.info("Accepting invitation: id={}, email={}", invitation.id, invitation.getEmail());

		// Fetch managed entity from repository
		Invitation managedInvitation = invitationRepository.findById(invitation.id);
		if (managedInvitation == null)
		{
			throw new IllegalArgumentException("Einladung nicht gefunden");
		}

		managedInvitation.setStatus(InvitationStatus.ACCEPTED);
		managedInvitation.setAcceptedAt(Instant.now());
		// Changes to managed entities are automatically persisted

		LOG.info("Invitation accepted: id={}, email={}", managedInvitation.id, managedInvitation.getEmail());
	}

	/**
	 * Cancels a pending invitation.
	 *
	 * @param invitation
	 *            The invitation to cancel
	 */
	@Transactional
	public void cancelInvitation(Invitation invitation)
	{
		LOG.info("Cancelling invitation: id={}, email={}", invitation.id, invitation.getEmail());

		// Fetch managed entity from repository
		Invitation managedInvitation = invitationRepository.findById(invitation.id);
		if (managedInvitation == null)
		{
			throw new IllegalArgumentException("Einladung nicht gefunden");
		}

		if (managedInvitation.getStatus() != InvitationStatus.PENDING)
		{
			LOG.warn("Cannot cancel invitation with status: {}", managedInvitation.getStatus());
			throw new IllegalStateException("Einladung kann nicht storniert werden");
		}

		managedInvitation.setStatus(InvitationStatus.CANCELLED);
		// Changes to managed entities are automatically persisted

		LOG.info("Invitation cancelled: id={}, email={}", managedInvitation.id, managedInvitation.getEmail());
	}

	/**
	 * Resends an invitation email.
	 *
	 * @param invitation
	 *            The invitation to resend
	 */
	@Transactional
	public void resendInvitation(Invitation invitation)
	{
		LOG.info("Resending invitation: id={}, email={}", invitation.id, invitation.getEmail());

		// Validate invitation is still pending
		if (invitation.getStatus() != InvitationStatus.PENDING)
		{
			LOG.warn("Cannot resend invitation with status: {}", invitation.getStatus());
			throw new IllegalStateException("Einladung kann nicht erneut gesendet werden");
		}

		// Check not expired
		if (Instant.now().isAfter(invitation.getExpiresAt()))
		{
			LOG.warn("Cannot resend expired invitation: id={}", invitation.id);
			throw new IllegalStateException("Einladung ist abgelaufen");
		}

		// Send invitation email
		try
		{
			emailService.sendInvitationEmail(invitation.getEmail(), invitation.getToken(),
				invitation.getOrganization().getDisplayName(), invitation.getInvitedBy().getDisplayName(),
				invitation.getRole().name());
			LOG.info("Invitation email resent successfully: id={}, email={}", invitation.id, invitation.getEmail());
		}
		catch (Exception e)
		{
			LOG.error("Failed to resend invitation email: id={}, email={}, error={}", invitation.id,
				invitation.getEmail(), e.getMessage(), e);
			throw new RuntimeException("Fehler beim Senden der E-Mail", e);
		}

		LOG.info("Invitation resent: id={}, email={}", invitation.id, invitation.getEmail());
	}
}
