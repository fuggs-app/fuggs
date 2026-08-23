package app.fuggs.bot.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envelope every Telegram Bot API method returns.
 *
 * @param <T>
 *            the payload type of the wrapped {@code result} field
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramResponse<T>(boolean ok, T result, String description)
{
}
