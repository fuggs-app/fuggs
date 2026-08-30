package app.fuggs.bot.whatsapp;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the WhatsApp Cloud API integration.
 */
@ConfigMapping(prefix = "fuggs.whatsapp")
public interface WhatsAppConfig
{
	/**
	 * @return the permanent (or temporary, during setup) access token issued by
	 *         Meta, empty when unconfigured
	 */
	Optional<String> accessToken();

	/**
	 * @return the WhatsApp phone number id messages are sent from
	 */
	Optional<String> phoneNumberId();

	/**
	 * @return the app secret used to verify {@code X-Hub-Signature-256} on
	 *         incoming webhook payloads
	 */
	Optional<String> appSecret();

	/**
	 * @return the token this service expects back in {@code hub.verify_token}
	 *         during Meta's webhook subscription handshake
	 */
	Optional<String> verifyToken();

	/**
	 * @return whether the integration is active at all
	 */
	@WithDefault("false")
	boolean enabled();

	/**
	 * Returns whether the integration is both enabled and has everything it
	 * needs to talk to the Graph API.
	 *
	 * @return true when the bot can actually send messages and resolve media
	 */
	default boolean isUsable()
	{
		return enabled() && accessToken().filter(t -> !t.isBlank()).isPresent()
			&& phoneNumberId().filter(t -> !t.isBlank()).isPresent();
	}

	/**
	 * @return the {@code Authorization} header value for Graph API calls
	 */
	default String bearerToken()
	{
		return "Bearer " + accessToken().orElse("");
	}
}
