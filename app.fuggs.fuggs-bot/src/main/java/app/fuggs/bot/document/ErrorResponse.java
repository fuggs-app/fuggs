package app.fuggs.bot.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Error body from {@code POST /api/bot/documents} on fuggs-app. {@code
 * message}, when present, is LLM-generated, ready-to-send text (e.g. the "we
 * couldn't find your account" message for an unknown sender) - relay it
 * verbatim rather than composing a reply from {@code error}, which is only a
 * machine-readable code.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorResponse(String error, String message)
{
}
