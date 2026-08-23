package app.fuggs.bot.telegram.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An incoming message. Exactly one of {@code text}, {@code photo} or
 * {@code document} is relevant for us; everything else is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
	@JsonProperty("message_id") Long messageId,
	TelegramUser from,
	TelegramChat chat,
	String text,
	List<TelegramPhotoSize> photo,
	TelegramDocument document)
{
	/**
	 * Returns the largest available photo size, or null when this message
	 * carries no photo. Telegram orders the array smallest to largest.
	 *
	 * @return the best-quality photo size, or null
	 */
	public TelegramPhotoSize largestPhoto()
	{
		if (photo == null || photo.isEmpty())
		{
			return null;
		}
		return photo.get(photo.size() - 1);
	}

	/**
	 * @return true when the message carries a photo or a document
	 */
	public boolean hasAttachment()
	{
		return document != null || largestPhoto() != null;
	}
}
