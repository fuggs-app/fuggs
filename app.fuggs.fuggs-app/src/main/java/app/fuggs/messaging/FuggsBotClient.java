package app.fuggs.messaging;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

/**
 * Client for fuggs-bot's internal notification endpoint
 * ({@code NotificationResource}), used to push a proactive chat message to a
 * member days after their original upload (AC #3) - something no HTTP
 * request/response from the original bot conversation can reach anymore.
 * Mirrors {@code app.fuggs.bot.document.FuggsAppClient} on the other side: same
 * shared-secret header, same reasoning for JSON over form-encoded (the global
 * CSRF filter only exempts non-form content types).
 */
@Path("/api/notifications")
@RegisterRestClient(configKey = "fuggs-bot")
@ClientHeaderParam(name = "X-Bot-Secret", value = "{sharedSecret}")
public interface FuggsBotClient
{
	record NotificationRequest(String channel, String recipientId, String message)
	{
	}

	/**
	 * Sends a notification through the given channel. Failures (bot service
	 * down, unknown channel, invalid recipient) surface as
	 * {@code WebApplicationException} - callers decide whether that's worth
	 * failing on; for AC #3 it deliberately isn't (see
	 * {@code BotNotificationService#notifyTransactionBooked}).
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	void sendNotification(NotificationRequest request);

	/**
	 * Resolves the configured shared secret at call time, referenced by the
	 * class-level {@code @ClientHeaderParam} above. Must be a default (not
	 * static) method - the MicroProfile Rest Client spec resolves
	 * {@code {methodName}} header values as instance methods on the client
	 * proxy.
	 *
	 * @return the shared secret, or an empty string when unconfigured
	 */
	default String sharedSecret()
	{
		return ConfigProvider.getConfig()
			.getOptionalValue("fuggs.bot.shared-secret", String.class)
			.orElse("");
	}
}
