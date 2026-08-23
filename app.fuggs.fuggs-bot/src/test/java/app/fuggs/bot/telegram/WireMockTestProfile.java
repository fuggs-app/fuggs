package app.fuggs.bot.telegram;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WireMockTestProfile implements QuarkusTestProfile
{
	public static final int WIREMOCK_PORT = 18099;

	public static final String TEST_TOKEN = "123456:TEST-TOKEN";

	@Override
	public Map<String, String> getConfigOverrides()
	{
		Map<String, String> config = new HashMap<>();
		config.put("quarkus.wiremock.devservices.enabled", "true");
		config.put("quarkus.wiremock.devservices.port", String.valueOf(WIREMOCK_PORT));
		config.put("quarkus.rest-client.telegram.url", "http://localhost:" + WIREMOCK_PORT);
		config.put("quarkus.rest-client.fuggs-app.url", "http://localhost:" + WIREMOCK_PORT);
		config.put("fuggs.telegram.bot-token", TEST_TOKEN);
		// Keep the scheduled poller off so it cannot race the assertions;
		// tests drive poll() directly.
		config.put("fuggs.telegram.enabled", "false");
		return config;
	}
}
