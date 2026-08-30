package app.fuggs.bot.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of {@code GET /{phone-number-id}}. Cheapest way to verify that the
 * configured access token and phone number id are valid and the Graph API is
 * reachable - mirrors {@code TelegramUser} as the payload of Telegram's
 * equivalent {@code getMe} check.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppPhoneNumberInfo(
	String id,
	@JsonProperty("display_phone_number") String displayPhoneNumber,
	@JsonProperty("verified_name") String verifiedName)
{
}
