package app.fuggs.bot.telegram;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Telegram bot integration.
 */
@ConfigMapping(prefix = "fuggs.telegram")
public interface TelegramConfig
{
	/**
	 * @return the bot token issued by BotFather, empty when unconfigured
	 */
	Optional<String> botToken();

	/**
	 * @return whether the integration is active at all
	 */
	@WithDefault("false")
	boolean enabled();

	/**
	 * Interval between poll cycles, as a Quarkus duration such as {@code 3s}.
	 * Declared here because {@code @ConfigMapping} validates every property
	 * under its prefix, even ones only referenced from a {@code @Scheduled}
	 * expression.
	 *
	 * @return the poll interval
	 */
	@WithDefault("3s")
	String pollInterval();

	/**
	 * @return seconds the poller holds the getUpdates connection open
	 */
	@WithDefault("0")
	int pollTimeoutSeconds();

	/**
	 * Returns whether the integration is both enabled and has a usable token.
	 *
	 * @return true when the bot can actually talk to Telegram
	 */
	default boolean isUsable()
	{
		return enabled() && botToken().filter(t -> !t.isBlank()).isPresent();
	}
}
