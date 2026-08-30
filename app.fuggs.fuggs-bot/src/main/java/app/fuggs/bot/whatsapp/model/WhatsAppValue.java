package app.fuggs.bot.whatsapp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload of a {@code "messages"} change. {@code messages} is {@code null} for
 * a delivery-status update (Meta sends those as a separate {@code statuses}
 * field on the same object, deliberately not modeled here since the MVP ignores
 * them).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppValue(
	@JsonProperty("messaging_product") String messagingProduct,
	List<WhatsAppMessage> messages)
{
}
