package app.fuggs.bot.whatsapp;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.fuggs.bot.whatsapp.model.WhatsAppPhoneNumberInfo;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Verifies on startup that the configured access token and phone number id are
 * valid and the Graph API is reachable, and exposes the same check for
 * on-demand use. Mirrors {@code TelegramConnectivityService}.
 */
@ApplicationScoped
public class WhatsAppConnectivityService
{
	private static final Logger LOG = LoggerFactory.getLogger(WhatsAppConnectivityService.class);

	@Inject
	WhatsAppConfig config;

	@RestClient
	WhatsAppClient client;

	void onStart(@Observes StartupEvent event)
	{
		if (!config.enabled())
		{
			LOG.info("WhatsApp integration disabled (fuggs.whatsapp.enabled=false)");
			return;
		}

		if (!config.isUsable())
		{
			LOG.warn("WhatsApp integration enabled but not fully configured "
				+ "- set FUGGS_WHATSAPP_TOKEN and FUGGS_WHATSAPP_PHONE_NUMBER_ID");
			return;
		}

		try
		{
			WhatsAppPhoneNumberInfo phoneNumber = whoAmI();
			LOG.info("WhatsApp connection OK: {} ({}, id={})",
				phoneNumber.verifiedName(), phoneNumber.displayPhoneNumber(), phoneNumber.id());
		}
		catch (Exception e)
		{
			// Never fail startup over this - the web app must still boot.
			LOG.error("WhatsApp connection FAILED: {}", e.getMessage(), e);
		}
	}

	/**
	 * Calls {@code GET /{phone-number-id}} and returns the sender number's own
	 * record.
	 *
	 * @return the phone number identity as reported by the Graph API
	 * @throws IllegalStateException
	 *             when the integration is not fully configured
	 */
	public WhatsAppPhoneNumberInfo whoAmI()
	{
		String phoneNumberId = config.phoneNumberId()
			.filter(id -> !id.isBlank())
			.orElseThrow(() -> new IllegalStateException("No WhatsApp phone number id configured"));

		return client.getPhoneNumber(phoneNumberId, config.bearerToken());
	}
}
