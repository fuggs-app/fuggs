package app.fuggs.bot.whatsapp;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WhatsAppWireMockTestProfile implements QuarkusTestProfile
{
	public static final int WIREMOCK_PORT = 18098;

	public static final String TEST_TOKEN = "TEST-WHATSAPP-TOKEN";

	public static final String TEST_PHONE_NUMBER_ID = "1234567890";

	public static final String TEST_APP_SECRET = "test-app-secret";

	public static final String TEST_VERIFY_TOKEN = "test-verify-token";

	@Override
	public Map<String, String> getConfigOverrides()
	{
		Map<String, String> config = new HashMap<>();
		config.put("quarkus.wiremock.devservices.enabled", "true");
		config.put("quarkus.wiremock.devservices.port", String.valueOf(WIREMOCK_PORT));
		config.put("quarkus.rest-client.whatsapp.url", "http://localhost:" + WIREMOCK_PORT);
		config.put("quarkus.rest-client.fuggs-app.url", "http://localhost:" + WIREMOCK_PORT);
		config.put("fuggs.whatsapp.access-token", TEST_TOKEN);
		config.put("fuggs.whatsapp.phone-number-id", TEST_PHONE_NUMBER_ID);
		config.put("fuggs.whatsapp.app-secret", TEST_APP_SECRET);
		config.put("fuggs.whatsapp.verify-token", TEST_VERIFY_TOKEN);
		// Keep the startup connectivity check off so it cannot race the
		// assertions; WhatsAppConnectivityServiceTest drives it directly.
		config.put("fuggs.whatsapp.enabled", "false");
		return config;
	}
}
