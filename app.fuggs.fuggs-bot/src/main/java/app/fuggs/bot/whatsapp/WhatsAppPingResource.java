package app.fuggs.bot.whatsapp;

import java.util.Map;

import app.fuggs.bot.whatsapp.model.WhatsAppPhoneNumberInfo;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Dev-only endpoint to verify the WhatsApp connection on demand.
 * <p>
 * Deliberately unauthenticated and restricted to the dev profile so it never
 * exists in a production build.
 * </p>
 */
@Path("/api/whatsapp/ping")
@IfBuildProfile("dev")
public class WhatsAppPingResource
{
	@Inject
	WhatsAppConnectivityService connectivityService;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response ping()
	{
		try
		{
			WhatsAppPhoneNumberInfo phoneNumber = connectivityService.whoAmI();
			return Response.ok(Map.of(
				"connected", true,
				"displayPhoneNumber", phoneNumber.displayPhoneNumber(),
				"verifiedName", phoneNumber.verifiedName(),
				"phoneNumberId", phoneNumber.id())).build();
		}
		catch (Exception e)
		{
			return Response.status(Response.Status.SERVICE_UNAVAILABLE)
				.entity(Map.of("connected", false, "error", String.valueOf(e.getMessage())))
				.build();
		}
	}
}
