package app.fuggs.invitation.service;

import app.fuggs.invitation.domain.Invitation;
import app.fuggs.invitation.model.RegistrationData;
import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import app.fuggs.member.service.KeycloakAdminService;
import app.fuggs.organization.domain.Organization;
import app.fuggs.organization.repository.OrganizationRepository;
import app.fuggs.shared.security.Roles;
import app.fuggs.shared.util.SlugUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class RegistrationService
{
	private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

	@Inject
	InvitationService invitationService;

	@Inject
	MemberRepository memberRepository;

	@Inject
	OrganizationRepository organizationRepository;

	@Inject
	KeycloakAdminService keycloakAdminService;

	/**
	 * Registers a new founder user with a new organization.
	 *
	 * @param inviteToken
	 *            The invitation token
	 * @param data
	 *            The registration data
	 * @return The created member
	 */
	@Transactional
	public Member registerFounder(String inviteToken, RegistrationData data)
	{
		LOG.info("Registering founder: email={}, organizationName={}", data.getEmail(), data.getOrganizationName());

		// Validate invitation
		Invitation invitation = invitationService.validateToken(inviteToken);

		// Validate organization name is provided
		if (data.getOrganizationName() == null || data.getOrganizationName().isBlank())
		{
			throw new IllegalArgumentException("Organisationsname ist erforderlich für Gründer-Registrierung");
		}

		// Check for duplicate email
		if (memberRepository.findByEmail(data.getEmail()) != null)
		{
			throw new IllegalArgumentException("Ein Benutzer mit dieser E-Mail-Adresse existiert bereits");
		}

		// Create organization
		Organization organization = createOrganization(data.getOrganizationName());

		// Create member
		Member member = createMember(data, organization, invitation, "FOUNDER");

		// Create Keycloak user with ADMIN and MEMBER roles
		String keycloakUserId = keycloakAdminService.createUser(member.getUserName(), member.getEmail(),
			member.getFirstName(), member.getLastName(), List.of(Roles.ADMIN, Roles.MEMBER));

		member.setKeycloakUserId(keycloakUserId);
		memberRepository.persist(member);

		LOG.info("Keycloak user created for founder: memberId={}, keycloakUserId={}", member.id, keycloakUserId);

		// Mark invitation as accepted
		invitationService.acceptInvitation(invitation);

		LOG.info("Founder registered successfully: memberId={}, organizationId={}, email={}", member.id, organization.id,
			member.getEmail());

		return member;
	}

	/**
	 * Registers a new member user to an existing organization.
	 *
	 * @param inviteToken
	 *            The invitation token
	 * @param data
	 *            The registration data
	 * @return The created member
	 */
	@Transactional
	public Member registerMember(String inviteToken, RegistrationData data)
	{
		LOG.info("Registering member: email={}", data.getEmail());

		// Validate invitation
		Invitation invitation = invitationService.validateToken(inviteToken);

		// Get organization from invitation
		Organization organization = invitation.getOrganization();
		if (organization == null)
		{
			throw new IllegalArgumentException("Einladung ist keiner Organisation zugeordnet");
		}

		// Check for duplicate email
		if (memberRepository.findByEmail(data.getEmail()) != null)
		{
			throw new IllegalArgumentException("Ein Benutzer mit dieser E-Mail-Adresse existiert bereits");
		}

		// Create member
		Member member = createMember(data, organization, invitation, "INVITED");

		// Determine Keycloak roles based on invitation role
		List<String> roles = invitation.getRole().toKeycloakRoles();

		// Create Keycloak user
		String keycloakUserId = keycloakAdminService.createUser(member.getUserName(), member.getEmail(),
			member.getFirstName(), member.getLastName(), roles);

		member.setKeycloakUserId(keycloakUserId);
		memberRepository.persist(member);

		LOG.info("Keycloak user created for member: memberId={}, keycloakUserId={}, roles={}", member.id, keycloakUserId,
			roles);

		// Mark invitation as accepted
		invitationService.acceptInvitation(invitation);

		LOG.info("Member registered successfully: memberId={}, organizationId={}, email={}", member.id, organization.id,
			member.getEmail());

		return member;
	}

	/**
	 * Creates a new organization with a unique slug.
	 */
	private Organization createOrganization(String name)
	{
		LOG.info("Creating organization: name={}", name);

		// Generate unique slug
		String slug = SlugUtil.generateUniqueSlug(name, organizationRepository);

		// Create organization
		Organization organization = new Organization();
		organization.setName(name);
		organization.setSlug(slug);
		organization.setDisplayName(name);
		organization.setActive(true);

		organizationRepository.persist(organization);

		LOG.info("Organization created: id={}, slug={}, name={}", organization.id, slug, name);

		return organization;
	}

	/**
	 * Creates a new member.
	 */
	private Member createMember(RegistrationData data, Organization organization, Invitation invitation,
		String inviteType)
	{
		LOG.info("Creating member: email={}, organizationId={}", data.getEmail(), organization.id);

		String username = generateUsername(data.getEmail());

		// Check for duplicate username
		if (memberRepository.findByUsername(username) != null)
		{
			throw new IllegalArgumentException("Benutzername bereits vergeben: " + username);
		}

		Member member = new Member();
		member.setFirstName(data.getFirstName());
		member.setLastName(data.getLastName());
		member.setEmail(data.getEmail());
		member.setUserName(username);
		member.setOrganization(organization);

		// Set invitation tracking fields
		member.setInvitedByMemberId(invitation.getInvitedBy() != null ? invitation.getInvitedBy().id : null);
		member.setInviteType(inviteType);
		member.setJoinedAt(Instant.now());

		memberRepository.persist(member);

		LOG.info("Member created: id={}, username={}, email={}", member.id, username, member.getEmail());

		return member;
	}

	/**
	 * Generates a username from an email address (part before @).
	 */
	private String generateUsername(String email)
	{
		if (email == null || !email.contains("@"))
		{
			throw new IllegalArgumentException("Invalid email address");
		}
		return email.substring(0, email.indexOf('@'));
	}
}
