package app.fuggs.bot.telegram;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.fuggs.bot.telegram.model.TelegramResponse;
import app.fuggs.bot.telegram.model.TelegramUser;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Verifies on startup that the configured bot token is valid and the Telegram
 * API is reachable, and exposes the same check for on-demand use.
 */
@ApplicationScoped
public class TelegramConnectivityService
{
	private static final Logger LOG = LoggerFactory.getLogger(TelegramConnectivityService.class);

	@Inject
	TelegramConfig config;

	@RestClient
	TelegramClient client;

	void onStart(@Observes StartupEvent event)
	{
		if (!config.enabled())
		{
			LOG.info("Telegram integration disabled (fuggs.telegram.enabled=false)");
			return;
		}

		if (!config.isUsable())
		{
			LOG.warn("Telegram integration enabled but no bot token configured "
				+ "- set FUGGS_TELEGRAM_BOT_TOKEN");
			return;
		}

		try
		{
			TelegramUser bot = whoAmI();
			LOG.info("Telegram connection OK: bot @{} (id={}, name={})",
				bot.username(), bot.id(), bot.firstName());
		}
		catch (Exception e)
		{
			// Never fail startup over this - the web app must still boot.
			LOG.error("Telegram connection FAILED: {}", e.getMessage(), e);
		}
	}

	/**
	 * Calls getMe and returns the bot's own user record.
	 *
	 * @return the bot identity as reported by Telegram
	 * @throws IllegalStateException
	 *             when no token is configured or Telegram rejects the call
	 */
	public TelegramUser whoAmI()
	{
		String token = config.botToken()
			.filter(t -> !t.isBlank())
			.orElseThrow(() -> new IllegalStateException("No Telegram bot token configured"));

		TelegramResponse<TelegramUser> response = client.getMe(token);
		if (response == null || !response.ok() || response.result() == null)
		{
			String description = response != null ? response.description() : "empty response";
			throw new IllegalStateException("Telegram getMe failed: " + description);
		}
		return response.result();
	}
}
