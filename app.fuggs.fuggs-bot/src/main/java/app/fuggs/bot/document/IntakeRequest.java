package app.fuggs.bot.document;

/**
 * Request body for {@code POST /api/bot/documents} on fuggs-app. Field names
 * must match {@code BotDocumentResource.IntakeRequest} exactly for Jackson
 * (de)serialization on both ends.
 * <p>
 * {@code channel} plus {@code senderIdentifier} is deliberately generic rather
 * than tying the shape to one channel's identifier - fuggs-app resolves the
 * sender to a {@code Member} differently per channel (WhatsApp's E.164 phone
 * number today), and this pair is all it needs from any channel adapter to do
 * that.
 * </p>
 * <p>
 * {@code pushAddress} is a reserved extension point: it lets fuggs-app capture
 * whatever a channel needs to message this sender again later (e.g. a numeric
 * chat id that can't be derived from the sender identifier alone) -
 * {@code null} when the channel has nothing to capture, which is the case for
 * every channel implemented today.
 * </p>
 */
public record IntakeRequest(String channel, String senderIdentifier, String pushAddress, String fileName,
	String contentType, String fileBase64)
{
}
