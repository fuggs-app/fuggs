package app.fuggs.bot.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.fuggs.bot.whatsapp.WhatsAppClient;
import app.fuggs.bot.whatsapp.WhatsAppConfig;
import app.fuggs.bot.whatsapp.model.WhatsAppSendMessageRequest;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Internal, machine-to-machine endpoint fuggs-app calls to push a proactive
 * chat message to a member - currently only used for issue #94's AC #3 ("your
 * receipt was just booked"), sent long after the original upload conversation
 * has ended, so nothing about that original HTTP request/response is still
 * around to reply on.
 * <p>
 * Deliberately not a Renarde {@code Controller} and not {@code @Authenticated}
 * - the caller has no user session, only a shared secret, mirroring
 * {@code BotDocumentResource} on the fuggs-app side (same {@code
 * X-Bot-Secret} header, same {@code fuggs.app.shared-secret} value - it is one
 * shared secret used by both services in both call directions).
 * </p>
 * <p>
 * Dispatches on {@code channel} for the same reason {@code
 * BotDocumentResource.resolveMember} does: fuggs-app doesn't need to know how
 * each channel delivers text, only that it can. Adding a channel means one more
 * {@code case} here, nothing else in this class changes.
 * </p>
 */
@Path("/api/notifications")
@ApplicationScoped
public class NotificationResource
{
	private static final Logger LOG = LoggerFactory.getLogger(NotificationResource.class);

	private static final String CHANNEL_WHATSAPP = "whatsapp";

	@ConfigProperty(name = "fuggs.app.shared-secret")
	Optional<String> sharedSecret;

	@Inject
	WhatsAppConfig whatsAppConfig;

	@RestClient
	WhatsAppClient whatsAppClient;

	public record NotificationRequest(String channel, String recipientId, String message)
	{
	}

	public record ErrorResponse(String error)
	{
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response send(@HeaderParam("X-Bot-Secret") String secret, NotificationRequest request)
	{
		if (!isAuthorized(secret))
		{
			return Response.status(Response.Status.UNAUTHORIZED).entity(new ErrorResponse("unauthorized")).build();
		}

		if (request == null || request.recipientId() == null || request.message() == null
			|| request.message().isBlank())
		{
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(new ErrorResponse("missing_fields"))
				.build();
		}

		return switch (request.channel() == null ? "" : request.channel())
		{
			case CHANNEL_WHATSAPP -> sendWhatsApp(request);
			default -> Response.status(Response.Status.BAD_REQUEST)
				.entity(new ErrorResponse("unsupported_channel"))
				.build();
		};
	}

	private Response sendWhatsApp(NotificationRequest request)
	{
		if (!whatsAppConfig.isUsable())
		{
			LOG.warn("Cannot deliver WhatsApp notification, integration not configured: recipientId={}",
				request.recipientId());
			return Response.status(Response.Status.SERVICE_UNAVAILABLE)
				.entity(new ErrorResponse("whatsapp_unavailable"))
				.build();
		}

		try
		{
			whatsAppClient.sendMessage(whatsAppConfig.phoneNumberId().orElseThrow(), whatsAppConfig.bearerToken(),
				new WhatsAppSendMessageRequest(request.recipientId(), request.message()));
			LOG.info("Delivered notification via WhatsApp: to={}", request.recipientId());
			return Response.ok().build();
		}
		catch (Exception e)
		{
			LOG.error("Failed to deliver WhatsApp notification: to={}, error={}", request.recipientId(),
				e.getMessage(), e);
			return Response.status(Response.Status.BAD_GATEWAY)
				.entity(new ErrorResponse("delivery_failed"))
				.build();
		}
	}

	/**
	 * Compares the provided header against the configured shared secret, using
	 * a constant-time comparison. An unset secret is only treated as a match in
	 * {@code dev}/{@code test} launch modes, so local dev works without
	 * exporting {@code FUGGS_BOT_SHARED_SECRET} - everywhere else this endpoint
	 * fails closed instead of silently accepting unauthenticated requests,
	 * matching {@code WhatsAppWebhookResource}'s signature check.
	 */
	private boolean isAuthorized(String secret)
	{
		String expected = sharedSecret.orElse("");
		if (expected.isBlank())
		{
			LaunchMode mode = LaunchMode.current();
			return mode == LaunchMode.DEVELOPMENT || mode == LaunchMode.TEST;
		}
		String provided = secret == null ? "" : secret;
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
			provided.getBytes(StandardCharsets.UTF_8));
	}
}
