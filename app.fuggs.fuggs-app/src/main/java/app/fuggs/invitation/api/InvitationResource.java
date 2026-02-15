package app.fuggs.invitation.api;

import app.fuggs.invitation.domain.Invitation;
import app.fuggs.invitation.domain.InvitationRole;
import app.fuggs.invitation.domain.InvitationStatus;
import app.fuggs.invitation.model.InvitationData;
import app.fuggs.invitation.repository.InvitationRepository;
import app.fuggs.invitation.service.InvitationService;
import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.security.OrganizationContext;
import app.fuggs.shared.security.Roles;
import app.fuggs.shared.util.FlashKeys;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jboss.resteasy.reactive.RestForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.util.List;

@RolesAllowed({ Roles.ADMIN })
@Path("/einladungen")
public class InvitationResource extends Controller
{
	private static final Logger LOG = LoggerFactory.getLogger(InvitationResource.class);

	@Inject
	InvitationRepository invitationRepository;

	@Inject
	InvitationService invitationService;

	@Inject
	MemberRepository memberRepository;

	@Inject
	OrganizationContext organizationContext;

	@Inject
	SecurityIdentity securityIdentity;

	@CheckedTemplate
	public static class Templates
	{
		private Templates()
		{
			// static
		}

		public static native TemplateInstance index(List<Invitation> pendingInvitations,
			List<Invitation> acceptedInvitations, List<Invitation> expiredInvitations);

		public static native TemplateInstance create();
	}

	@GET
	@Path("")
	public TemplateInstance index()
	{
		Organization currentOrg = organizationContext.getCurrentOrganization();

		List<Invitation> pending = invitationRepository.findByOrganizationAndStatus(currentOrg,
			InvitationStatus.PENDING);
		List<Invitation> accepted = invitationRepository.findByOrganizationAndStatus(currentOrg,
			InvitationStatus.ACCEPTED);
		List<Invitation> expired = invitationRepository.findByOrganizationAndStatus(currentOrg,
			InvitationStatus.EXPIRED);

		return Templates.index(pending, accepted, expired);
	}

	@GET
	@Path("/neu")
	public TemplateInstance create()
	{
		return Templates.create();
	}

	@POST
	@Path("")
	@Transactional
	public void createInvitation(
		@RestForm String email,
		@RestForm InvitationRole role)
	{
		if (validationFailed())
		{
			redirect(InvitationResource.class).create();
			return;
		}

		Organization currentOrg = organizationContext.getCurrentOrganization();
		Member currentMember = memberRepository.findByUsername(securityIdentity.getPrincipal().getName());

		if (currentMember == null)
		{
			LOG.error("Current member not found: username={}", securityIdentity.getPrincipal().getName());
			flash(FlashKeys.ERROR, "Fehler: Benutzer nicht gefunden");
			redirect(InvitationResource.class).index();
			return;
		}

		// Check if email already has a pending invitation
		Invitation existingInvitation = invitationRepository.findPendingByEmail(email);
		if (existingInvitation != null)
		{
			flash(FlashKeys.WARNING, "Es existiert bereits eine ausstehende Einladung für diese E-Mail-Adresse");
			redirect(InvitationResource.class).index();
			return;
		}

		// Check if email already belongs to a member
		Member existingMember = memberRepository.findByEmail(email);
		if (existingMember != null)
		{
			flash(FlashKeys.ERROR, "Ein Benutzer mit dieser E-Mail-Adresse existiert bereits");
			redirect(InvitationResource.class).create();
			return;
		}

		try
		{
			Invitation invitation = invitationService.createInvitation(
				email,
				role,
				currentOrg,
				currentMember);

			LOG.info("Invitation created: id={}, email={}, role={}", invitation.id, email, role);

			flash(FlashKeys.SUCCESS, "Einladung erstellt für " + email);
		}
		catch (Exception e)
		{
			LOG.error("Failed to create invitation: email={}, error={}", email, e.getMessage(), e);
			flash(FlashKeys.ERROR, "Fehler beim Erstellen der Einladung: " + e.getMessage());
		}

		redirect(InvitationResource.class).index();
	}

	@POST
	@Transactional
	@Path("/cancel")
	public void cancelInvitation(@RestForm Long invitationId)
	{
		LOG.info("Cancel invitation called: invitationId={}", invitationId);

		if (invitationId == null)
		{
			LOG.warn("Invitation ID is null");
			flash(FlashKeys.ERROR, "Einladung ID fehlt");
			redirect(InvitationResource.class).index();
			return;
		}

		Invitation invitation = invitationRepository.findByIdScoped(invitationId);
		if (invitation == null)
		{
			LOG.warn("Invitation not found: invitationId={}", invitationId);
			flash(FlashKeys.ERROR, "Einladung nicht gefunden");
			redirect(InvitationResource.class).index();
			return;
		}

		try
		{
			invitationService.cancelInvitation(invitation);
			flash(FlashKeys.SUCCESS, "Einladung storniert");
		}
		catch (IllegalStateException e)
		{
			LOG.warn("Cannot cancel invitation: id={}, error={}", invitationId, e.getMessage());
			flash(FlashKeys.ERROR, e.getMessage());
		}

		redirect(InvitationResource.class).index();
	}

	@POST
	@Transactional
	@Path("/resend")
	public void resendInvitation(@RestForm Long invitationId)
	{
		LOG.info("Resend invitation called: invitationId={}", invitationId);

		if (invitationId == null)
		{
			LOG.warn("Invitation ID is null");
			flash(FlashKeys.ERROR, "Einladung ID fehlt");
			redirect(InvitationResource.class).index();
			return;
		}

		Invitation invitation = invitationRepository.findByIdScoped(invitationId);
		if (invitation == null)
		{
			LOG.warn("Invitation not found: invitationId={}", invitationId);
			flash(FlashKeys.ERROR, "Einladung nicht gefunden");
			redirect(InvitationResource.class).index();
			return;
		}

		try
		{
			invitationService.resendInvitation(invitation);
			flash(FlashKeys.SUCCESS, "Einladung erneut gesendet an " + invitation.getEmail());
		}
		catch (IllegalStateException e)
		{
			LOG.warn("Cannot resend invitation: id={}, error={}", invitationId, e.getMessage());
			flash(FlashKeys.ERROR, e.getMessage());
		}

		redirect(InvitationResource.class).index();
	}
}
