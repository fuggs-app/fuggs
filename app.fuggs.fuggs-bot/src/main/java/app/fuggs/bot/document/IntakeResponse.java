package app.fuggs.bot.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Successful response from {@code POST /api/bot/documents} on fuggs-app.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntakeResponse(Long documentId)
{
}
