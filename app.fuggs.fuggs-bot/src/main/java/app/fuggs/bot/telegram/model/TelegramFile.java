package app.fuggs.bot.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of {@code getFile}. {@code filePath} is combined with the bot token to
 * build the download URL
 * {@code https://api.telegram.org/file/bot<token>/<filePath>}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramFile(
	@JsonProperty("file_id") String fileId,
	@JsonProperty("file_size") Long fileSize,
	@JsonProperty("file_path") String filePath)
{
}
