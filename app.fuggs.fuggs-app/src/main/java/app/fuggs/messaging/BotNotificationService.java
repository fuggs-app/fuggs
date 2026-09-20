package app.fuggs.messaging;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.fuggs.document.domain.Document;
import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Turns the three bot-facing events from issue #94 into LLM-generated German
 * text, and delivers the one that has to be sent later rather than returned
 * inline. Mirrors {@code app.fuggs.zugferd.service.TagGenerationService}'s
 * fallback shape: wrap the LLM call, log a warning and fall back to a plain
 * static sentence on failure rather than dropping the message - a member losing
 * their receipt confirmation is worse than one off-brand sentence.
 */
@ApplicationScoped
public class BotNotificationService
{
	private static final Logger LOG = LoggerFactory.getLogger(BotNotificationService.class);

	private static final String CHANNEL_WHATSAPP = "whatsapp";

	private static final String UNKNOWN_SENDER_FALLBACK = "Wir konnten dein Konto bei Fuggs nicht finden, "
		+ "wende dich bitte an deinen Bommelwart.";
	private static final String UPLOAD_ACKNOWLEDGED_FALLBACK = "Danke, dein Beleg wurde hochgeladen und wird "
		+ "von deinem Bommelwart geprüft.";
	private static final String TRANSACTION_BOOKED_FALLBACK = "Dein Beleg wurde gerade bearbeitet. "
		+ "Damit ist für dich alles erledigt!";

	@Inject
	BotMessageService botMessageService;

	@Inject
	ObjectMapper objectMapper;

	@Inject
	MemberRepository memberRepository;

	@RestClient
	FuggsBotClient fuggsBotClient;

	/**
	 * AC #1: text for a submission whose sender could not be matched to a
	 * member.
	 */
	public String unknownSenderMessage()
	{
		try
		{
			return botMessageService.unknownSenderMessage();
		}
		catch (Exception e)
		{
			LOG.warn("Failed to generate unknown-sender message via LLM, using fallback", e);
			return UNKNOWN_SENDER_FALLBACK;
		}
	}

	/**
	 * AC #2: text acknowledging a completed upload, naming what was on the
	 * receipt when analysis extracted it.
	 *
	 * @param document
	 *            the analyzed document - only its already-extracted fields
	 *            (vendor, total, currency) are used, nothing is re-derived
	 * @param analysisFailed
	 *            whether automatic analysis failed for this document
	 */
	public String uploadAcknowledgedMessage(Document document, boolean analysisFailed)
	{
		try
		{
			String vendorName = document.getSenderName();
			Map<String, Object> facts = Map.of(
				"vendorName", vendorName == null || vendorName.isBlank() ? "unbekannt" : vendorName,
				"total", document.getTotal() != null ? document.getTotal().toPlainString() : "unbekannt",
				"currencyCode", document.getCurrencyCode() != null ? document.getCurrencyCode() : "unbekannt",
				"analysisFailed", analysisFailed);
			return botMessageService.uploadAcknowledgedMessage(objectMapper.writeValueAsString(facts));
		}
		catch (JsonProcessingException e)
		{
			LOG.warn("Failed to serialize facts for upload-acknowledged message, using fallback", e);
			return UPLOAD_ACKNOWLEDGED_FALLBACK;
		}
		catch (Exception e)
		{
			LOG.warn("Failed to generate upload-acknowledged message via LLM, using fallback", e);
			return UPLOAD_ACKNOWLEDGED_FALLBACK;
		}
	}

	/**
	 * AC #3: proactively notifies the original uploader that their document
	 * became a booked transaction. Best-effort and silent on any failure -
	 * booking a transaction must never fail because a member can't be notified
	 * (unknown uploader, uploader never used the bot, LLM down, fuggs-bot
	 * unreachable).
	 *
	 * @param document
	 *            the document that was just turned into a transaction
	 * @param processedByDisplayName
	 *            the display name of whoever booked it, or {@code null} if
	 *            unavailable
	 */
	public void notifyTransactionBooked(Document document, String processedByDisplayName)
	{
		try
		{
			Member uploader = memberRepository.findByUsername(document.getUploadedBy());
			NotificationTarget target = uploader != null ? resolveNotificationTarget(uploader) : null;
			if (target == null)
			{
				LOG.debug("Skipping transaction-booked notification, uploader not reachable: documentId={}",
					document.getId());
				return;
			}

			String message = transactionBookedMessage(document, processedByDisplayName);
			fuggsBotClient.sendNotification(
				new FuggsBotClient.NotificationRequest(target.channel(), target.recipientId(), message));

			LOG.info("Sent transaction-booked notification: documentId={}, member={}, channel={}",
				document.getId(), uploader.getUserName(), target.channel());
		}
		catch (Exception e)
		{
			LOG.error("Failed to send transaction-booked notification: documentId={}, error={}",
				document.getId(), e.getMessage(), e);
		}
	}

	private record NotificationTarget(String channel, String recipientId)
	{
	}

	/**
	 * Picks which channel to push a proactive notification through. Only
	 * WhatsApp exists today; a future second channel would add its own check
	 * here, in the same preference-order shape.
	 */
	private NotificationTarget resolveNotificationTarget(Member member)
	{
		if (member.getWhatsappPhoneE164() != null)
		{
			return new NotificationTarget(CHANNEL_WHATSAPP, member.getWhatsappPhoneE164());
		}
		return null;
	}

	private String transactionBookedMessage(Document document, String processedByDisplayName)
	{
		try
		{
			String vendorName = document.getSenderName();
			Map<String, Object> facts = new java.util.HashMap<>();
			facts.put("vendorName", vendorName == null || vendorName.isBlank() ? "unbekannt" : vendorName);
			facts.put("uploadedRelativeTime", relativeTimePhrase(document));
			facts.put("processedByName", processedByDisplayName != null ? processedByDisplayName : "dein Bommelwart");
			return botMessageService.transactionBookedMessage(objectMapper.writeValueAsString(facts));
		}
		catch (Exception e)
		{
			LOG.warn("Failed to generate transaction-booked message via LLM, using fallback: documentId={}",
				document.getId(), e);
			return TRANSACTION_BOOKED_FALLBACK;
		}
	}

	/**
	 * Renders {@code document.getCreatedAt()} as a German relative-time phrase
	 * ("heute", "gestern", "vorgestern", "vor 5 Tagen"). Computed here rather
	 * than left to the LLM, since date arithmetic is exactly the kind of thing
	 * a language model gets subtly wrong.
	 */
	private String relativeTimePhrase(Document document)
	{
		LocalDate uploadedOn = document.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
		long daysAgo = ChronoUnit.DAYS.between(uploadedOn, LocalDate.now());
		return switch ((int)Math.max(daysAgo, 0))
		{
			case 0 -> "heute";
			case 1 -> "gestern";
			case 2 -> "vorgestern";
			default -> "vor " + daysAgo + " Tagen";
		};
	}
}
