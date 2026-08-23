package app.fuggs.bot.telegram;

import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.fuggs.bot.telegram.model.SendMessageRequest;
import app.fuggs.bot.telegram.model.TelegramDocument;
import app.fuggs.bot.telegram.model.TelegramMessage;
import app.fuggs.bot.telegram.model.TelegramPhotoSize;
import app.fuggs.bot.telegram.model.TelegramResponse;
import app.fuggs.bot.telegram.model.TelegramUpdate;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Polls Telegram for incoming updates.
 * <p>
 * Long polling is used instead of a webhook because it needs no publicly
 * reachable HTTPS endpoint, which makes local development possible without a
 * tunnel. A webhook transport is planned for production.
 * </p>
 * <p>
 * <b>Connectivity slice:</b> this currently only echoes what it received back
 * to the sender so the round trip can be verified. Member resolution and
 * document intake follow.
 * </p>
 */
@ApplicationScoped
public class TelegramPoller
{
	private static final Logger LOG = LoggerFactory.getLogger(TelegramPoller.class);

	@Inject
	TelegramConfig config;

	@RestClient
	TelegramClient client;

	/**
	 * Offset of the next update to fetch. Passing it back to Telegram
	 * acknowledges every lower update id, so they are not redelivered.
	 */
	private Long offset;

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
			LOG.debug("Ignoring update without message: updateId={}", update.updateId());
			return;
		}

		String username = message.from() != null ? message.from().username() : null;
		LOG.info("Telegram update received: updateId={}, chatId={}, from=@{}, {}",
			update.updateId(), message.chat().id(), username, describeContent(message));

		reply(message.chat().id(), describeContent(message));
	}

	/**
	 * Builds a short human-readable summary of what the message carried. Used
	 * for the echo reply while wiring up connectivity.
	 *
	 * @param message
	 *            the incoming message
	 * @return a summary such as {@code document Rechnung.pdf (application/pdf)}
	 */
	private String describeContent(TelegramMessage message)
	{
		TelegramDocument document = message.document();
		if (document != null)
		{
			return "document " + document.fileName() + " (" + document.mimeType()
				+ ", " + document.fileSize() + " bytes)";
		}

		TelegramPhotoSize photo = message.largestPhoto();
		if (photo != null)
		{
			return "photo " + photo.width() + "x" + photo.height()
				+ " (" + photo.fileSize() + " bytes)";
		}

		if (message.text() != null)
		{
			return "text \"" + message.text() + "\"";
		}

		return "unsupported message type";
	}

	private void reply(Long chatId, String content)
	{
		try
		{
			client.sendMessage(config.botToken().orElseThrow(),
				new SendMessageRequest(chatId, "Fuggs hat empfangen: " + content));
		}
		catch (Exception e)
		{
			LOG.error("Failed to send Telegram reply: chatId={}, error={}", chatId, e.getMessage(), e);
		}
	}
}
