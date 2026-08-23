package app.fuggs.bot.telegram;

import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import app.fuggs.bot.telegram.model.SendMessageRequest;
import app.fuggs.bot.telegram.model.TelegramResponse;
import app.fuggs.bot.telegram.model.TelegramUpdate;
import app.fuggs.bot.telegram.model.TelegramUser;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Telegram Bot API client.
 * <p>
 * The bot token is part of the URL path rather than a header, hence the
 * {@code token} path parameter on every method. Base URL is configured via
 * {@code quarkus.rest-client.telegram.url} so tests can point it at WireMock.
 * </p>
 */
@Path("/")
@RegisterRestClient(configKey = "telegram")
@Produces(MediaType.APPLICATION_JSON)
public interface TelegramClient
{
	/**
	 * Returns basic information about the bot. Cheapest way to verify that the
	 * token is valid and the API is reachable.
	 *
	 * @param token
	 *            the bot token
	 * @return the bot's own user record
	 */
	@GET
	@Path("/bot{token}/getMe")
	TelegramResponse<TelegramUser> getMe(@PathParam("token") String token);

	/**
	 * Long-polls for incoming updates.
	 *
	 * @param token
	 *            the bot token
	 * @param offset
	 *            first update id to return; acknowledges everything below it
	 * @param timeout
	 *            seconds to hold the connection open, 0 for a short poll
	 * @return the pending updates
	 */
	@GET
	@Path("/bot{token}/getUpdates")
	TelegramResponse<List<TelegramUpdate>> getUpdates(
		@PathParam("token") String token,
		@QueryParam("offset") Long offset,
		@QueryParam("timeout") Integer timeout);

	/**
	 * Sends a text message to a chat.
	 *
	 * @param token
	 *            the bot token
	 * @param request
	 *            target chat id and message text
	 * @return the sent message envelope
	 */
	@POST
	@Path("/bot{token}/sendMessage")
	@Consumes(MediaType.APPLICATION_JSON)
	TelegramResponse<Object> sendMessage(
		@PathParam("token") String token,
		SendMessageRequest request);
}
