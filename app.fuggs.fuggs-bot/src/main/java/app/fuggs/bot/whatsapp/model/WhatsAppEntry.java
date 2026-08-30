package app.fuggs.bot.whatsapp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One WhatsApp Business Account's worth of changes within a webhook payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppEntry(String id, List<WhatsAppChange> changes)
{
}
