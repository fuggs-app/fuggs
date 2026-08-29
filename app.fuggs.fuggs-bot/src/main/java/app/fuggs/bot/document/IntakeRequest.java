package app.fuggs.bot.document;

/**
 * Request body for {@code POST /api/bot/documents} on fuggs-app. Field names
 * must match {@code BotDocumentResource.IntakeRequest} exactly for Jackson
 * (de)serialization on both ends.
 * <p>
 * {@code channel} plus {@code senderIdentifier} is deliberately generic rather
 * than e.g. {@code telegramUsername} - fuggs-app resolves the sender to a
 * {@code Member} differently per channel (Telegram username, a future WhatsApp
 * E.164 phone number, ...), and this pair is all it needs from any channel
 * adapter to do that.
 * </p>
 * <p>
 * {@code pushAddress} lets fuggs-app capture whatever it needs to message this
 * sender again later (e.g. Telegram's numeric chat id, which cannot be derived
 * from the username) - {@code null} when the channel has nothing to capture.
 * </p>
 */
public record IntakeRequest(String channel, String senderIdentifier, String pushAddress, String fileName,
	String contentType, String fileBase64)
{
}
