package app.fuggs.bot.whatsapp;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import app.fuggs.bot.whatsapp.model.WhatsAppMediaUrlResponse;
import app.fuggs.bot.whatsapp.model.WhatsAppPhoneNumberInfo;
import app.fuggs.bot.whatsapp.model.WhatsAppSendMessageRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * WhatsApp Cloud (Graph) API client.
 * <p>
 * The credential travels as a bearer token header rather than part of the URL
 * path, so every method takes it as an explicit parameter. Base URL is
 * configured via {@code quarkus.rest-client.whatsapp.url} (Meta's versioned
 * Graph API root) so tests can point it at WireMock.
 * </p>
 */
@RegisterRestClient(configKey = "whatsapp")
@Produces(MediaType.APPLICATION_JSON)
public interface WhatsAppClient
{
	/**
	 * Returns basic information about the configured sender number. Cheapest
	 * way to verify that the access token and phone number id are valid and the
	 * Graph API is reachable.
	 *
	 * @param phoneNumberId
	 *            the WhatsApp phone number id
	 * @param bearerToken
	 *            the {@code Authorization} header value, see
	 *            {@link WhatsAppConfig#bearerToken()}
	 * @return the phone number's own record
	 */
	@GET
	@Path("/{phoneNumberId}")
	WhatsAppPhoneNumberInfo getPhoneNumber(
		@PathParam("phoneNumberId") String phoneNumberId,
		@HeaderParam("Authorization") String bearerToken);

	/**
	 * Resolves the short-lived download URL for a previously referenced
	 * {@code media id}. The returned {@code url} must be fetched with the same
	 * bearer token - it is not a public link.
	 *
	 * @param mediaId
	 *            the media id from an incoming message
	 * @param bearerToken
	 *            the {@code Authorization} header value
	 * @return media metadata including the download URL
	 */
	@GET
	@Path("/{mediaId}")
	WhatsAppMediaUrlResponse getMediaUrl(
		@PathParam("mediaId") String mediaId,
		@HeaderParam("Authorization") String bearerToken);

	/**
	 * Sends a free-form text message. Only valid within Meta's 24h service
	 * window, or ever for our test recipients - see
	 * {@code docs/plan-whatsapp-bot.md} §6.
	 *
	 * @param phoneNumberId
	 *            the WhatsApp phone number id to send from
	 * @param bearerToken
	 *            the {@code Authorization} header value
	 * @param request
	 *            recipient and message text
	 */
	@POST
	@Path("/{phoneNumberId}/messages")
	@Consumes(MediaType.APPLICATION_JSON)
	void sendMessage(
		@PathParam("phoneNumberId") String phoneNumberId,
		@HeaderParam("Authorization") String bearerToken,
		WhatsAppSendMessageRequest request);
}
