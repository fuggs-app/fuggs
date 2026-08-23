package app.fuggs.bot.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Telegram user or bot. Note that {@code username} is optional in Telegram
 * and is absent for users who never set one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUser(
	Long id,
	@JsonProperty("is_bot") Boolean isBot,
	@JsonProperty("first_name") String firstName,
	@JsonProperty("last_name") String lastName,
	String username)
{
}
