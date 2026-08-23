package app.fuggs.bot.document;

/**
 * Request body for {@code POST /api/bot/documents} on fuggs-app. Field names
 * must match {@code BotDocumentResource.IntakeRequest} exactly for Jackson
 * (de)serialization on both ends.
 */
public record IntakeRequest(String telegramUsername, String fileName, String contentType, String fileBase64)
{
}
