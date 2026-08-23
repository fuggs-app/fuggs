package app.fuggs.bot.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One polled or delivered update. {@code updateId} is the deduplication key and
 * the basis for the polling offset.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
	@JsonProperty("update_id") Long updateId,
	TelegramMessage message)
{
}
