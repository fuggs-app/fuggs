package app.fuggs.bot.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A chat. For private chats the {@code id} is the value required by
 * {@code sendMessage} — it cannot be substituted by a username.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChat(Long id, String type, String username)
{
}
