package app.fuggs.bot.whatsapp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Top-level body Meta posts to the webhook for every event (messages, delivery
 * statuses, ...).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppWebhookPayload(String object, List<WhatsAppEntry> entry)
{
}
