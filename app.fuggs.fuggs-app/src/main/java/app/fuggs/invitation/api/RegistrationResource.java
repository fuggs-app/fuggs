package app.fuggs.invitation.api;

import app.fuggs.invitation.domain.Invitation;
import app.fuggs.invitation.domain.InvitationRole;
import app.fuggs.invitation.model.RegistrationData;
import app.fuggs.invitation.service.InvitationService;
import app.fuggs.invitation.service.RegistrationService;
import app.fuggs.member.domain.Member;
import app.fuggs.shared.util.FlashKeys;
import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/registrierung")
public class RegistrationResource extends Controller
{
	private static final Logger LOG = LoggerFactory.getLogger(RegistrationResource.class);

	@Inject
	InvitationService invitationService;

	@Inject
	RegistrationService registrationService;

	@CheckedTemplate
	public static class Templates
	{
		private Templates()
		{
			// static
		}

		public static native TemplateInstance registration(String token, Invitation invitation, boolean isFounder);

		public static native TemplateInstance erfolg(Member member, String organizationName);

		public static native TemplateInstance invalid(String errorMessage);
	}

	@GET
	@Path("")
	public TemplateInstance registration(@RestQuery String token)
	{
		if (token == null || token.isBlank())
		{
			return Templates.invalid("Kein Einladungstoken angegeben");
		}

		try
		{
			Invitation invitation = invitationService.validateToken(token);

			// Determine if this is a founder registration (no organization
			// assigned)
			boolean isFounder = invitation.getOrganization() == null;

			return Templates.registration(token, invitation, isFounder);
		}
		catch (IllegalArgumentException e)
		{
			LOG.warn("Invalid registration token: token={}, error={}", token, e.getMessage());
			return Templates.invalid(e.getMessage());
		}
	}

	@POST
	@Transactional
	@Path("/founder")
	public void registerFounder(
		@RestForm String token,
		@RestForm String firstName,
		@RestForm String lastName,
		@RestForm String email,
		@RestForm String password,
		@RestForm String organizationName)
	{
		if (validationFailed())
		{
			redirect(RegistrationResource.class).registration(token);
			return;
		}

		RegistrationData registrationData = new RegistrationData(firstName, lastName, email, password);
		registrationData.setOrganizationName(organizationName);

		try
		{
			Member member = registrationService.registerFounder(token, registrationData);

			LOG.info("Founder registered: memberId={}, email={}, organizationId={}",
				member.id, member.getEmail(), member.getOrganization().id);

			// Redirect to success page
			redirect(RegistrationResource.class).erfolg(member.id);
		}
		catch (IllegalArgumentException e)
		{
			LOG.warn("Founder registration failed: token={}, error={}", token, e.getMessage());
			flash(FlashKeys.ERROR, e.getMessage());
			redirect(RegistrationResource.class).registration(token);
		}
	}

	@POST
	@Transactional
	@Path("/member")
	public void registerMember(
		@RestForm String token,
		@RestForm String firstName,
		@RestForm String lastName,
		@RestForm String email,
		@RestForm String password)
	{
		if (validationFailed())
		{
			redirect(RegistrationResource.class).registration(token);
			return;
		}

		RegistrationData registrationData = new RegistrationData(firstName, lastName, email, password);

		try
		{
			Member member = registrationService.registerMember(token, registrationData);

			LOG.info("Member registered: memberId={}, email={}, organizationId={}",
				member.id, member.getEmail(), member.getOrganization().id);

			// Redirect to success page
			redirect(RegistrationResource.class).erfolg(member.id);
		}
		catch (IllegalArgumentException e)
		{
			LOG.warn("Member registration failed: token={}, error={}", token, e.getMessage());
			flash(FlashKeys.ERROR, e.getMessage());
			redirect(RegistrationResource.class).registration(token);
		}
	}

	@GET
	@Path("/erfolg")
	public TemplateInstance erfolg(@RestQuery Long memberId)
	{
		// This is a simple success page - no need to load member details for
		// now
		// In the future, we could display more information here
		return Templates.erfolg(null, null);
	}
}
