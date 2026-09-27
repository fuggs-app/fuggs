package app.fuggs.bot.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code image} or {@code document} object embedded in an incoming message.
 * {@code id} is the media id used to resolve a download URL via {@code GET
 * /{media-id}}; the actual bytes and size are not known until then.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppMedia(
	String id,
	@JsonProperty("mime_type") String mimeType,
	String filename,
	String caption)
{
}
