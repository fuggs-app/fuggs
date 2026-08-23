package app.fuggs.bot.telegram;

import java.util.Map;

import app.fuggs.bot.telegram.model.TelegramUser;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Dev-only endpoint to verify the Telegram connection on demand.
 * <p>
 * Deliberately unauthenticated and restricted to the dev profile so it never
 * exists in a production build.
 * </p>
 */
@Path("/api/telegram/ping")
@IfBuildProfile("dev")
public class TelegramPingResource
{
	@Inject
	TelegramConnectivityService connectivityService;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response ping()
	{
		try
		{
			TelegramUser bot = connectivityService.whoAmI();
			return Response.ok(Map.of(
				"connected", true,
				"username", bot.username(),
				"botId", bot.id(),
				"name", bot.firstName())).build();
		}
		catch (Exception e)
		{
			return Response.status(Response.Status.SERVICE_UNAVAILABLE)
				.entity(Map.of("connected", false, "error", String.valueOf(e.getMessage())))
				.build();
		}
	}
}
