package app.fuggs.bot.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An incoming message. {@code from} is the sender's phone number in E.164
 * without a leading '+', which doubles as both the member-lookup key and the
 * address to reply to. Exactly one of {@code image} or {@code document} is
 * relevant for us; every other {@code type} (text, audio, sticker, location,
 * ...) is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppMessage(
	String from,
	String id,
	String type,
	WhatsAppMedia image,
	WhatsAppMedia document)
{
	/**
	 * Picks the relevant attachment off this message: a document (preferred,
	 * arrives uncompressed) or an image.
	 *
	 * @return the attachment, or {@code null} when this message carries neither
	 */
	public WhatsAppMedia attachment()
	{
		return document != null ? document : image;
	}
}
