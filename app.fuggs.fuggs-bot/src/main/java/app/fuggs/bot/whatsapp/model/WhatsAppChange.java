package app.fuggs.bot.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One change within an entry. {@code field} is {@code "messages"} for the
 * events we act on; other values (e.g. account review updates) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppChange(WhatsAppValue value, String field)
{
}
