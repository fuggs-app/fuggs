package app.fuggs.bot.telegram.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code sendMessage}. A private chat can only be addressed by
 * its numeric chat id — a Telegram username is not accepted here.
 */
public record SendMessageRequest(
	@JsonProperty("chat_id") Long chatId,
	String text)
{
}
