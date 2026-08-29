package app.fuggs.bot.document;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from {@code GET /api/bot/documents/{id}/status} on fuggs-app.
 * {@code message}, when present, is LLM-generated, ready-to-send text (issue
 * #94 requires every member-facing message to be LLM-written) - relay it
 * verbatim rather than composing a reply from the other fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusResponse(
	String status,
	boolean complete,
	String error,
	String name,
	BigDecimal total,
	String currencyCode,
	String message)
{
}
