package app.fuggs.bot.document;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from {@code GET /api/bot/documents/{id}/status} on fuggs-app.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusResponse(
	String status,
	boolean complete,
	String error,
	String name,
	BigDecimal total,
	String currencyCode)
{
}
