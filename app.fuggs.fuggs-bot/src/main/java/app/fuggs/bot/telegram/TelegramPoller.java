package app.fuggs.bot.telegram;

import java.util.Base64;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.fuggs.bot.document.FuggsAppClient;
import app.fuggs.bot.document.IntakeRequest;
import app.fuggs.bot.document.IntakeResponse;
import app.fuggs.bot.document.StatusResponse;
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
import jakarta.ws.rs.WebApplicationException;

/**
 * Polls Telegram for incoming updates and forwards received receipts into the
 * fuggs-app document/bill pipeline via {@link FuggsAppClient}.
 * <p>
 * Long polling is used instead of a webhook because it needs no publicly
 * reachable HTTPS endpoint, which makes local development possible without a
 * tunnel. A webhook transport is planned for production.
 * </p>
 */
@ApplicationScoped
public class TelegramPoller
{
	private static final Logger LOG = LoggerFactory.getLogger(TelegramPoller.class);

	/** Telegram bots cannot download files larger than this. */
	private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

	private static final int STATUS_POLL_INTERVAL_MS = 2000;
	private static final int STATUS_POLL_MAX_ATTEMPTS = 15;

	private static final String NO_USERNAME_MESSAGE = "Bitte lege in Telegram einen Benutzernamen fest "
		+ "(Einstellungen -> Benutzername), damit Fuggs dich einem Mitglied zuordnen kann.";
	private static final String UNKNOWN_SENDER_MESSAGE = "Dein Telegram-Konto ist noch keinem Fuggs-Mitglied "
		+ "zugeordnet. Bitte deinen Bommelwart, deinen Telegram-Benutzernamen in den Stammdaten zu hinterlegen.";
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

	@RestClient
	TelegramClient client;

	@RestClient
	FuggsAppClient fuggsAppClient;

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
	 * Downloads the attachment, forwards it to fuggs-app, and replies with the
	 * outcome. Runs on a background virtual thread so the poll loop isn't
	 * blocked while analysis completes.
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

			Long documentId = submit(chatId, username, content, file);
			if (documentId == null)
			{
				// submit() already replied with the specific failure reason
				return;
			}

			pollAndReply(chatId, documentId);
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
	 * Submits the file to fuggs-app. Returns the created document id, or
	 * {@code null} after already sending the appropriate reply on failure.
	 */
	private Long submit(Long chatId, String username, byte[] content, IncomingFile file)
	{
		try
		{
			String fileBase64 = Base64.getEncoder().encodeToString(content);
			IntakeResponse response = fuggsAppClient.submitDocument(
				new IntakeRequest(username, file.fileName(), file.contentType(), fileBase64));
			return response.documentId();
		}
		catch (WebApplicationException e)
		{
			int status = e.getResponse() != null ? e.getResponse().getStatus() : -1;
			if (status == 404)
			{
				reply(chatId, UNKNOWN_SENDER_MESSAGE);
			}
			else
			{
				LOG.error("fuggs-app rejected document submission: chatId={}, status={}", chatId, status, e);
				reply(chatId, GENERIC_FAILURE_MESSAGE);
			}
			return null;
		}
		catch (Exception e)
		{
			LOG.error("Failed to submit document to fuggs-app: chatId={}, error={}", chatId, e.getMessage(), e);
			reply(chatId, GENERIC_FAILURE_MESSAGE);
			return null;
		}
	}

	/**
	 * Polls the analysis status until it completes or the attempt budget is
	 * exhausted, then sends the matching reply.
	 */
	private void pollAndReply(Long chatId, Long documentId)
	{
		for (int attempt = 0; attempt < STATUS_POLL_MAX_ATTEMPTS; attempt++)
		{
			StatusResponse status;
			try
			{
				status = fuggsAppClient.getStatus(documentId);
			}
			catch (Exception e)
			{
				LOG.error("Failed to check document status: documentId={}, error={}", documentId,
					e.getMessage(), e);
				reply(chatId, GENERIC_FAILURE_MESSAGE);
				return;
			}

			if (status.complete())
			{
				reply(chatId, status.error() == null || status.error().isBlank()
					? buildSuccessMessage(status)
					: buildFailureMessage(status));
				return;
			}

			sleep(STATUS_POLL_INTERVAL_MS);
		}

		LOG.info("Document analysis still running after polling budget: documentId={}", documentId);
		reply(chatId, STILL_PROCESSING_MESSAGE);
	}

	private String buildSuccessMessage(StatusResponse status)
	{
		StringBuilder message = new StringBuilder("Beleg erfolgreich verarbeitet");
		if (status.name() != null && !status.name().isBlank())
		{
			message.append(": ").append(status.name());
		}
		if (status.total() != null)
		{
			message.append(" (").append(status.total());
			if (status.currencyCode() != null && !status.currencyCode().isBlank())
			{
				message.append(" ").append(status.currencyCode());
			}
			message.append(")");
		}
		message.append(". Du kannst ihn in Fuggs prüfen und bestätigen.");
		return message.toString();
	}

	private String buildFailureMessage(StatusResponse status)
	{
		String detail = status.error() != null && !status.error().isBlank() ? ": " + status.error() : "";
		return "Die Analyse des Belegs ist fehlgeschlagen" + detail
			+ ". Bitte lade den Beleg direkt in Fuggs hoch und fülle die Daten manuell aus.";
	}

	private void sleep(long millis)
	{
		try
		{
			Thread.sleep(millis);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
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
