package app.fuggs.bot.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One size variant of a photo. Telegram sends an array ordered smallest to
 * largest; the last entry is the best available quality.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramPhotoSize(
	@JsonProperty("file_id") String fileId,
	@JsonProperty("file_unique_id") String fileUniqueId,
	Integer width,
	Integer height,
	@JsonProperty("file_size") Long fileSize)
{
}
