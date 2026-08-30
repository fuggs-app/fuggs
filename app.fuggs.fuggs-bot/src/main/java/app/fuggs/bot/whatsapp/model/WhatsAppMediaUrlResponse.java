package app.fuggs.bot.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of {@code GET /{media-id}}. {@code url} is short-lived and must be
 * downloaded with the same bearer token, not treated as a public link.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppMediaUrlResponse(
	String url,
	@JsonProperty("mime_type") String mimeType,
	@JsonProperty("file_size") Long fileSize,
	String id)
{
}
