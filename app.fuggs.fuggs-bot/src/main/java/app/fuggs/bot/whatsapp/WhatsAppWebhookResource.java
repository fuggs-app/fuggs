package app.fuggs.bot.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import app.fuggs.bot.document.IntakeOutcome;
import app.fuggs.bot.document.ReceiptIntakeService;
import app.fuggs.bot.whatsapp.model.WhatsAppChange;
import app.fuggs.bot.whatsapp.model.WhatsAppEntry;
import app.fuggs.bot.whatsapp.model.WhatsAppMedia;
import app.fuggs.bot.whatsapp.model.WhatsAppMediaUrlResponse;
import app.fuggs.bot.whatsapp.model.WhatsAppMessage;
import app.fuggs.bot.whatsapp.model.WhatsAppSendMessageRequest;
import app.fuggs.bot.whatsapp.model.WhatsAppWebhookPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Receives WhatsApp Cloud API webhook events and forwards received receipts
 * into the fuggs-app document/bill pipeline via {@link ReceiptIntakeService}.
 * <p>
 * A webhook is used rather than long polling because Meta's Cloud API offers no
 * polling transport - it only ever pushes to a subscribed, publicly reachable
 * HTTPS URL. In dev this requires a tunnel (e.g. {@code
 * cloudflared}); see {@code docs/plan-whatsapp-bot.md}.
 * </p>
 * <p>
 * This class only handles WhatsApp transport - verifying the webhook,
 * downloading attachments, sending replies - and WhatsApp-specific wording. The
 * actual submit-and-poll business logic lives in {@link ReceiptIntakeService},
 * which knows nothing about WhatsApp - a future second channel would add its
 * own adapter following this same shape rather than touching that service.
 * </p>
 */
@Path("/api/whatsapp/webhook")
@ApplicationScoped
public class WhatsAppWebhookResource
{
	private static final Logger LOG = LoggerFactory.getLogger(WhatsAppWebhookResource.class);

	/**
	 * The channel identifier fuggs-app's {@code BotDocumentResource} resolves
	 * members by.
	 */
	private static final String CHANNEL = "whatsapp";

	private static final String HUB_MODE_SUBSCRIBE = "subscribe";
	private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
	private static final String SIGNATURE_PREFIX = "sha256=";
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	/** Bounded LRU of recently processed WhatsApp message ids, for dedup. */
	private static final int MAX_TRACKED_MESSAGE_IDS = 500;

	/**
	 * Not a WhatsApp limit (documents there allow up to 100 MB) but our own
	 * downstream constraint - {@code az-document-ai} is configured with
	 * {@code quarkus.http.limits.max-body-size=4M}.
	 */
	private static final long MAX_FILE_SIZE_BYTES = 4L * 1024 * 1024;

	private static final String FILE_TOO_LARGE_MESSAGE = "Diese Datei ist zu groß (max. 4 MB). "
		+ "Bitte sende den Beleg als kleinere Datei.";
	private static final String DOWNLOAD_FAILED_MESSAGE = "Der Beleg konnte nicht von WhatsApp heruntergeladen "
		+ "werden. Bitte versuche es erneut.";
	private static final String GENERIC_FAILURE_MESSAGE = "Beim Verarbeiten des Belegs ist ein Fehler "
		+ "aufgetreten. Bitte versuche es später erneut oder lade den Beleg direkt in Fuggs hoch.";
	private static final String STILL_PROCESSING_MESSAGE = "Der Beleg wird noch analysiert. Du bekommst "
		+ "noch keine Bestätigung, kannst den Beleg aber bereits in Fuggs sehen.";

	private final Set<String> processedMessageIds = Collections.newSetFromMap(
		Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(16, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest)
			{
				return size() > MAX_TRACKED_MESSAGE_IDS;
			}
		}));

	@Inject
	WhatsAppConfig config;

	@Inject
	WhatsAppMediaDownloader mediaDownloader;

	@Inject
	ReceiptIntakeService receiptIntakeService;

	@Inject
	ObjectMapper objectMapper;

	@RestClient
	WhatsAppClient client;

	/**
	 * Meta's webhook subscription handshake: echoes {@code hub.challenge} when
	 * {@code hub.verify_token} matches the configured value.
	 */
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public Response verify(
		@QueryParam("hub.mode") String mode,
		@QueryParam("hub.verify_token") String verifyToken,
		@QueryParam("hub.challenge") String challenge)
	{
		String expected = config.verifyToken().orElse("");
		if (!expected.isBlank() && HUB_MODE_SUBSCRIBE.equals(mode) && expected.equals(verifyToken))
		{
			return Response.ok(challenge).build();
		}
		LOG.warn("Rejected WhatsApp webhook verification attempt: mode={}", mode);
		return Response.status(Response.Status.FORBIDDEN).build();
	}

	/**
	 * Receives a batch of webhook events. Always acks with {@code 200} once the
	 * signature is verified, then processes attachments asynchronously - this
	 * endpoint triggers S3 writes and paid LLM calls, so replying slowly would
	 * make Meta redeliver on top of an already-running submission.
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response receive(@HeaderParam(SIGNATURE_HEADER) String signatureHeader, String rawBody)
	{
		if (!isValidSignature(signatureHeader, rawBody))
		{
			LOG.warn("Rejected WhatsApp webhook payload with invalid signature");
			return Response.status(Response.Status.UNAUTHORIZED).build();
		}

		WhatsAppWebhookPayload payload;
		try
		{
			payload = objectMapper.readValue(rawBody, WhatsAppWebhookPayload.class);
		}
		catch (Exception e)
		{
			LOG.warn("Failed to parse WhatsApp webhook payload: {}", e.getMessage());
			// Ack anyway - a malformed payload from Meta isn't worth a retry
			// storm.
			return Response.ok().build();
		}

		for (WhatsAppEntry entry : orEmpty(payload.entry()))
		{
			for (WhatsAppChange change : orEmpty(entry.changes()))
			{
				if (change.value() != null)
				{
					for (WhatsAppMessage message : orEmpty(change.value().messages()))
					{
						handleMessage(message);
					}
				}
			}
		}
		return Response.ok().build();
	}

	private void handleMessage(WhatsAppMessage message)
	{
		if (message.id() != null && isDuplicate(message.id()))
		{
			LOG.debug("Ignoring duplicate WhatsApp message: id={}", message.id());
			return;
		}

		WhatsAppMedia media = message.attachment();
		if (media == null)
		{
			LOG.debug("Ignoring WhatsApp message without a supported attachment: id={}, type={}",
				message.id(), message.type());
			return;
		}

		String from = message.from();
		LOG.info("WhatsApp attachment received: id={}, from={}, mediaId={}", message.id(), from, media.id());

		Thread.ofVirtual().name("whatsapp-intake-" + message.id())
			.start(() -> processAttachment(from, media));
	}

	/**
	 * Resolves the media URL, downloads the attachment, forwards it to
	 * fuggs-app via {@link ReceiptIntakeService}, and replies with the outcome.
	 * Runs on a background virtual thread so the webhook response isn't held up
	 * while analysis completes.
	 */
	private void processAttachment(String from, WhatsAppMedia media)
	{
		try
		{
			WhatsAppMediaUrlResponse mediaInfo;
			try
			{
				mediaInfo = client.getMediaUrl(media.id(), config.bearerToken());
			}
			catch (Exception e)
			{
				LOG.error("Failed to resolve WhatsApp media URL: mediaId={}, error={}", media.id(), e.getMessage(),
					e);
				reply(from, DOWNLOAD_FAILED_MESSAGE);
				return;
			}

			if (mediaInfo == null || mediaInfo.url() == null)
			{
				LOG.warn("WhatsApp getMediaUrl returned no URL: mediaId={}", media.id());
				reply(from, DOWNLOAD_FAILED_MESSAGE);
				return;
			}

			if (mediaInfo.fileSize() != null && mediaInfo.fileSize() > MAX_FILE_SIZE_BYTES)
			{
				reply(from, FILE_TOO_LARGE_MESSAGE);
				return;
			}

			byte[] content;
			try
			{
				content = mediaDownloader.download(mediaInfo.url());
			}
			catch (Exception e)
			{
				LOG.error("Failed to download WhatsApp media: mediaId={}, error={}", media.id(), e.getMessage(), e);
				reply(from, DOWNLOAD_FAILED_MESSAGE);
				return;
			}

			IntakeOutcome outcome = receiptIntakeService.submit(CHANNEL, from, null, content, fileNameFor(media),
				media.mimeType());
			reply(from, toReplyText(outcome));
		}
		catch (Exception e)
		{
			LOG.error("Unexpected error while processing WhatsApp attachment: from={}, error={}", from,
				e.getMessage(), e);
			reply(from, GENERIC_FAILURE_MESSAGE);
		}
	}

	/**
	 * WhatsApp documents carry the original {@code filename}; images do not -
	 * they only ever arrive as a plain image attachment, never named.
	 */
	private String fileNameFor(WhatsAppMedia media)
	{
		if (media.filename() != null && !media.filename().isBlank())
		{
			return media.filename();
		}
		return "image/jpeg".equals(media.mimeType()) ? "beleg.jpg" : "beleg";
	}

	/**
	 * Words the channel-agnostic {@link IntakeOutcome} into the WhatsApp reply.
	 * For {@code Success}/{@code AnalysisFailed}/{@code UnknownSender} that's
	 * just relaying fuggs-app's LLM-generated text verbatim; the two pure-infra
	 * outcomes fall back to this adapter's own static text.
	 */
	private String toReplyText(IntakeOutcome outcome)
	{
		return switch (outcome)
		{
			case IntakeOutcome.Success success -> success.message();
			case IntakeOutcome.AnalysisFailed failed -> failed.message();
			case IntakeOutcome.UnknownSender unknown -> unknown.message();
			case IntakeOutcome.StillProcessing ignored -> STILL_PROCESSING_MESSAGE;
			case IntakeOutcome.SubmissionFailed ignored -> GENERIC_FAILURE_MESSAGE;
		};
	}

	private void reply(String to, String text)
	{
		try
		{
			client.sendMessage(config.phoneNumberId().orElseThrow(), config.bearerToken(),
				new WhatsAppSendMessageRequest(to, text));
		}
		catch (Exception e)
		{
			LOG.error("Failed to send WhatsApp reply: to={}, error={}", to, e.getMessage(), e);
		}
	}

	private boolean isDuplicate(String messageId)
	{
		return !processedMessageIds.add(messageId);
	}

	/**
	 * Verifies the {@code X-Hub-Signature-256} header - an HMAC-SHA256 of the
	 * raw request body keyed with the app secret. This endpoint is publicly
	 * reachable and triggers S3 writes plus paid LLM calls, so an unconfigured
	 * secret fails closed (rejects every payload) rather than skipping
	 * verification.
	 */
	private boolean isValidSignature(String signatureHeader, String rawBody)
	{
		Optional<String> secret = config.appSecret().filter(s -> !s.isBlank());
		if (secret.isEmpty() || signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX))
		{
			return false;
		}
		try
		{
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.get().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
			String computedHex = HexFormat.of().formatHex(computed);
			String providedHex = signatureHeader.substring(SIGNATURE_PREFIX.length());
			return MessageDigest.isEqual(
				computedHex.getBytes(StandardCharsets.UTF_8),
				providedHex.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException e)
		{
			LOG.error("Failed to verify WhatsApp webhook signature: {}", e.getMessage(), e);
			return false;
		}
	}

	private <T> List<T> orEmpty(List<T> list)
	{
		return list != null ? list : List.of();
	}
}
