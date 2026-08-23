package app.fuggs.bot.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A file sent as a document, i.e. uncompressed. This is the good path for
 * ZugFerd PDFs, unlike photos which Telegram recompresses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramDocument(
	@JsonProperty("file_id") String fileId,
	@JsonProperty("file_name") String fileName,
	@JsonProperty("mime_type") String mimeType,
	@JsonProperty("file_size") Long fileSize)
{
}
