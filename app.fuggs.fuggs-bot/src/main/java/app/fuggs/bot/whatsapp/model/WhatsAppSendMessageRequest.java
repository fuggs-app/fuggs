package app.fuggs.bot.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST /{phone-number-id}/messages}. Only the free-form
 * text shape is modeled - the MVP never sends a template message (see
 * {@code docs/plan-whatsapp-bot.md} §6 for why that stays unimplemented).
 */
public record WhatsAppSendMessageRequest(
	@JsonProperty("messaging_product") String messagingProduct,
	String to,
	String type,
	WhatsAppTextBody text)
{
	/**
	 * @param to
	 *            recipient phone number in E.164 without a leading '+'
	 * @param text
	 *            the message text
	 */
	public WhatsAppSendMessageRequest(String to, String text)
	{
		this("whatsapp", to, "text", new WhatsAppTextBody(text));
	}
}
