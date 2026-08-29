package app.fuggs.bot.telegram;

import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.fuggs.bot.document.IntakeOutcome;
import app.fuggs.bot.document.ReceiptIntakeService;
import app.fuggs.bot.telegram.model.SendMessageRequest;
import app.fuggs.bot.telegram.model.TelegramDocument;
import app.fuggs.bot.telegram.model.TelegramFile;
import app.fuggs.bot.telegram.model.TelegramMessage;
import app.fuggs.bot.telegram.model.TelegramPhotoSize;
import app.fuggs.bot.telegram.model.TelegramResponse;
import app.fuggs.bot.telegram.model.TelegramUpdate;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Polls Telegram for incoming updates and forwards received receipts into the
 * fuggs-app document/bill pipeline via {@link ReceiptIntakeService}.
 * <p>
 * Long polling is used instead of a webhook because it needs no publicly
 * reachable HTTPS endpoint, which makes local development possible without a
 * tunnel. A webhook transport is planned for production.
 * </p>
 * <p>
 * This class only handles Telegram transport - reading updates, downloading
 * attachments, sending replies - and Telegram-specific wording. The actual
 * submit-and-poll business logic lives in {@link ReceiptIntakeService}, which
 * knows nothing about Telegram; a future channel (e.g. WhatsApp) would add its
 * own adapter following this same shape rather than touching that service.
 * </p>
 */
@ApplicationScoped
public class TelegramPoller
{
	private static final Logger LOG = LoggerFactory.getLogger(TelegramPoller.class);

	/**
	 * The channel identifier fuggs-app's {@code BotDocumentResource} resolves
	 * members by.
	 */
	private static final String CHANNEL = "telegram";

	/** Telegram bots cannot download files larger than this. */
	private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

	/**
	 * Not covered by issue #94's "every message is LLM-generated" AC - this
	 * fires before fuggs-app is ever contacted (no username to look up at all),
	 * so there are no business facts to hand an LLM yet.
	 */
	private static final String NO_USERNAME_MESSAGE = "Bitte lege in Telegram einen Benutzernamen fest "
		+ "(Einstellungen -> Benutzername), damit Fuggs dich einem Mitglied zuordnen kann.";

	/**
	 * These three are pure Telegram-transport/infra conditions - a file too big
	 * to download, a failed download, fuggs-app unreachable - not business
	 * messages, so they stay static per the documented LLM-fallback decision in
	 * {@code docs/plan-telegram-bot.md}. Every message the three ACs actually
	 * cover (unknown sender, upload ack, transaction booked) comes from
	 * fuggs-app already LLM-generated via {@link IntakeOutcome}.
	 */
	private static final String FILE_TOO_LARGE_MESSAGE = "Diese Datei ist zu groß (max. 20 MB). "
		+ "Bitte sende den Beleg als kleinere Datei.";
	private static final String DOWNLOAD_FAILED_MESSAGE = "Der Beleg konnte nicht von Telegram heruntergeladen "
		+ "werden. Bitte versuche es erneut.";
	private static final String GENERIC_FAILURE_MESSAGE = "Beim Verarbeiten des Belegs ist ein Fehler "
		+ "aufgetreten. Bitte versuche es später erneut oder lade den Beleg direkt in Fuggs hoch.";
	private static final String STILL_PROCESSING_MESSAGE = "Der Beleg wird noch analysiert. Du bekommst "
		+ "noch keine Bestätigung, kannst den Beleg aber bereits in Fuggs sehen.";

	@Inject
	TelegramConfig config;

	@Inject
	TelegramFileDownloader fileDownloader;

	@Inject
	ReceiptIntakeService receiptIntakeService;

	@RestClient
	TelegramClient client;

	/**
	 * Offset of the next update to fetch. Passing it back to Telegram
	 * acknowledges every lower update id, so they are not redelivered.
	 */
	private Long offset;

	private record IncomingFile(String fileId, String fileName, String contentType, Long fileSize)
	{
	}

	@Scheduled(every = "${fuggs.telegram.poll-interval:3s}", concurrentExecution = ConcurrentExecution.SKIP)
	void scheduledPoll()
	{
		if (!config.isUsable())
		{
			return;
		}
		pollOnce();
	}

	/**
	 * Fetches and handles one batch of pending updates. Separated from the
	 * schedule so it can be driven directly, and so the enablement check stays
	 * a scheduling concern.
	 */
	public void pollOnce()
	{
		try
		{
			TelegramResponse<List<TelegramUpdate>> response = client.getUpdates(
				config.botToken().orElseThrow(), offset, config.pollTimeoutSeconds());

			if (response == null || !response.ok() || response.result() == null)
			{
				LOG.warn("Telegram getUpdates failed: {}",
					response != null ? response.description() : "empty response");
				return;
			}

			for (TelegramUpdate update : response.result())
			{
				handle(update);
				offset = update.updateId() + 1;
			}
		}
		catch (Exception e)
		{
			LOG.error("Telegram polling error: {}", e.getMessage(), e);
		}
	}

	private void handle(TelegramUpdate update)
	{
		TelegramMessage message = update.message();
		if (message == null || message.chat() == null)
		{
			LOG.debug("Ignoring update without a message: updateId={}", update.updateId());
			return;
		}

		Long chatId = message.chat().id();
		IncomingFile file = extractFile(message);
		if (file == null)
		{
			LOG.debug("Ignoring update without a supported attachment: updateId={}", update.updateId());
			return;
		}

		String username = message.from() != null ? message.from().username() : null;
		if (username == null || username.isBlank())
		{
			LOG.info("Telegram attachment from a user without a username: updateId={}, chatId={}",
				update.updateId(), chatId);
			reply(chatId, NO_USERNAME_MESSAGE);
			return;
		}

		LOG.info("Telegram attachment received: updateId={}, chatId={}, from=@{}, file={}",
			update.updateId(), chatId, username, file.fileName());

		Thread.ofVirtual().name("telegram-intake-" + update.updateId())
			.start(() -> processAttachment(chatId, username, file));
	}

	/**
	 * Picks the relevant attachment off a message: a document (preferred,
	 * uncompressed) or the largest available photo size. Telegram recompresses
	 * photos, which costs OCR accuracy.
	 */
	private IncomingFile extractFile(TelegramMessage message)
	{
		TelegramDocument document = message.document();
		if (document != null)
		{
			return new IncomingFile(document.fileId(), document.fileName(), document.mimeType(),
				document.fileSize());
		}

		TelegramPhotoSize photo = message.largestPhoto();
		if (photo != null)
		{
			return new IncomingFile(photo.fileId(), "photo.jpg", "image/jpeg", photo.fileSize());
		}

		return null;
	}

	/**
	 * Downloads the attachment, forwards it to fuggs-app via
	 * {@link ReceiptIntakeService}, and replies with the outcome. Runs on a
	 * background virtual thread so the poll loop isn't blocked while analysis
	 * completes.
	 */
	private void processAttachment(Long chatId, String username, IncomingFile file)
	{
		try
		{
			if (file.fileSize() != null && file.fileSize() > MAX_FILE_SIZE_BYTES)
			{
				reply(chatId, FILE_TOO_LARGE_MESSAGE);
				return;
			}

			byte[] content = downloadFile(file.fileId());
			if (content == null)
			{
				reply(chatId, DOWNLOAD_FAILED_MESSAGE);
				return;
			}

			IntakeOutcome outcome = receiptIntakeService.submit(CHANNEL, username, String.valueOf(chatId), content,
				file.fileName(), file.contentType());
			reply(chatId, toReplyText(outcome));
		}
		catch (Exception e)
		{
			LOG.error("Unexpected error while processing Telegram attachment: chatId={}, error={}",
				chatId, e.getMessage(), e);
			reply(chatId, GENERIC_FAILURE_MESSAGE);
		}
	}

	private byte[] downloadFile(String fileId)
	{
		try
		{
			TelegramResponse<TelegramFile> fileResponse = client.getFile(config.botToken().orElseThrow(), fileId);
			if (fileResponse == null || !fileResponse.ok() || fileResponse.result() == null
				|| fileResponse.result().filePath() == null)
			{
				LOG.warn("Telegram getFile failed: fileId={}, description={}", fileId,
					fileResponse != null ? fileResponse.description() : "empty response");
				return null;
			}
			return fileDownloader.download(fileResponse.result().filePath());
		}
		catch (Exception e)
		{
			LOG.error("Failed to download Telegram file: fileId={}, error={}", fileId, e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Words the channel-agnostic {@link IntakeOutcome} into the Telegram reply.
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

	private void reply(Long chatId, String text)
	{
		try
		{
			client.sendMessage(config.botToken().orElseThrow(), new SendMessageRequest(chatId, text));
		}
		catch (Exception e)
		{
			LOG.error("Failed to send Telegram reply: chatId={}, error={}", chatId, e.getMessage(), e);
		}
	}
}
